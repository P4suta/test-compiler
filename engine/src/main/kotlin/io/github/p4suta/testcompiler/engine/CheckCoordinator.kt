package io.github.p4suta.testcompiler.engine

import io.github.p4suta.testcompiler.model.CacheSummary
import io.github.p4suta.testcompiler.model.CatalogStatus
import io.github.p4suta.testcompiler.model.ChallengePlan
import io.github.p4suta.testcompiler.model.Diagnostic
import io.github.p4suta.testcompiler.model.EnvironmentSummary
import io.github.p4suta.testcompiler.model.Finding
import io.github.p4suta.testcompiler.model.FindingKind
import io.github.p4suta.testcompiler.model.FindingStatus
import io.github.p4suta.testcompiler.model.RunReportV1
import io.github.p4suta.testcompiler.model.SourceLocation
import io.github.p4suta.testcompiler.model.StableFindingId
import io.github.p4suta.testcompiler.model.Witness
import io.github.p4suta.testcompiler.model.WitnessValue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.TreeMap

class CheckCoordinator private constructor(private val supervisor: ProcessSupervisor) {
    constructor() : this(ProcessSupervisor())
    fun check(request: CheckRequest): CheckResult {
        require(Files.isRegularFile(request.agentJar)) { "Agent jar does not exist: ${request.agentJar}" }
        require(Files.isRegularFile(request.runnerJar)) { "Runner jar does not exist: ${request.runnerJar}" }
        Files.createDirectories(request.outputDirectory)
        val previousReport = readReport(request.outputDirectory.resolve("run-report-v1.json"))
        val cacheKey = Hashing.cacheKey(request)
        val cacheReport = request.cacheDirectory.resolve(cacheKey).resolve("run-report-v1.json")
        val cachedReport = if (request.changedRef == null && Files.isRegularFile(cacheReport)) readReport(cacheReport) else null
        val corruptCache = request.changedRef == null && Files.isRegularFile(cacheReport) && cachedReport == null
        if (cachedReport != null) {
            val cacheHit = cachedReport.copy(
                generatedAt = Instant.now().toString(),
                cache = CacheSummary(cacheKey, hit = true, cacheable = true, reason = "complete-input-match"),
            )
            val (reportPath, sarifPath) = ReportWriter.write(cacheHit, request.outputDirectory)
            return result(cacheHit, request, reportPath, sarifPath)
        }

        val launcher = WorkerLauncher(request, supervisor)
        val baseline = launcher.launch("baseline")
        if (baseline.timedOut || baseline.exitCode != 0) {
            val reason = if (baseline.timedOut) "baseline-timeout" else "baseline-failed-exit-${baseline.exitCode}"
            val report = baseReport(
                request,
                cacheKey,
                CatalogStatus.BASELINE_FAILED,
                cacheable = false,
                findings = emptyList(),
                diagnostics = listOf(Diagnostic("baseline-failed", "error", reason, baseline.logFile.toString())),
            )
            val (reportPath, sarifPath) = ReportWriter.write(report, request.outputDirectory)
            return result(report, request, reportPath, sarifPath)
        }

        val nonCacheable = baseline.events.firstOrNull { it.type == "NON_CACHEABLE" }
        val catalog = CandidateFactory.create(baseline.events)
        val changed = ChangedFiles.fromGit(request.projectRoot, request.changedRef)
        val selected = catalog.candidates
            .filter { candidate -> changed == null || candidateMatchesChange(candidate, changed, request) }
            .take(request.maxChallenges)
        val diagnostics = mutableListOf<Diagnostic>()
        if (corruptCache) {
            diagnostics += Diagnostic("corrupt-cache", "warning", "Ignored an unreadable cache entry and ran a complete check")
        }
        catalog.unsupported.forEach { observation ->
            diagnostics += Diagnostic(
                "unsupported-value",
                "info",
                "Boundary contains an object graph that cannot be proven without serialization",
                listOfNotNull(observation.owner, observation.method).joinToString("#"),
            )
        }
        if (catalog.candidates.size > request.maxChallenges) {
            diagnostics += Diagnostic(
                "challenge-limit",
                "warning",
                "${catalog.candidates.size - request.maxChallenges} challenge(s) were not run; increase maxChallenges",
            )
        }
        if (request.changedRef != null && changed == null) {
            diagnostics += Diagnostic("changed-ref-unavailable", "warning", "Could not resolve Git reference; full catalog was checked")
        }

        val baselinedIds = readBaselineIds(request.projectRoot.resolve(".test-compiler-baseline.json"))
        val findings = selected.mapIndexedNotNull { index, candidate ->
            evaluateCandidate(request, launcher, candidate, index, baselinedIds)
        }.toMutableList()

        if (changed != null && previousReport != null) {
            val retained = previousReport.findings.filterNot { finding -> changed.any { it == finding.source.path } }
            findings += retained
            diagnostics += Diagnostic(
                "incremental-catalog-merge",
                "info",
                "Merged ${retained.size} unaffected finding(s) from the previous complete catalog",
            )
        }
        val uniqueFindings = FindingIdCollisionResolver.resolve(findings.sortedBy { it.id }.distinct())
        if (uniqueFindings.any { it.id.substringAfterLast('-', "").toIntOrNull() != null }) {
            diagnostics += Diagnostic("finding-id-collision", "warning", "A truncated stable ID collision was deterministically disambiguated")
        }
        val cacheable = nonCacheable == null
        val report = baseReport(
            request,
            cacheKey,
            CatalogStatus.COMPLETE,
            cacheable,
            uniqueFindings,
            diagnostics,
            nonCacheable?.reason,
        )
        val (reportPath, sarifPath) = ReportWriter.write(report, request.outputDirectory)
        if (cacheable) {
            Files.createDirectories(cacheReport.parent)
            Files.copy(reportPath, cacheReport, StandardCopyOption.REPLACE_EXISTING)
        }
        return result(report, request, reportPath, sarifPath)
    }

    fun cancel() = supervisor.cancel()

    private fun evaluateCandidate(
        request: CheckRequest,
        launcher: WorkerLauncher,
        candidate: Candidate,
        index: Int,
        baselinedIds: Set<String>,
    ): Finding? {
        val source = resolveSource(request, candidate)
        val id = StableFindingId.create(candidate.kind, source, candidate.target, candidate.operator)
        val selection = if (candidate.kind == FindingKind.ORDER_DEPENDENCY) candidate.orderSequence else candidate.testIds
        val first = launcher.launch("challenge-${index.toString().padStart(4, '0')}", candidate, selection)
        if (first.timedOut) {
            return finding(candidate, id, source, FindingStatus.INCONCLUSIVE, null, "timeout", null)
        }
        val firstDemonstratesGap = if (candidate.kind == FindingKind.ORDER_DEPENDENCY) {
            first.exitCode == 1
        } else {
            first.exitCode == 0
        }
        if (!firstDemonstratesGap) return null
        val confirmation = launcher.launch("confirm-${index.toString().padStart(4, '0')}", candidate, selection)
        val confirmationDemonstratesGap = if (candidate.kind == FindingKind.ORDER_DEPENDENCY) {
            confirmation.exitCode == 1
        } else {
            confirmation.exitCode == 0
        }
        if (confirmation.timedOut || !confirmationDemonstratesGap) {
            return finding(candidate, id, source, FindingStatus.INCONCLUSIVE, null, "non-reproducible", null)
        }
        val firstTrace = normalizedTrace(first.events)
        val confirmationTrace = normalizedTrace(confirmation.events)
        if (firstTrace != confirmationTrace) {
            return finding(candidate, id, source, FindingStatus.INCONCLUSIVE, null, "trace-mismatch", null)
        }
        val status = if (id in baselinedIds) FindingStatus.BASELINED else FindingStatus.PROVEN
        val witness = Witness(
            arguments = candidate.argumentTypes.map { WitnessValue(it, WitnessFactory.value(it), synthetic = true) },
            testSequence = candidate.testNames,
            expected = if (candidate.kind == FindingKind.ORDER_DEPENDENCY) "tests pass independently" else "baseline tests pass",
            counterfactual = when (candidate.kind) {
                FindingKind.BEHAVIOR_GAP -> "tests also pass after ${candidate.operator}"
                FindingKind.EFFECT_GAP -> "tests also pass with the effect suppressed"
                FindingKind.ORDER_DEPENDENCY -> "saved sequence reproduces the state-dependent failure"
            },
        )
        return finding(candidate, id, source, status, witness, null, firstTrace)
    }

    private fun finding(
        candidate: Candidate,
        id: String,
        source: SourceLocation,
        status: FindingStatus,
        witness: Witness?,
        inconclusive: String?,
        traceHash: String?,
    ): Finding {
        val (title, message) = when (candidate.kind) {
            FindingKind.BEHAVIOR_GAP -> "Behavioral gap" to "Tests did not distinguish ${candidate.operator} at ${candidate.target}"
            FindingKind.EFFECT_GAP -> "Unobserved important effect" to "Tests passed when ${candidate.target} was suppressed"
            FindingKind.ORDER_DEPENDENCY -> "Test-order dependency" to "The minimal candidate sequence depends on ${candidate.method}"
        }
        val plan = ChallengePlan(
            planId = id,
            kind = candidate.kind,
            target = candidate.target,
            operator = candidate.operator,
            attributes = TreeMap<String, String>().apply {
                put("owner", candidate.owner)
                put("method", candidate.method)
                put("descriptor", candidate.descriptor)
                if (candidate.orderSequence.isNotEmpty()) put("testSequence", candidate.orderSequence.joinToString("\u001f"))
                else put("testIds", candidate.testIds.joinToString("\u001f"))
            },
        )
        return Finding(
            id = id,
            kind = candidate.kind,
            status = status,
            title = title,
            message = message,
            source = source,
            impactedTests = candidate.testNames,
            plan = plan,
            witness = witness,
            replayCommand = "testc replay $id",
            normalizedTraceHash = traceHash,
            inconclusiveReason = inconclusive,
        )
    }

    private fun normalizedTrace(events: List<Observation>): String = Hashing.text(
        events.filter { it.type != "TEST_START" && it.type != "TEST_FINISH" }
            .joinToString("\n") { event ->
                listOf(
                    event.type, event.testName, event.owner, event.method, event.descriptor,
                    event.source, event.line, event.valueKind, event.category, event.access, event.keyHash,
                ).joinToString("|")
            },
    )

    private fun resolveSource(request: CheckRequest, candidate: Candidate): SourceLocation {
        if (candidate.source == "<global-state>") {
            return SourceLocation("<global-state>", 1, symbol = candidate.method)
        }
        val packagePath = candidate.owner.substringBeforeLast('/', "")
        val direct = request.sourceRoots.asSequence()
            .map { it.resolve(packagePath).resolve(candidate.source) }
            .firstOrNull { Files.isRegularFile(it) }
        val found = direct ?: request.sourceRoots.asSequence().flatMap { root ->
            if (!Files.isDirectory(root)) emptySequence() else Files.walk(root).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.fileName.toString() == candidate.source }
                    .sorted().toList().asSequence()
            }
        }.firstOrNull()
        val path = found?.let { request.projectRoot.toAbsolutePath().normalize().relativize(it.toAbsolutePath().normalize()).toString() }
            ?.replace('\\', '/') ?: candidate.source
        return SourceLocation(path, candidate.line.coerceAtLeast(1), symbol = "${candidate.method}${candidate.descriptor}")
    }

    private fun candidateMatchesChange(candidate: Candidate, changed: Set<String>, request: CheckRequest): Boolean {
        if (candidate.source == "<global-state>") return true
        val resolved = resolveSource(request, candidate).path
        return changed.any { changedPath -> changedPath == resolved || changedPath.endsWith("/${candidate.source}") }
    }

    private fun readBaselineIds(path: Path): Set<String> = runCatching {
        JsonSupport.mapper.readTree(path.toFile()).path("findingIds").map { it.asText() }.toSet()
    }.getOrDefault(emptySet())

    private fun readReport(path: Path): RunReportV1? = runCatching {
        JsonSupport.mapper.readValue(path.toFile(), RunReportV1::class.java)
    }.getOrNull()

    private fun baseReport(
        request: CheckRequest,
        cacheKey: String,
        status: CatalogStatus,
        cacheable: Boolean,
        findings: List<Finding>,
        diagnostics: List<Diagnostic>,
        cacheReason: String? = null,
    ): RunReportV1 = RunReportV1(
        toolVersion = "0.1.0",
        generatedAt = Instant.now().toString(),
        catalogStatus = status,
        projectIdentityHash = Hashing.text(request.projectRoot.toAbsolutePath().normalize().toString()),
        environment = EnvironmentSummary(
            javaVersion = System.getProperty("java.version"),
            gradleVersion = request.gradleVersion,
            os = "${System.getProperty("os.name")}/${System.getProperty("os.arch")}",
            configurationHash = request.configurationHash,
        ),
        cache = CacheSummary(cacheKey, hit = false, cacheable = cacheable, reason = cacheReason),
        findings = findings,
        diagnostics = diagnostics,
    )

    private fun result(
        report: RunReportV1,
        request: CheckRequest,
        reportPath: Path,
        sarifPath: Path,
    ): CheckResult {
        val proven = report.findings.count { it.status == FindingStatus.PROVEN }
        val any = report.findings.count { it.status == FindingStatus.PROVEN || it.status == FindingStatus.BASELINED }
        val gateFailed = when (request.gate) {
            Gate.DIAGNOSTIC_ONLY -> false
            Gate.NEW -> proven > 0
            Gate.ALL -> any > 0
        }
        return CheckResult(reportPath, sarifPath, report.findings.size, proven, gateFailed, report.catalogStatus == CatalogStatus.BASELINE_FAILED)
    }
}

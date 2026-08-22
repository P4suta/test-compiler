package io.github.p4suta.testcompiler.engine

import io.github.p4suta.testcompiler.model.FindingKind
import io.github.p4suta.testcompiler.model.RunReportV1
import java.nio.file.Path

data class ReplayResult(val reproduced: Boolean, val message: String)

class ReplayService {
    fun replay(request: CheckRequest, findingId: String): ReplayResult {
        val reportPath = request.outputDirectory.resolve("run-report-v1.json")
        val report = runCatching { JsonSupport.mapper.readValue(reportPath.toFile(), RunReportV1::class.java) }
            .getOrElse { return ReplayResult(false, "No readable report at $reportPath") }
        val finding = report.findings.firstOrNull { it.id == findingId }
            ?: return ReplayResult(false, "Finding $findingId is not in the current report")
        val separator = "\u001f"
        val ids = finding.plan.attributes["testIds"]?.split(separator).orEmpty().filter { it.isNotBlank() }
        val order = finding.plan.attributes["testSequence"]?.split(separator).orEmpty().filter { it.isNotBlank() }
        val target = finding.plan.target.substringBefore('#')
        val methodWithDescriptor = finding.plan.target.substringAfter('#', "")
        val descriptor = finding.plan.attributes["descriptor"].orEmpty()
        val candidate = Candidate(
            kind = finding.kind,
            owner = finding.plan.attributes["owner"] ?: target,
            method = finding.plan.attributes["method"] ?: methodWithDescriptor.removeSuffix(descriptor),
            descriptor = descriptor,
            source = finding.source.path.substringAfterLast('/'),
            line = finding.source.line,
            operator = finding.plan.operator,
            testIds = ids,
            testNames = finding.impactedTests,
            orderSequence = order,
        )
        val launcher = WorkerLauncher(request, ProcessSupervisor())
        val selection = if (finding.kind == FindingKind.ORDER_DEPENDENCY) order else ids
        val first = launcher.launch("replay-$findingId-1", candidate, selection)
        val second = launcher.launch("replay-$findingId-2", candidate, selection)
        val expectedExit = if (finding.kind == FindingKind.ORDER_DEPENDENCY) 1 else 0
        val reproduced = !first.timedOut && !second.timedOut && first.exitCode == expectedExit && second.exitCode == expectedExit
        return ReplayResult(reproduced, if (reproduced) "Reproduced $findingId twice" else "Could not reproduce $findingId deterministically")
    }
}

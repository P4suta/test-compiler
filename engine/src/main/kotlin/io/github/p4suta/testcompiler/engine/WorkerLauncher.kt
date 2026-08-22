package io.github.p4suta.testcompiler.engine

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal data class WorkerResult(
    val exitCode: Int?,
    val timedOut: Boolean,
    val events: List<Observation>,
    val eventFile: Path,
    val logFile: Path,
)

internal class WorkerLauncher(
    private val request: CheckRequest,
    private val supervisor: ProcessSupervisor,
) {
    fun launch(name: String, candidate: Candidate? = null, selected: List<String> = candidate?.testIds.orEmpty()): WorkerResult {
        val runDirectory = request.outputDirectory.resolve("runs").resolve(name)
        Files.createDirectories(runDirectory)
        val eventFile = runDirectory.resolve("events.jsonl")
        val logFile = runDirectory.resolve("worker.log")
        val roots = request.productionRoots.joinToString(File.pathSeparator) { it.toAbsolutePath().toString() }
        val testRoots = request.testRoots.joinToString(File.pathSeparator) { it.toAbsolutePath().toString() }
        val classpath = (listOf(request.runnerJar, request.agentJar) + request.testRuntimeClasspath)
            .map { it.toAbsolutePath().normalize() }.distinct()
            .joinToString(File.pathSeparator)
        val arguments = buildList {
            add("-Dfile.encoding=UTF-8")
            add("-Dtestcompiler.eventFile=${eventFile.toAbsolutePath()}")
            add("-Dtestcompiler.productionRoots=$roots")
            add("-Dtestcompiler.testRoots=$testRoots")
            add("-Dtestcompiler.customEffects=${request.customEffects.joinToString(";")}")
            candidate?.takeUnless { it.kind == io.github.p4suta.testcompiler.model.FindingKind.ORDER_DEPENDENCY }?.let { challenge ->
                add("-Dtestcompiler.plan.id=${challenge.target}:${challenge.operator}")
                add("-Dtestcompiler.plan.owner=${challenge.owner}")
                add("-Dtestcompiler.plan.method=${challenge.method}")
                add("-Dtestcompiler.plan.descriptor=${challenge.descriptor}")
                add("-Dtestcompiler.plan.operator=${challenge.operator}")
                add("-Dtestcompiler.plan.line=${challenge.line}")
            }
            add("-javaagent:${request.agentJar.toAbsolutePath()}")
            add("-cp")
            add(classpath)
            add("io.github.p4suta.testcompiler.engine.WorkerMain")
            request.testRoots.forEach { add("--test-root=${it.toAbsolutePath()}") }
            selected.forEach { add("--select=$it") }
        }
        val result = supervisor.run(request.javaExecutable, arguments, runDirectory, request.timeout, logFile)
        return WorkerResult(result.exitCode, result.timedOut, ObservationReader.read(eventFile), eventFile, logFile)
    }
}

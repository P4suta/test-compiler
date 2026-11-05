package io.github.p4suta.testcompiler.engine

import io.github.p4suta.walaru.runner.JvmWorkerSupervisor
import java.nio.file.Path
import kotlin.time.Duration

internal data class SupervisedResult(val exitCode: Int?, val timedOut: Boolean, val log: Path)

internal class ProcessSupervisor {
    private val delegate = JvmWorkerSupervisor()

    fun run(javaExecutable: Path, arguments: List<String>, directory: Path, timeout: Duration, log: Path): SupervisedResult {
        val result = delegate.run(
            javaExecutable = javaExecutable,
            arguments = arguments,
            directory = directory,
            timeout = java.time.Duration.ofMillis(timeout.inWholeMilliseconds),
            log = log,
        )
        return SupervisedResult(result.exitCode, result.timedOut, result.log)
    }

    fun cancel() {
        delegate.cancel()
    }
}

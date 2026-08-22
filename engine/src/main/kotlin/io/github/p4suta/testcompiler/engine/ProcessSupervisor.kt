package io.github.p4suta.testcompiler.engine

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

internal data class SupervisedResult(val exitCode: Int?, val timedOut: Boolean, val log: Path)

internal class ProcessSupervisor {
    private val active = AtomicReference<Process?>()

    fun run(javaExecutable: Path, arguments: List<String>, directory: Path, timeout: Duration, log: Path): SupervisedResult {
        Files.createDirectories(directory)
        Files.createDirectories(log.parent)
        val argumentFile = directory.resolve("java-${Hashing.text(arguments.joinToString("\u0000")).take(12)}.args")
        Files.writeString(argumentFile, arguments.joinToString("\n") { quote(it) } + "\n", StandardCharsets.UTF_8)
        val process = ProcessBuilder(javaExecutable.toString(), "@${argumentFile.toAbsolutePath()}")
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start()
        active.set(process)
        val hook = Thread({ terminateTree(process) }, "test-compiler-process-cleanup")
        Runtime.getRuntime().addShutdownHook(hook)
        return try {
            if (process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
                SupervisedResult(process.exitValue(), false, log)
            } else {
                terminateTree(process)
                SupervisedResult(null, true, log)
            }
        } finally {
            active.compareAndSet(process, null)
            runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
        }
    }

    fun cancel() {
        active.get()?.let(::terminateTree)
    }

    private fun terminateTree(process: Process) {
        process.descendants().sorted(Comparator.reverseOrder()).forEach { child ->
            child.destroy()
            if (child.isAlive) child.destroyForcibly()
        }
        process.destroy()
        if (process.isAlive) process.destroyForcibly()
    }

    private fun quote(argument: String): String {
        val normalized = argument.replace('\\', '/')
        return "\"${normalized.replace("\"", "\\\"")}\""
    }
}

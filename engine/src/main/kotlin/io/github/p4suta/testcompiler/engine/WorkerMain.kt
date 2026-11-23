package io.github.p4suta.testcompiler.engine

import io.github.p4suta.walaru.runner.RunnerEngine
import io.github.p4suta.walaru.runner.RunnerLifecycle
import io.github.p4suta.walaru.runner.RunnerRequest
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.system.exitProcess

/** Diagnostics-specific worker facade over Walaru's shared JUnit Platform/TestNG runner. */
object WorkerMain {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val request = RunnerRequest(
            testRoots = arguments.filter { it.startsWith("--test-root=") }
                .map { Path.of(it.substringAfter('=')) }
                .filter { it.exists() },
            selectors = arguments.filter { it.startsWith("--select=") }
                .map { it.substringAfter('=') },
            resultFile = null,
        )
        val result = runCatching { RunnerEngine.execute(request, TestCompilerLifecycle()) }
            .getOrElse { failure ->
                failure.printStackTrace(System.err)
                exitProcess(2)
            }
        exitProcess(if (result.failed > 0) 1 else 0)
    }

    private class TestCompilerLifecycle : RunnerLifecycle {
        private val bridge = runCatching {
            Class.forName("io.github.p4suta.testcompiler.agent.AgentBridge")
        }.getOrNull()
        private val start = bridge?.getMethod("testStarted", String::class.java, String::class.java)
        private val finish = bridge?.getMethod("testFinished", String::class.java, String::class.java)

        override fun started(uniqueId: String, publicName: String) {
            start?.invoke(null, uniqueId, publicName)
        }

        override fun finished(
            uniqueId: String,
            publicName: String,
            status: String,
            failure: Throwable?,
        ) {
            finish?.invoke(null, uniqueId, status.uppercase())
            failure?.printStackTrace(System.err)
        }
    }
}

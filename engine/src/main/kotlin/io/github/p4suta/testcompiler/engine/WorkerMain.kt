package io.github.p4suta.testcompiler.engine

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.engine.discovery.DiscoverySelectors
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.exists

object WorkerMain {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val testRoots = arguments.filter { it.startsWith("--test-root=") }
            .map { Path.of(it.substringAfter('=')) }
            .filter { it.exists() }
        val selected = arguments.filter { it.startsWith("--select=") }
            .map { it.substringAfter('=') }
        val launcher = LauncherFactory.create()
        val listener = RecordingListener()
        launcher.registerTestExecutionListeners(listener)
        var platformFailure: Throwable? = null
        try {
            val junitSelected = selected.filter { it.startsWith("[") }
            if (selected.isEmpty()) {
                val request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(DiscoverySelectors.selectClasspathRoots(testRoots.toSet()))
                    .build()
                launcher.execute(request)
            } else if (junitSelected.isNotEmpty()) {
                junitSelected.forEach { uniqueId ->
                    val request = LauncherDiscoveryRequestBuilder.request()
                        .selectors(DiscoverySelectors.selectUniqueId(uniqueId))
                        .build()
                    launcher.execute(request)
                }
            }
        } catch (failure: Throwable) {
            platformFailure = failure
        }
        val testNg = TestNgAdapter.run(testRoots, selected.filter { it.startsWith("testng:") })
        if (listener.executed.get() == 0 && testNg.executed == 0 && platformFailure != null) {
            platformFailure.printStackTrace(System.err)
            kotlin.system.exitProcess(2)
        }
        if (listener.failed.get() > 0 || testNg.failed > 0) {
            kotlin.system.exitProcess(1)
        }
    }

    private class RecordingListener : TestExecutionListener {
        val failed = AtomicInteger()
        val executed = AtomicInteger()
        private val bridge = runCatching { Class.forName("io.github.p4suta.testcompiler.agent.AgentBridge") }.getOrNull()
        private val start = bridge?.getMethod("testStarted", String::class.java, String::class.java)
        private val finish = bridge?.getMethod("testFinished", String::class.java, String::class.java)

        override fun executionStarted(testIdentifier: TestIdentifier) {
            if (testIdentifier.isTest) {
                executed.incrementAndGet()
                start?.invoke(null, testIdentifier.uniqueId, publicName(testIdentifier))
            }
        }

        override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
            if (!testIdentifier.isTest) return
            val status = testExecutionResult.status.name
            finish?.invoke(null, testIdentifier.uniqueId, status)
            if (testExecutionResult.status == TestExecutionResult.Status.FAILED) {
                failed.incrementAndGet()
                testExecutionResult.throwable.ifPresent { it.printStackTrace(System.err) }
            }
        }

        private fun publicName(identifier: TestIdentifier): String {
            val source = identifier.source.orElse(null)
            return if (source is MethodSource) "${source.className}#${source.methodName}" else identifier.legacyReportingName
        }
    }
}

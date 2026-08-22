package io.github.p4suta.testcompiler.engine

import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class CheckRequest(
    val projectRoot: Path,
    val outputDirectory: Path,
    val cacheDirectory: Path,
    val javaExecutable: Path,
    val runnerJar: Path,
    val agentJar: Path,
    val testRuntimeClasspath: List<Path>,
    val productionRoots: List<Path>,
    val testRoots: List<Path>,
    val sourceRoots: List<Path>,
    val gate: Gate = Gate.DIAGNOSTIC_ONLY,
    val changedRef: String? = null,
    val timeout: Duration = 5.minutes,
    val maxChallenges: Int = 64,
    val trackedEnvironment: List<String> = emptyList(),
    val trackedFiles: List<Path> = emptyList(),
    val customEffects: List<String> = emptyList(),
    val configurationHash: String = "built-in-defaults",
    val gradleVersion: String? = null,
)

enum class Gate {
    DIAGNOSTIC_ONLY,
    NEW,
    ALL;

    companion object {
        @JvmStatic
        fun parse(value: String?): Gate = when (value?.lowercase()) {
            "new" -> NEW
            "all" -> ALL
            else -> DIAGNOSTIC_ONLY
        }
    }
}

data class CheckResult(
    val reportPath: Path,
    val sarifPath: Path,
    val findingCount: Int,
    val provenCount: Int,
    val gateFailed: Boolean,
    val baselineFailed: Boolean,
)

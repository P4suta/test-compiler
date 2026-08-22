package io.github.p4suta.testcompiler.model

import java.time.Instant
import java.util.SortedMap
import java.util.TreeMap

const val REPORT_SCHEMA_VERSION: String = "1"

enum class FindingKind {
    BEHAVIOR_GAP,
    EFFECT_GAP,
    ORDER_DEPENDENCY,
}

enum class FindingStatus {
    PROVEN,
    INCONCLUSIVE,
    UNSUPPORTED,
    BASELINED,
}

enum class CatalogStatus {
    COMPLETE,
    BASELINE_FAILED,
    CANCELLED,
    PARTIAL,
}

data class SourceLocation(
    val path: String,
    val line: Int,
    val column: Int = 1,
    val symbol: String,
)

data class ChallengePlan(
    val planId: String,
    val kind: FindingKind,
    val target: String,
    val operator: String,
    val iteration: Int = 0,
    val attributes: SortedMap<String, String> = TreeMap(),
)

data class WitnessValue(
    val type: String,
    val value: String,
    val synthetic: Boolean = true,
)

data class Witness(
    val arguments: List<WitnessValue> = emptyList(),
    val testSequence: List<String> = emptyList(),
    val expected: String? = null,
    val counterfactual: String? = null,
)

data class Finding(
    val id: String,
    val kind: FindingKind,
    val status: FindingStatus,
    val title: String,
    val message: String,
    val source: SourceLocation,
    val impactedTests: List<String>,
    val plan: ChallengePlan,
    val witness: Witness? = null,
    val replayCommand: String,
    val normalizedTraceHash: String? = null,
    val cacheReason: String? = null,
    val inconclusiveReason: String? = null,
)

data class Diagnostic(
    val code: String,
    val severity: String,
    val message: String,
    val target: String? = null,
)

data class CacheSummary(
    val key: String,
    val hit: Boolean,
    val cacheable: Boolean,
    val reason: String? = null,
)

data class EnvironmentSummary(
    val javaVersion: String,
    val gradleVersion: String? = null,
    val os: String,
    val configurationHash: String,
)

data class RunReportV1(
    val schemaVersion: String = REPORT_SCHEMA_VERSION,
    val toolVersion: String,
    val generatedAt: String = Instant.EPOCH.toString(),
    val catalogStatus: CatalogStatus,
    val projectIdentityHash: String,
    val environment: EnvironmentSummary,
    val cache: CacheSummary,
    val findings: List<Finding>,
    val diagnostics: List<Diagnostic> = emptyList(),
)

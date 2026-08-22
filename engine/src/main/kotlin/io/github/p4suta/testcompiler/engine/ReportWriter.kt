package io.github.p4suta.testcompiler.engine

import io.github.p4suta.testcompiler.model.RunReportV1
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal object ReportWriter {
    fun write(report: RunReportV1, directory: Path): Pair<Path, Path> {
        Files.createDirectories(directory)
        val reportPath = directory.resolve("run-report-v1.json")
        JsonSupport.mapper.writeValue(reportPath.toFile(), report)
        val sarifPath = directory.resolve("test-compiler.sarif")
        JsonSupport.mapper.writeValue(sarifPath.toFile(), SarifProjection.project(report))
        val schemaPath = directory.resolve("run-report-v1.schema.json")
        ReportWriter::class.java.getResourceAsStream(
            "/io/github/p4suta/testcompiler/model/run-report-v1.schema.json",
        )!!.use { input -> Files.copy(input, schemaPath, StandardCopyOption.REPLACE_EXISTING) }
        return reportPath to sarifPath
    }
}

private object SarifProjection {
    fun project(report: RunReportV1): Map<String, Any> = mapOf(
        "version" to "2.1.0",
        "${'$'}schema" to "https://json.schemastore.org/sarif-2.1.0.json",
        "runs" to listOf(
            mapOf(
                "tool" to mapOf(
                    "driver" to mapOf(
                        "name" to "test-compiler",
                        "version" to report.toolVersion,
                        "informationUri" to "https://github.com/p4suta/test-compiler",
                        "rules" to report.findings.map { finding ->
                            mapOf(
                                "id" to finding.kind.name.lowercase().replace('_', '-'),
                                "shortDescription" to mapOf("text" to finding.title),
                            )
                        }.distinctBy { it["id"] },
                    ),
                ),
                "results" to report.findings.map { finding ->
                    mapOf(
                        "ruleId" to finding.kind.name.lowercase().replace('_', '-'),
                        "level" to if (finding.status.name == "PROVEN") "warning" else "note",
                        "message" to mapOf("text" to "${finding.message} Replay: ${finding.replayCommand}"),
                        "locations" to listOf(
                            mapOf(
                                "physicalLocation" to mapOf(
                                    "artifactLocation" to mapOf("uri" to finding.source.path),
                                    "region" to mapOf(
                                        "startLine" to finding.source.line,
                                        "startColumn" to finding.source.column,
                                    ),
                                ),
                            ),
                        ),
                        "partialFingerprints" to mapOf("testCompilerFindingId" to finding.id),
                        "properties" to mapOf(
                            "status" to finding.status.name,
                            "planId" to finding.plan.planId,
                            "impactedTests" to finding.impactedTests,
                        ),
                    )
                },
            ),
        ),
    )
}

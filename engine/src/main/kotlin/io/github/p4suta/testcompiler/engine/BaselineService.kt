package io.github.p4suta.testcompiler.engine

import io.github.p4suta.testcompiler.model.FindingStatus
import io.github.p4suta.testcompiler.model.RunReportV1
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object BaselineService {
    @JvmStatic
    fun update(reportPath: Path, baselinePath: Path): Int {
        require(Files.isRegularFile(reportPath)) { "No report exists at $reportPath; run check first" }
        val report = JsonSupport.mapper.readValue(reportPath.toFile(), RunReportV1::class.java)
        val ids = report.findings.filter { it.status == FindingStatus.PROVEN || it.status == FindingStatus.BASELINED }
            .map { it.id }.sorted()
        JsonSupport.mapper.writeValue(
            baselinePath.toFile(),
            mapOf("schemaVersion" to "1", "updatedAt" to Instant.now().toString(), "findingIds" to ids),
        )
        return ids.size
    }
}

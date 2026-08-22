package io.github.p4suta.testcompiler.intellij

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

@Service(Service.Level.PROJECT)
class ReportIndex(private val project: Project) {
    private val mapper = ObjectMapper()
    @Volatile private var snapshot = Snapshot(FileTime.fromMillis(-1), emptyList())

    fun all(): List<IdeFinding> {
        refresh()
        return snapshot.findings
    }

    fun forPath(path: Path): List<IdeFinding> {
        val relative = relativePath(path) ?: return emptyList()
        return all().filter { it.path.replace('\\', '/') == relative }
    }

    private fun refresh() {
        val report = reportPath() ?: return
        val modified = runCatching { Files.getLastModifiedTime(report) }.getOrNull() ?: return
        if (modified == snapshot.modified) return
        val findings = runCatching {
            mapper.readTree(report.toFile()).path("findings").map { finding ->
                IdeFinding(
                    id = finding.path("id").asText(),
                    title = finding.path("title").asText(),
                    message = finding.path("message").asText(),
                    path = finding.path("source").path("path").asText(),
                    line = finding.path("source").path("line").asInt(1),
                    replayCommand = finding.path("replayCommand").asText(),
                )
            }
        }
            .getOrDefault(emptyList())
            .sortedBy { it.id }
        snapshot = Snapshot(modified, findings)
    }

    private fun relativePath(path: Path): String? {
        val base = project.basePath?.let(Path::of)?.toAbsolutePath()?.normalize() ?: return null
        return runCatching { base.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/') }.getOrNull()
    }

    private fun reportPath(): Path? = project.basePath?.let(Path::of)
        ?.resolve("build/reports/test-compiler/run-report-v1.json")

    private data class Snapshot(val modified: FileTime, val findings: List<IdeFinding>)
}

data class IdeFinding(
    val id: String,
    val title: String,
    val message: String,
    val path: String,
    val line: Int,
    val replayCommand: String,
)

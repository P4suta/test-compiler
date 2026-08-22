package io.github.p4suta.testcompiler.engine

import java.nio.charset.StandardCharsets
import java.nio.file.Path

internal object ChangedFiles {
    fun fromGit(projectRoot: Path, reference: String?): Set<String>? {
        if (reference.isNullOrBlank()) return null
        return runCatching {
            val process = ProcessBuilder("git", "diff", "--name-only", "$reference...HEAD")
                .directory(projectRoot.toFile())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
            if (process.waitFor() != 0) null else output.lineSequence()
                .map { it.trim().replace('\\', '/') }
                .filter { it.isNotEmpty() }
                .toSet()
        }.getOrNull()
    }
}

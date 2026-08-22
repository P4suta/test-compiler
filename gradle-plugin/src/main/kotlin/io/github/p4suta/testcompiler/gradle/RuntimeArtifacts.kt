package io.github.p4suta.testcompiler.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal object RuntimeArtifacts {
    fun extract(outputDirectory: Path): Pair<Path, Path> {
        val runtime = outputDirectory.resolve(".runtime")
        Files.createDirectories(runtime)
        val engine = copy("/test-compiler-engine.jar", runtime.resolve("test-compiler-engine.jar"))
        val agent = copy("/test-compiler-agent.jar", runtime.resolve("test-compiler-agent.jar"))
        return engine to agent
    }

    private fun copy(resource: String, target: Path): Path {
        val stream = RuntimeArtifacts::class.java.getResourceAsStream(resource)
            ?: error("Missing embedded runtime resource $resource")
        stream.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
        return target
    }
}

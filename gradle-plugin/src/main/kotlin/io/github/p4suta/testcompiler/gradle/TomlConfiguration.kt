package io.github.p4suta.testcompiler.gradle

import java.nio.file.Files
import java.nio.file.Path

internal data class TomlConfiguration(
    val gate: String = "diagnostic-only",
    val timeoutSeconds: Long = 300,
    val maxChallenges: Int = 64,
    val trackedEnvironment: List<String> = emptyList(),
    val trackedFiles: List<String> = emptyList(),
    val customEffects: List<String> = emptyList(),
) {
    val hash: String get() = sha256(toString())

    companion object {
        fun read(path: Path): TomlConfiguration {
            if (!Files.isRegularFile(path)) return TomlConfiguration()
            val entries = Files.readAllLines(path)
                .asSequence()
                .map { it.substringBefore('#').trim() }
                .filter { it.contains('=') }
                .associate { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim() }
            return TomlConfiguration(
                gate = entries["gate"]?.trim('"', '\'') ?: "diagnostic-only",
                timeoutSeconds = entries["timeout-seconds"]?.toLongOrNull() ?: 300,
                maxChallenges = entries["max-challenges"]?.toIntOrNull() ?: 64,
                trackedEnvironment = parseList(entries["tracked-environment"]),
                trackedFiles = parseList(entries["tracked-files"]),
                customEffects = parseList(entries["custom-effects"]),
            )
        }

        private fun parseList(value: String?): List<String> = value
            ?.removePrefix("[")?.removeSuffix("]")
            ?.split(',')
            ?.map { it.trim().trim('"', '\'') }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        private fun sha256(value: String): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

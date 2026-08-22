package io.github.p4suta.testcompiler.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object StableFindingId {
    @JvmStatic
    fun create(kind: FindingKind, source: SourceLocation, target: String, operator: String): String {
        val canonical = listOf(
            "finding-v1",
            kind.name,
            source.path.replace('\\', '/'),
            source.line.toString(),
            source.symbol,
            target,
            operator,
        ).joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "tc-${digest.take(20)}"
    }
}

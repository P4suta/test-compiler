package io.github.p4suta.testcompiler.engine

import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path

internal data class Observation(
    val type: String,
    val test: String? = null,
    val testName: String? = null,
    val owner: String? = null,
    val method: String? = null,
    val descriptor: String? = null,
    val source: String? = null,
    val line: Int = 1,
    val valueKind: String? = null,
    val argumentTypes: List<String> = emptyList(),
    val category: String? = null,
    val access: String? = null,
    val keyHash: String? = null,
    val status: String? = null,
    val reason: String? = null,
)

internal object ObservationReader {
    fun read(path: Path): List<Observation> {
        if (!Files.isRegularFile(path)) return emptyList()
        return Files.readAllLines(path)
            .filter { it.isNotBlank() }
            .map { line -> fromNode(JsonSupport.mapper.readTree(line)) }
    }

    private fun fromNode(node: JsonNode): Observation = Observation(
        type = node.path("type").asText(),
        test = node.textOrNull("test"),
        testName = node.textOrNull("testName"),
        owner = node.textOrNull("owner"),
        method = node.textOrNull("method"),
        descriptor = node.textOrNull("descriptor"),
        source = node.textOrNull("source"),
        line = node.path("line").asInt(1),
        valueKind = node.textOrNull("valueKind"),
        argumentTypes = node.path("argumentTypes").takeIf { it.isArray }
            ?.map { it.asText() }.orEmpty(),
        category = node.textOrNull("category"),
        access = node.textOrNull("access"),
        keyHash = node.textOrNull("keyHash"),
        status = node.textOrNull("status"),
        reason = node.textOrNull("reason"),
    )

    private fun JsonNode.textOrNull(name: String): String? =
        path(name).takeUnless { it.isMissingNode || it.isNull }?.asText()
}

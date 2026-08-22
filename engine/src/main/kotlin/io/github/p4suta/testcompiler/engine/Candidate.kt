package io.github.p4suta.testcompiler.engine

import io.github.p4suta.testcompiler.model.FindingKind

internal data class Candidate(
    val kind: FindingKind,
    val owner: String,
    val method: String,
    val descriptor: String,
    val source: String,
    val line: Int,
    val operator: String,
    val testIds: List<String>,
    val testNames: List<String>,
    val argumentTypes: List<String> = emptyList(),
    val orderSequence: List<String> = emptyList(),
) {
    val target: String get() = "$owner#$method$descriptor"
}

internal data class CandidateCatalog(
    val candidates: List<Candidate>,
    val unsupported: List<Observation>,
)

internal object CandidateFactory {
    fun create(observations: List<Observation>): CandidateCatalog {
        val unsupported = mutableListOf<Observation>()
        val behavioral = observations.asSequence()
            .filter { it.type == "METHOD_RETURN" || it.type == "BRANCH" }
            .flatMap { observation ->
                operators(observation).asSequence().map { operator -> observation to operator }
            }
            .groupBy { (observation, operator) ->
                listOf(observation.owner, observation.method, observation.descriptor, operator).joinToString("\u0000")
            }
            .values
            .mapNotNull { entries ->
                val first = entries.first().first
                if (first.owner == null || first.method == null || first.descriptor == null || first.source == null) {
                    unsupported += first
                    null
                } else if (first.argumentTypes.any { !WitnessFactory.supports(it) }) {
                    unsupported += first
                    null
                } else {
                    Candidate(
                        kind = FindingKind.BEHAVIOR_GAP,
                        owner = first.owner,
                        method = first.method,
                        descriptor = first.descriptor,
                        source = first.source,
                        line = first.line,
                        operator = entries.first().second,
                        testIds = entries.mapNotNull { it.first.test }.distinct().sorted(),
                        testNames = entries.mapNotNull { it.first.testName }.distinct().sorted(),
                        argumentTypes = first.argumentTypes,
                    )
                }
            }

        val effects = observations.asSequence()
            .filter { it.type == "EFFECT" && it.owner != null && it.method != null && it.descriptor != null }
            .groupBy { listOf(it.owner, it.method, it.descriptor, it.source, it.line).joinToString("\u0000") }
            .values
            .map { entries ->
                val first = entries.first()
                Candidate(
                    kind = FindingKind.EFFECT_GAP,
                    owner = requireNotNull(first.owner),
                    method = requireNotNull(first.method),
                    descriptor = requireNotNull(first.descriptor),
                    source = first.source ?: "unknown",
                    line = first.line,
                    operator = "SUPPRESS_EFFECT",
                    testIds = entries.mapNotNull { it.test }.distinct().sorted(),
                    testNames = entries.mapNotNull { it.testName }.distinct().sorted(),
                )
            }

        val order = orderCandidates(observations)
        val all = (behavioral + effects + order).sortedWith(
            compareBy(Candidate::kind, Candidate::owner, Candidate::method, Candidate::descriptor, Candidate::operator),
        )
        return CandidateCatalog(all, unsupported.distinct())
    }

    private fun operators(observation: Observation): List<String> = when (observation.valueKind) {
        "BOOLEAN" -> listOf("BOOLEAN_NEGATE")
        "INT", "LONG", "FLOAT", "DOUBLE" -> listOf("ZERO_RETURN")
        "STRING" -> listOf("EMPTY_STRING", "NULL_RETURN")
        "REFERENCE" -> listOf("NULL_RETURN")
        "BRANCH" -> listOf("NEGATE_BRANCH")
        else -> emptyList()
    }

    private fun orderCandidates(observations: List<Observation>): List<Candidate> {
        val publicNames = observations.mapNotNull { event ->
            event.test?.let { id -> event.testName?.let { name -> id to name } }
        }.toMap()
        return observations.filter { it.type == "GLOBAL_ACCESS" }
            .groupBy { "${it.category}:${it.keyHash}" }
            .flatMap { (resource, accesses) ->
                val writers = accesses.filter { it.access == "WRITE" }.mapNotNull { it.test }.distinct()
                val readers = accesses.filter { it.access == "READ" }.mapNotNull { it.test }.distinct()
                writers.flatMap { writer ->
                    readers.filter { it != writer }.map { reader ->
                        val reversed = listOf(reader, writer)
                        Candidate(
                            kind = FindingKind.ORDER_DEPENDENCY,
                            owner = "global",
                            method = resource,
                            descriptor = "",
                            source = "<global-state>",
                            line = 1,
                            operator = "REVERSE_DEPENDENCY",
                            testIds = listOf(writer, reader),
                            testNames = listOfNotNull(publicNames[writer], publicNames[reader]),
                            orderSequence = reversed,
                        )
                    }
                }
            }
            .distinctBy { it.target + it.testIds.joinToString() }
    }
}

internal object WitnessFactory {
    fun supports(type: String): Boolean = when {
        type == "nullable" -> true
        type in primitiveAndBoxed -> true
        type == "java.lang.String" -> true
        type.startsWith("enum:") || type.startsWith("record:") || type.startsWith("data:") -> true
        type.endsWith("[]") -> true
        type.startsWith("java.util.") || type.startsWith("kotlin.collections.") -> true
        else -> false
    }

    fun value(type: String): String = when {
        type == "boolean" || type == "java.lang.Boolean" -> "false"
        type == "char" || type == "java.lang.Character" -> "\\u0000"
        type in numeric -> "0"
        type == "java.lang.String" -> ""
        type == "nullable" -> "null"
        type.endsWith("[]") || type.startsWith("java.util.") || type.startsWith("kotlin.collections.") -> "[]"
        type.startsWith("enum:") -> "<first-enum-constant>"
        type.startsWith("record:") || type.startsWith("data:") -> "{}"
        else -> "<unsupported>"
    }

    private val numeric = setOf(
        "byte", "short", "int", "long", "float", "double",
        "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
        "java.lang.Float", "java.lang.Double",
    )
    private val primitiveAndBoxed = numeric + setOf(
        "boolean", "char", "java.lang.Boolean", "java.lang.Character",
    )
}

package io.github.p4suta.testcompiler.engine

import io.github.p4suta.testcompiler.model.Finding

internal object FindingIdCollisionResolver {
    fun resolve(findings: List<Finding>): List<Finding> = findings.groupBy { it.id }
        .toSortedMap()
        .flatMap { (id, group) ->
            if (group.size == 1) group
            else group.sortedBy { it.plan.target + "\u0000" + it.plan.operator }.mapIndexed { index, finding ->
                val resolved = "$id-${index + 1}"
                finding.copy(
                    id = resolved,
                    plan = finding.plan.copy(planId = resolved),
                    replayCommand = "testc replay $resolved",
                )
            }
        }
}

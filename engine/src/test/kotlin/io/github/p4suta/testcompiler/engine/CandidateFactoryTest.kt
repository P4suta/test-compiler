package io.github.p4suta.testcompiler.engine

import io.github.p4suta.testcompiler.model.FindingKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CandidateFactoryTest {
    @Test
    fun `creates a deterministic boolean challenge with a zero witness`() {
        val catalog = CandidateFactory.create(
            listOf(
                Observation(
                    type = "METHOD_RETURN",
                    test = "test-id",
                    testName = "demo.ExampleTest#zero",
                    owner = "demo/Example",
                    method = "positive",
                    descriptor = "(I)Z",
                    source = "Example.kt",
                    line = 8,
                    valueKind = "BOOLEAN",
                    argumentTypes = listOf("java.lang.Integer"),
                ),
            ),
        )

        val candidate = catalog.candidates.single()
        assertThat(candidate.kind).isEqualTo(FindingKind.BEHAVIOR_GAP)
        assertThat(candidate.operator).isEqualTo("BOOLEAN_NEGATE")
        assertThat(WitnessFactory.value(candidate.argumentTypes.single())).isEqualTo("0")
    }

    @Test
    fun `derives writer reader order pair`() {
        val catalog = CandidateFactory.create(
            listOf(
                Observation(type = "GLOBAL_ACCESS", test = "writer", testName = "T#writer", category = "system-property", access = "WRITE", keyHash = "a"),
                Observation(type = "GLOBAL_ACCESS", test = "reader", testName = "T#reader", category = "system-property", access = "READ", keyHash = "a"),
            ),
        )

        val candidate = catalog.candidates.single()
        assertThat(candidate.kind).isEqualTo(FindingKind.ORDER_DEPENDENCY)
        assertThat(candidate.orderSequence).containsExactly("reader", "writer")
    }
}

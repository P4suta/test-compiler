package io.github.p4suta.testcompiler.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StableFindingIdTest {
    @Test
    fun `path separators do not affect an id`() {
        val left = StableFindingId.create(
            FindingKind.BEHAVIOR_GAP,
            SourceLocation("src\\main\\java\\Example.java", 7, symbol = "isValid(I)Z"),
            "demo/Example#isValid(I)Z",
            "BOOLEAN_NEGATE",
        )
        val right = StableFindingId.create(
            FindingKind.BEHAVIOR_GAP,
            SourceLocation("src/main/java/Example.java", 7, symbol = "isValid(I)Z"),
            "demo/Example#isValid(I)Z",
            "BOOLEAN_NEGATE",
        )

        assertThat(left).isEqualTo(right).matches("tc-[0-9a-f]{20}")
    }
}

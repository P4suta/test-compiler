package io.github.p4suta.testcompiler.cli

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText

class TestcMainTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `init is non destructive`() {
        assertThat(TestcMain.run(listOf("init"), directory)).isZero()
        val initial = directory.resolve(".test-compiler.toml").readText()
        assertThat(TestcMain.run(listOf("init"), directory)).isZero()
        assertThat(directory.resolve(".test-compiler.toml").readText()).isEqualTo(initial)
    }
}

package io.github.p4suta.testcompiler.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class TestNgAdapterTest {
    @Test
    fun `optional adapter runs an annotated TestNG class`() {
        val root = Path.of(requireNotNull(javaClass.protectionDomain.codeSource).location.toURI())

        val result = TestNgAdapter.run(listOf(root), listOf("testng:${FixtureTestNgCase::class.java.name}#works"))

        assertThat(result.executed).isEqualTo(1)
        assertThat(result.failed).isZero()
    }
}

class FixtureTestNgCase {
    @org.testng.annotations.Test
    fun works() = Unit
}

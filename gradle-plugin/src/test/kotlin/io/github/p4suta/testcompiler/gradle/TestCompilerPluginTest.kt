package io.github.p4suta.testcompiler.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class TestCompilerPluginTest {
    @Test
    fun `registers the fixed public task names`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("java")
        project.pluginManager.apply("io.github.p4suta.test-compiler")

        assertThat(project.tasks.names).contains(
            "testCompilerCheck",
            "testCompilerReplay",
            "testCompilerDoctor",
            "testCompilerBaseline",
        )
    }
}

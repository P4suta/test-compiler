package io.github.p4suta.testcompiler.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class MixedFixtureFunctionalTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun `mixed fixture emits authoritative behavior effect and order diagnostics`() {
        val functionalGradleVersion = System.getProperty("functionalGradleVersion", "9.6.0")
        val fixtureKotlinVersion = if (functionalGradleVersion.startsWith("8.")) "2.2.21" else "2.4.10"
        fixture("settings.gradle.kts", "rootProject.name = \"mixed-fixture\"\n")
        fixture(
            "build.gradle.kts",
            """
                plugins {
                    kotlin("jvm") version "$fixtureKotlinVersion"
                    id("io.github.p4suta.test-compiler")
                }

                repositories { mavenCentral() }

                dependencies {
                    testImplementation(platform("org.junit:junit-bom:6.1.0"))
                    testImplementation("org.junit.jupiter:junit-jupiter")
                    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
                }

                kotlin { jvmToolchain(21) }
                tasks.test { useJUnitPlatform() }

                testCompiler {
                    cacheDirectory.set(layout.buildDirectory.dir("test-compiler-cache"))
                    maxChallenges.set(16)
                    timeoutSeconds.set(60)
                }
            """.trimIndent(),
        )
        fixture(
            "src/main/kotlin/demo/Production.kt",
            """
                package demo

                object Boundary {
                    @JvmStatic fun accepts(x: Int): Boolean = x > 0
                }

                fun interface SemanticMessaging {
                    fun send(message: String)
                }

                class EffectService(private val messaging: SemanticMessaging) {
                    fun notifyCustomer() {
                        messaging.send("payload")
                    }
                }
            """.trimIndent(),
        )
        fixture(
            "src/test/java/demo/BoundaryTest.java",
            """
                package demo;

                import org.junit.jupiter.api.Test;

                final class BoundaryTest {
                    @Test void boundary() {
                        Boundary.accepts(0);
                    }

                    @Test void unobservedEffect() {
                        new EffectService(message -> { }).notifyCustomer();
                    }
                }
            """.trimIndent(),
        )
        fixture(
            "src/test/java/demo/OrderTest.java",
            """
                package demo;

                import static org.junit.jupiter.api.Assertions.assertEquals;
                import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
                import org.junit.jupiter.api.Order;
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.TestMethodOrder;

                @TestMethodOrder(OrderAnnotation.class)
                final class OrderTest {
                    @Test @Order(1) void writer() {
                        System.setProperty("testcompiler.fixture.state", "ready");
                    }

                    @Test @Order(2) void reader() {
                        assertEquals("ready", System.getProperty("testcompiler.fixture.state"));
                    }
                }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments("testCompilerCheck", "--stacktrace", "--configuration-cache")
            .withPluginClasspath()
            .withGradleVersion(functionalGradleVersion)
            .forwardOutput()
            .build()

        assertThat(result.output).contains("test-compiler:")
        val report = projectDirectory.resolve("build/reports/test-compiler/run-report-v1.json")
        val sarif = projectDirectory.resolve("build/reports/test-compiler/test-compiler.sarif")
        val tree = ObjectMapper().readTree(report.toFile())
        val kinds = tree.path("findings").map { it.path("kind").asText() }.toSet()
        assertThat(kinds).contains("BEHAVIOR_GAP", "EFFECT_GAP", "ORDER_DEPENDENCY")
        val behavior = tree.path("findings").first { it.path("kind").asText() == "BEHAVIOR_GAP" }
        assertThat(behavior.path("witness").path("arguments").first().path("value").asText()).isEqualTo("0")
        assertThat(sarif.readText()).contains(behavior.path("id").asText())

        val findingId = behavior.path("id").asText()
        GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments("testCompilerCheck", "--rerun-tasks", "--configuration-cache")
            .withPluginClasspath()
            .withGradleVersion(functionalGradleVersion)
            .build()
        val cachedTree = ObjectMapper().readTree(report.toFile())
        assertThat(cachedTree.path("cache").path("hit").asBoolean()).isTrue()
        assertThat(cachedTree.path("findings").map { it.path("id").asText() }).contains(findingId)

        val replay = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments(
                "testCompilerReplay",
                "-PtestCompiler.finding=$findingId",
                "--no-configuration-cache",
            )
            .withPluginClasspath()
            .withGradleVersion(functionalGradleVersion)
            .build()
        assertThat(replay.output).contains("Reproduced $findingId twice")

        GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments("testCompilerBaseline", "--no-configuration-cache")
            .withPluginClasspath()
            .withGradleVersion(functionalGradleVersion)
            .build()
        assertThat(projectDirectory.resolve(".test-compiler-baseline.json").readText()).contains(findingId)
    }

    @Test
    fun `TestNG is discovered without linking it into the engine`() {
        fixture("settings.gradle.kts", "rootProject.name = \"testng-fixture\"\n")
        fixture(
            "build.gradle.kts",
            """
                plugins {
                    java
                    id("io.github.p4suta.test-compiler")
                }
                repositories { mavenCentral() }
                dependencies { testImplementation("org.testng:testng:7.11.0") }
                java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
                testCompiler {
                    cacheDirectory.set(layout.buildDirectory.dir("test-compiler-cache"))
                    timeoutSeconds.set(60)
                }
            """.trimIndent(),
        )
        fixture(
            "src/main/java/demo/Boundary.java",
            """
                package demo;
                public final class Boundary {
                    public static boolean accepts(int value) { return value > 0; }
                }
            """.trimIndent(),
        )
        fixture(
            "src/test/java/demo/TestNgBoundaryTest.java",
            """
                package demo;
                import org.testng.annotations.Test;
                public final class TestNgBoundaryTest {
                    @Test public void boundary() { Boundary.accepts(0); }
                }
            """.trimIndent(),
        )

        GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments("testCompilerCheck", "--stacktrace", "--no-configuration-cache")
            .withPluginClasspath()
            .withGradleVersion(System.getProperty("functionalGradleVersion", "9.6.0"))
            .build()

        val report = ObjectMapper().readTree(
            projectDirectory.resolve("build/reports/test-compiler/run-report-v1.json").toFile(),
        )
        assertThat(report.path("findings").map { it.path("kind").asText() }).contains("BEHAVIOR_GAP")
        assertThat(report.path("findings").first().path("impactedTests").first().asText()).contains("TestNgBoundaryTest#boundary")
    }

    private fun fixture(relative: String, content: String) {
        val target = projectDirectory.resolve(relative)
        target.parent.createDirectories()
        target.writeText(content + "\n")
    }
}

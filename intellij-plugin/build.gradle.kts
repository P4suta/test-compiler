import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(libs.jackson.databind)
    intellijPlatform {
        intellijIdea("2025.3.6")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("org.jetbrains.kotlin")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
}

intellijPlatform {
    pluginConfiguration {
        id = "io.github.p4suta.test-compiler"
        name = "test-compiler"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "253"
        }
        description = "Deterministic counterfactual diagnostics for JVM test suites."
        changeNotes = "Initial 0.1.0 report, replay, baseline, editor, and Problems integration."
    }
    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdea, "2025.3.6")
            create(IntelliJPlatformType.IntellijIdea, "2026.1")
            create(IntelliJPlatformType.IntellijIdea, "2026.2")
        }
    }
}

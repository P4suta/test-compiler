plugins {
    kotlin("jvm")
    id("com.gradle.plugin-publish")
}

val embeddedAgent = configurations.create("embeddedAgent")
val embeddedEngine = configurations.create("embeddedEngine")

dependencies {
    implementation(project(":model"))
    implementation(project(":engine"))
    embeddedAgent(project(path = ":agent", configuration = "fatJarElements"))
    embeddedEngine(project(path = ":engine", configuration = "fatJarElements"))

    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.jackson.databind)
    testRuntimeOnly(libs.junit.launcher)
}

gradlePlugin {
    plugins {
        create("testCompiler") {
            id = "io.github.p4suta.test-compiler"
            implementationClass = "io.github.p4suta.testcompiler.gradle.TestCompilerPlugin"
            displayName = "test-compiler"
            description = "Deterministic counterfactual diagnostics for JVM test suites"
            website = "https://github.com/p4suta/test-compiler"
            vcsUrl = "https://github.com/p4suta/test-compiler"
            tags = listOf("testing", "mutation-testing", "jvm", "counterfactual")
        }
    }
}

tasks.processResources {
    dependsOn(embeddedAgent, embeddedEngine)
    from(embeddedAgent) {
        rename { "test-compiler-agent.jar" }
    }
    from(embeddedEngine) {
        rename { "test-compiler-engine.jar" }
    }
}

tasks.test {
    project.findProperty("functionalGradleVersion")?.toString()?.let { version ->
        systemProperty("functionalGradleVersion", version)
    }
}

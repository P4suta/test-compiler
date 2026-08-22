package io.github.p4suta.testcompiler.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import java.io.File
import java.nio.file.Path

class TestCompilerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("testCompiler", TestCompilerExtension::class.java)
        project.pluginManager.withPlugin("java") {
            configureJavaProject(project, extension)
        }
    }

    private fun configureJavaProject(project: Project, extension: TestCompilerExtension) {
        val configFile = TomlConfiguration.read(project.layout.projectDirectory.file(".test-compiler.toml").asFile.toPath())
        extension.gate.convention(configFile.gate)
        extension.timeoutSeconds.convention(configFile.timeoutSeconds)
        extension.maxChallenges.convention(configFile.maxChallenges)
        extension.trackedEnvironment.convention(configFile.trackedEnvironment)
        extension.trackedFiles.convention(configFile.trackedFiles)
        extension.customEffects.convention(configFile.customEffects)
        extension.cacheDirectory.convention(project.layout.dir(project.provider { defaultCacheDirectory(project) }))

        val sourceSets = project.extensions.getByType(JavaPluginExtension::class.java).sourceSets
        val main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        val test = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)
        val toolchains = project.extensions.getByType(JavaToolchainService::class.java)
        val java = toolchains.launcherFor { spec -> spec.languageVersion.set(JavaLanguageVersion.of(21)) }
        val reportDirectory = project.layout.buildDirectory.dir("reports/test-compiler")

        fun TestCompilerRuntimeTask.common() {
            dependsOn(test.classesTaskName)
            testRuntimeClasspath.from(test.runtimeClasspath)
            productionRoots.from(main.output.classesDirs)
            testRoots.from(test.output.classesDirs)
            sourceRoots.from(main.allSource.sourceDirectories)
            sourceRoots.from(test.allSource.sourceDirectories)
            this.reportDirectory.set(reportDirectory)
            projectDirectory.set(project.layout.projectDirectory)
            cacheDirectory.set(extension.cacheDirectory)
            javaExecutable.set(java.map { it.executablePath })
            gate.set(project.providers.gradleProperty("testCompiler.gate").orElse(extension.gate))
            changedRef.set(project.providers.gradleProperty("testCompiler.changed").orElse(""))
            timeoutSeconds.set(extension.timeoutSeconds)
            maxChallenges.set(extension.maxChallenges)
            trackedEnvironment.set(extension.trackedEnvironment)
            trackedFiles.set(extension.trackedFiles)
            customEffects.set(extension.customEffects)
            configurationHash.set(configFile.hash)
            gradleVersion.set(project.gradle.gradleVersion)
        }

        project.tasks.register("testCompilerCheck", TestCompilerCheckTask::class.java) { task ->
            task.group = "verification"
            task.description = "Finds deterministic counterfactual gaps in the JVM test suite"
            task.common()
        }
        project.tasks.register("testCompilerReplay", TestCompilerReplayTask::class.java) { task ->
            task.group = "verification"
            task.description = "Replays one saved test-compiler finding"
            task.common()
            task.findingId.set(project.providers.gradleProperty("testCompiler.finding").orElse(""))
        }
        project.tasks.register("testCompilerDoctor", TestCompilerDoctorTask::class.java) { task ->
            task.group = "verification"
            task.description = "Diagnoses the JDK, agent, debug information, and test adapter"
            task.dependsOn(main.classesTaskName, test.classesTaskName)
            task.productionRoots.from(main.output.classesDirs)
            task.testRoots.from(test.output.classesDirs)
            task.testRuntimeClasspath.from(test.runtimeClasspath)
            task.javaExecutable.set(java.map { it.executablePath })
        }
        project.tasks.register("testCompilerBaseline", TestCompilerBaselineTask::class.java) { task ->
            task.group = "verification"
            task.description = "Explicitly baselines the findings in the current report"
            task.reportFile.set(reportDirectory.map { it.file("run-report-v1.json") })
            task.baselineFile.set(project.layout.projectDirectory.file(".test-compiler-baseline.json"))
        }
    }

    private fun defaultCacheDirectory(project: Project): File = Path.of(
            System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),
            ".test-compiler",
            "cache",
        ).toFile()
}

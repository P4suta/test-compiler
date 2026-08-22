package io.github.p4suta.testcompiler.gradle

import io.github.p4suta.testcompiler.engine.BaselineService
import io.github.p4suta.testcompiler.engine.CheckCoordinator
import io.github.p4suta.testcompiler.engine.CheckRequest
import io.github.p4suta.testcompiler.engine.Gate
import io.github.p4suta.testcompiler.engine.ReplayService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@DisableCachingByDefault(because = "Runs supervised external test processes and manages its own complete-input cache")
abstract class TestCompilerRuntimeTask : DefaultTask() {
    @get:Classpath abstract val testRuntimeClasspath: ConfigurableFileCollection
    @get:Classpath abstract val productionRoots: ConfigurableFileCollection
    @get:Classpath abstract val testRoots: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoots: ConfigurableFileCollection
    @get:OutputDirectory abstract val reportDirectory: DirectoryProperty
    @get:Internal abstract val projectDirectory: DirectoryProperty
    @get:Internal abstract val cacheDirectory: DirectoryProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val javaExecutable: RegularFileProperty
    @get:Input abstract val gate: Property<String>
    @get:Input abstract val changedRef: Property<String>
    @get:Input abstract val timeoutSeconds: Property<Long>
    @get:Input abstract val maxChallenges: Property<Int>
    @get:Input abstract val trackedEnvironment: ListProperty<String>
    @get:Input abstract val trackedFiles: ListProperty<String>
    @get:Input abstract val customEffects: ListProperty<String>
    @get:Input abstract val configurationHash: Property<String>
    @get:Input abstract val gradleVersion: Property<String>

    internal fun request(): CheckRequest {
        val output = reportDirectory.get().asFile.toPath()
        val (runner, agent) = RuntimeArtifacts.extract(output)
        return CheckRequest(
            projectRoot = projectDirectory.get().asFile.toPath(),
            outputDirectory = output,
            cacheDirectory = cacheDirectory.get().asFile.toPath(),
            javaExecutable = javaExecutable.get().asFile.toPath(),
            runnerJar = runner,
            agentJar = agent,
            testRuntimeClasspath = testRuntimeClasspath.files.map { it.toPath() },
            productionRoots = productionRoots.files.map { it.toPath() },
            testRoots = testRoots.files.map { it.toPath() },
            sourceRoots = sourceRoots.files.map { it.toPath() },
            gate = Gate.parse(gate.orNull),
            changedRef = changedRef.orNull?.takeIf { it.isNotBlank() },
            timeout = timeoutSeconds.get().seconds,
            maxChallenges = maxChallenges.get(),
            trackedEnvironment = trackedEnvironment.get(),
            trackedFiles = trackedFiles.get().map { projectDirectory.get().asFile.toPath().resolve(it).normalize() },
            customEffects = customEffects.get(),
            configurationHash = configurationHash.get(),
            gradleVersion = gradleVersion.get(),
        )
    }
}

@DisableCachingByDefault(because = "Runs supervised external test processes and manages its own complete-input cache")
abstract class TestCompilerCheckTask : TestCompilerRuntimeTask() {
    @TaskAction
    fun check() {
        val result = CheckCoordinator().check(request())
        logger.lifecycle(
            "test-compiler: {} finding(s), {} new proven; report: {}",
            result.findingCount,
            result.provenCount,
            result.reportPath,
        )
        if (result.baselineFailed) throw GradleException("test-compiler baseline failed; no challenges were run")
        if (result.gateFailed) throw GradleException("test-compiler diagnostic gate failed; see ${result.reportPath}")
    }
}

@DisableCachingByDefault(because = "Replays a saved external-process plan")
abstract class TestCompilerReplayTask : TestCompilerRuntimeTask() {
    @get:Input abstract val findingId: Property<String>

    @TaskAction
    fun replay() {
        val id = findingId.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException("Provide -PtestCompiler.finding=<finding-id>")
        val result = ReplayService().replay(request(), id)
        logger.lifecycle("test-compiler: {}", result.message)
        if (!result.reproduced) throw GradleException(result.message)
    }
}

@DisableCachingByDefault(because = "Produces console diagnostics only")
abstract class TestCompilerDoctorTask : DefaultTask() {
    @get:Classpath abstract val productionRoots: ConfigurableFileCollection
    @get:Classpath abstract val testRoots: ConfigurableFileCollection
    @get:Classpath abstract val testRuntimeClasspath: ConfigurableFileCollection
    @get:InputFile @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val javaExecutable: RegularFileProperty

    @TaskAction
    fun doctor() {
        val feature = Runtime.version().feature()
        val issues = mutableListOf<String>()
        if (feature < 21) issues += "JDK 21 or newer is required (running $feature)"
        if (!javaExecutable.get().asFile.isFile) issues += "Configured Java executable is missing"
        if (productionRoots.files.none { it.exists() }) issues += "No compiled production classes were found"
        if (testRoots.files.none { it.exists() }) issues += "No compiled test classes were found"
        if (!hasDebugLines(productionRoots.files.map { it.toPath() })) {
            issues += "No production LineNumberTable debug information was found"
        }
        runCatching { RuntimeArtifacts.extract(temporaryDir.toPath()) }
            .onFailure { issues += "Embedded Java agent is unreadable: ${it.message}" }
        if (issues.isEmpty()) {
            logger.lifecycle(
                "test-compiler doctor: OK (JDK {}, agent available, debug lines present, adapter={})",
                feature,
                frameworkAdapter(),
            )
        } else {
            issues.forEach { logger.error("test-compiler doctor: {}", it) }
            throw GradleException("test-compiler doctor found ${issues.size} problem(s)")
        }
    }

    private fun frameworkAdapter(): String {
        val names = testRuntimeClasspath.files.map { it.name.lowercase() }
        return when {
            names.any { "junit-platform" in it || "junit-jupiter" in it || "kotest" in it || "spock" in it } -> "junit-platform"
            names.any { "testng" in it } -> "testng"
            else -> "class-level-fallback"
        }
    }

    private fun hasDebugLines(roots: List<Path>): Boolean = roots.any { root ->
        if (!Files.isDirectory(root)) return@any false
        Files.walk(root).use { entries ->
            entries.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".class") }
                .limit(256)
                .anyMatch { classFile -> contains(Files.readAllBytes(classFile), "LineNumberTable".toByteArray()) }
        }
    }

    private fun contains(value: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || value.size < needle.size) return false
        for (offset in 0..value.size - needle.size) {
            if (needle.indices.all { index -> value[offset + index] == needle[index] }) return true
        }
        return false
    }
}

@DisableCachingByDefault(because = "Baseline updates are explicit user-authorized writes")
abstract class TestCompilerBaselineTask : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reportFile: RegularFileProperty
    @get:Internal abstract val baselineFile: RegularFileProperty

    @TaskAction
    fun baseline() {
        val count = BaselineService.update(reportFile.get().asFile.toPath(), baselineFile.get().asFile.toPath())
        logger.lifecycle("test-compiler: baselined {} finding(s) in {}", count, baselineFile.get().asFile)
    }
}

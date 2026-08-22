package io.github.p4suta.testcompiler.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class TestCompilerExtension {
    abstract val gate: Property<String>
    abstract val timeoutSeconds: Property<Long>
    abstract val maxChallenges: Property<Int>
    abstract val trackedEnvironment: ListProperty<String>
    abstract val trackedFiles: ListProperty<String>
    abstract val customEffects: ListProperty<String>
    abstract val cacheDirectory: DirectoryProperty
}

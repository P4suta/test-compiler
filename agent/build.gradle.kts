plugins {
    `java-library`
}

dependencies {
    implementation(libs.asm)
    implementation(libs.asm.commons)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.jar {
    manifest {
        attributes(
            "Premain-Class" to "io.github.p4suta.testcompiler.agent.TestCompilerAgent",
            "Can-Redefine-Classes" to "false",
            "Can-Retransform-Classes" to "false",
            "Implementation-Version" to project.version,
        )
    }
}

val fatJar = tasks.register<Jar>("fatJar") {
    archiveClassifier = "all"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.from(tasks.jar.get().manifest)
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    })
}

configurations.create("fatJarElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
    outgoing.artifact(fatJar)
}

tasks.assemble {
    dependsOn(fatJar)
}

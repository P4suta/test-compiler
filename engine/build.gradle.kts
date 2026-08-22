plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":model"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.kotlin)
    implementation(platform(libs.junit.bom))
    implementation(libs.junit.launcher)
    implementation(libs.junit.engine)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation("org.testng:testng:7.11.0")
    testRuntimeOnly(libs.junit.launcher)
}

val fatJar = tasks.register<Jar>("fatJar") {
    archiveClassifier = "all"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Main-Class" to "io.github.p4suta.testcompiler.engine.WorkerMain")
    }
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

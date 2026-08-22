plugins {
    kotlin("jvm")
    application
}

application {
    mainClass = "io.github.p4suta.testcompiler.cli.TestcMain"
    applicationName = "testc"
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

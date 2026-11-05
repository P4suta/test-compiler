pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "test-compiler"

// Development/release-source composite: Walaru owns the shared JVM execution model.
includeBuild("../walaru")

include(
    "model",
    "agent",
    "engine",
    "cli",
    "gradle-plugin",
    "intellij-plugin",
)

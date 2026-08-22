pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "test-compiler"

include(
    "model",
    "agent",
    "engine",
    "cli",
    "gradle-plugin",
    "intellij-plugin",
)

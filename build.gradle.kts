import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension

plugins {
    kotlin("jvm") version "2.4.20" apply false
    id("org.jetbrains.intellij.platform") version "2.18.1" apply false
    id("com.gradle.plugin-publish") version "2.2.1" apply false
}

allprojects {
    group = "io.github.p4suta.testcompiler"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.release = 21
        options.encoding = "UTF-8"
    }
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
            javaParameters = true
            allWarningsAsErrors = true
        }
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("file.encoding", "UTF-8")
    }

    if (name != "intellij-plugin") {
        pluginManager.withPlugin("java") {
            pluginManager.apply("maven-publish")
            pluginManager.apply("signing")
            extensions.configure<JavaPluginExtension> {
                withSourcesJar()
                withJavadocJar()
            }
            extensions.configure<PublishingExtension> {
                publications {
                    if (findByName("mavenJava") == null) {
                        create<MavenPublication>("mavenJava") {
                            from(components.findByName("java"))
                            pom {
                                name = "test-compiler ${project.name}"
                                description = "Deterministic counterfactual diagnostics for Gradle JVM test suites"
                                url = "https://github.com/p4suta/test-compiler"
                                licenses {
                                    license { name = "MIT"; url = "https://opensource.org/license/mit" }
                                    license { name = "Apache-2.0"; url = "https://www.apache.org/licenses/LICENSE-2.0" }
                                }
                                scm { url = "https://github.com/p4suta/test-compiler" }
                                developers { developer { id = "p4suta"; name = "p4suta" } }
                            }
                        }
                    }
                }
                repositories {
                    maven {
                        name = "centralPortal"
                        url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
                        credentials {
                            username = System.getenv("CENTRAL_USERNAME")
                            password = System.getenv("CENTRAL_PASSWORD")
                        }
                    }
                }
            }
            extensions.configure<SigningExtension> {
                val key = System.getenv("SIGNING_KEY")
                val password = System.getenv("SIGNING_PASSWORD")
                if (!key.isNullOrBlank()) {
                    useInMemoryPgpKeys(key, password)
                    sign(extensions.getByType<PublishingExtension>().publications)
                }
            }
        }
    }
}

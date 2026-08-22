package io.github.p4suta.testcompiler.cli

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

object TestcMain {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val exit = run(arguments.toList(), Path.of("").toAbsolutePath().normalize())
        if (exit != 0) kotlin.system.exitProcess(exit)
    }

    internal fun run(arguments: List<String>, projectRoot: Path): Int {
        if (arguments.isEmpty() || arguments.first() in setOf("help", "--help", "-h")) {
            println(usage())
            return 0
        }
        return when (arguments.first()) {
            "init" -> initialize(projectRoot)
            "check" -> gradle(
                projectRoot,
                "testCompilerCheck",
                arguments.drop(1).mapNotNull { option ->
                    when {
                        option.startsWith("--changed=") -> "-PtestCompiler.changed=${option.substringAfter('=')}"
                        option.startsWith("--gate=") -> "-PtestCompiler.gate=${option.substringAfter('=')}"
                        else -> null
                    }
                },
            )
            "replay" -> {
                val id = arguments.getOrNull(1) ?: return error("replay requires a finding id")
                gradle(projectRoot, "testCompilerReplay", listOf("-PtestCompiler.finding=$id"))
            }
            "doctor" -> gradle(projectRoot, "testCompilerDoctor", emptyList())
            "baseline" -> {
                if (arguments.getOrNull(1) != "update") return error("expected: testc baseline update")
                gradle(projectRoot, "testCompilerBaseline", emptyList())
            }
            else -> error("unknown command: ${arguments.first()}")
        }
    }

    private fun initialize(projectRoot: Path): Int {
        val path = projectRoot.resolve(".test-compiler.toml")
        if (path.exists()) {
            println("Not changed: $path already exists")
            return 0
        }
        Files.writeString(path, CONFIG_TEMPLATE, StandardCharsets.UTF_8)
        println("Created $path")
        return 0
    }

    private fun gradle(projectRoot: Path, task: String, options: List<String>): Int {
        val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        val wrapper = projectRoot.resolve(if (windows) "gradlew.bat" else "gradlew")
        if (!Files.isRegularFile(wrapper)) {
            return error("No Gradle wrapper found at $wrapper")
        }
        val command = buildList {
            if (windows) {
                add("cmd.exe")
                add("/d")
                add("/c")
            }
            add(wrapper.toAbsolutePath().toString())
            add(task)
            add("--console=plain")
            addAll(options)
        }
        return ProcessBuilder(command)
            .directory(projectRoot.toFile())
            .inheritIO()
            .start()
            .waitFor()
    }

    private fun error(message: String): Int {
        System.err.println("testc: $message")
        return 2
    }

    private fun usage(): String = """
        testc - deterministic counterfactual compiler for JVM tests

        Usage:
          testc check [--changed=<git-ref>] [--gate=new|all]
          testc replay <finding-id>
          testc doctor
          testc init
          testc baseline update
    """.trimIndent()

    private const val CONFIG_TEMPLATE = """# test-compiler v1 configuration
# Diagnostics are non-gating unless this is explicitly changed to "new" or "all".
gate = "diagnostic-only"
timeout-seconds = 300
max-challenges = 64

# Environment names are hashed into cache keys; values are never written to reports.
tracked-environment = []
tracked-files = []

# Logging and metrics are intentionally not effects. Add only semantic effects here.
custom-effects = []
"""
}

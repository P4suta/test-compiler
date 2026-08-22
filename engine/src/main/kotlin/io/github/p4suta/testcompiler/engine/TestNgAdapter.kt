package io.github.p4suta.testcompiler.engine

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

internal data class TestNgResult(val executed: Int, val failed: Int)

/** Reflection keeps TestNG optional while still providing method-level context and replay IDs. */
internal object TestNgAdapter {
    fun run(testRoots: List<Path>, selected: List<String>): TestNgResult {
        val testNgClass = runCatching { Class.forName("org.testng.TestNG") }.getOrNull()
            ?: return TestNgResult(0, 0)
        val selectedClasses = selected.map { it.removePrefix("testng:").substringBefore('#') }.toSet()
        val classes = discover(testRoots).filter { candidate ->
            selectedClasses.isEmpty() || candidate.name in selectedClasses
        }
        if (classes.isEmpty()) return TestNgResult(0, 0)

        val runner = testNgClass.getConstructor().newInstance()
        testNgClass.getMethod("setUseDefaultListeners", Boolean::class.javaPrimitiveType).invoke(runner, false)
        testNgClass.getMethod("setTestClasses", arrayOf<Class<*>>().javaClass).invoke(runner, classes.toTypedArray())
        testNgClass.getMethod("run").invoke(runner)
        val failed = testNgClass.getMethod("hasFailure").invoke(runner) as Boolean
        val executed = classes.sumOf { type ->
            type.declaredMethods.count { method ->
                method.declaredAnnotations.any { annotation -> annotation.annotationClass.java.name == "org.testng.annotations.Test" }
            }
        }
        return TestNgResult(executed, if (failed) 1 else 0)
    }

    private fun discover(roots: List<Path>): List<Class<*>> = roots.asSequence().flatMap { root ->
        if (!Files.isDirectory(root)) emptySequence() else Files.walk(root).use { files ->
            files.filter { it.isRegularFile() && it.extension == "class" && '$' !in it.fileName.toString() }
                .map { classFile ->
                    root.relativize(classFile).toString().removeSuffix(".class").replace('/', '.').replace('\\', '.')
                }
                .toList()
                .asSequence()
        }
    }.mapNotNull { name -> runCatching { Class.forName(name, false, Thread.currentThread().contextClassLoader) }.getOrNull() }
        .filter { type ->
            type.declaredMethods.any { method ->
                method.declaredAnnotations.any { annotation -> annotation.annotationClass.java.name == "org.testng.annotations.Test" }
            } || type.declaredAnnotations.any { annotation -> annotation.annotationClass.java.name == "org.testng.annotations.Test" }
        }
        .sortedBy { it.name }
        .toList()

}

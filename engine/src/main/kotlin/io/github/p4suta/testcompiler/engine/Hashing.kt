package io.github.p4suta.testcompiler.engine

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

internal object Hashing {
    fun text(value: String): String = digest().run {
        update(value.toByteArray(StandardCharsets.UTF_8))
        hex(digest())
    }

    fun stream(input: InputStream): String = input.use { source ->
        val digest = digest()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        hex(digest.digest())
    }

    fun cacheKey(request: CheckRequest): String {
        val digest = digest()
        fun token(value: String) {
            digest.update(value.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
        }
        token("cache-v1")
        token(System.getProperty("java.version"))
        token(request.configurationHash)
        token(request.timeout.toString())
        token(request.maxChallenges.toString())
        request.customEffects.sorted().forEach(::token)
        (request.productionRoots + request.testRoots + request.sourceRoots + request.testRuntimeClasspath + request.trackedFiles)
            .map { it.toAbsolutePath().normalize() }
            .distinct()
            .sortedBy { it.toString().replace('\\', '/') }
            .forEach { path ->
                token(request.projectRoot.relativizeOrSelf(path))
                when {
                    path.isRegularFile() -> token(stream(Files.newInputStream(path)))
                    path.isDirectory() -> Files.walk(path).use { entries ->
                        entries.filter { it.isRegularFile() }
                            .sorted()
                            .forEach { file ->
                                token(path.relativize(file).toString().replace('\\', '/'))
                                token(stream(Files.newInputStream(file)))
                            }
                    }
                    else -> token("missing")
                }
            }
        request.trackedEnvironment.sorted().forEach { name ->
            token(name)
            token(text(System.getenv(name) ?: "<unset>"))
        }
        return hex(digest.digest())
    }

    private fun Path.relativizeOrSelf(path: Path): String = try {
        toAbsolutePath().normalize().relativize(path).toString().replace('\\', '/')
    } catch (_: IllegalArgumentException) {
        path.toString().replace('\\', '/')
    }

    private fun digest(): MessageDigest = MessageDigest.getInstance("SHA-256")
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}

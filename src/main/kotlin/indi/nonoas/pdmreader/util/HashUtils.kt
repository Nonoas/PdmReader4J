package indi.nonoas.pdmreader.util

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object HashUtils {
    fun md5(path: Path): String = digest(path, "MD5")

    fun sha256(path: Path): String {
        return digest(path, "SHA-256")
    }

    private fun digest(path: Path, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) {
                    break
                }
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

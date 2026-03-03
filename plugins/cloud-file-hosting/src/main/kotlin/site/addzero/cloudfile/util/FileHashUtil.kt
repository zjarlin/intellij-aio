package site.addzero.cloudfile.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * Utility for computing file hashes and checksums
 */
object FileHashUtil {

    private const val BUFFER_SIZE = 8192

    /**
     * Compute MD5 hash of a file
     */
    fun md5(file: File): String {
        return computeHash(file, "MD5")
    }

    /**
     * Compute SHA-256 hash of a file
     */
    fun sha256(file: File): String {
        return computeHash(file, "SHA-256")
    }

    /**
     * Compute CRC32 checksum of a file (fast, for change detection)
     */
    fun crc32(file: File): Long {
        val crc = CRC32()
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                crc.update(buffer, 0, bytesRead)
            }
        }
        return crc.value
    }

    /**
     * Compute hash of file content for comparison
     */
    fun computeContentHash(file: File): String {
        return sha256(file)
    }

    private fun computeHash(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Get file metadata for comparison
     */
    fun getFileMetadata(file: File): FileMetadata {
        return FileMetadata(
            path = file.path,
            size = file.length(),
            lastModified = file.lastModified(),
            contentHash = computeContentHash(file)
        )
    }

    data class FileMetadata(
        val path: String,
        val size: Long,
        val lastModified: Long,
        val contentHash: String
    )
}

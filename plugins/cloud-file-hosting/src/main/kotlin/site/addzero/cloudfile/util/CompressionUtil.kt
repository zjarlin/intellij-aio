package site.addzero.cloudfile.util

import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Utility for compressing/decompressing files for faster upload/download
 * Each directory is compressed into a single .zip file for upload
 */
object CompressionUtil {

    private const val COMPRESSION_LEVEL = 9 // Best compression
    private const val BUFFER_SIZE = 8192

    /**
     * Compress a directory into a byte array
     * Returns null if compression fails
     */
    fun compressDirectory(sourceDir: File): ByteArray? {
        return try {
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                zos.setLevel(COMPRESSION_LEVEL)
                compressDirectoryInternal(sourceDir, sourceDir, zos)
            }
            baos.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Compress a list of files into a byte array
     */
    fun compressFiles(files: List<File>, baseDir: File): ByteArray? {
        return try {
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                zos.setLevel(COMPRESSION_LEVEL)
                files.forEach { file ->
                    if (file.exists() && file.isFile) {
                        addFileToZip(file, baseDir, zos)
                    }
                }
            }
            baos.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decompress a zip byte array to a target directory
     */
    fun decompressToDirectory(zipData: ByteArray, targetDir: File): Boolean {
        return try {
            targetDir.mkdirs()
            ByteArrayInputStream(zipData).use { bais ->
                ZipInputStream(bais).use { zis ->
                    var entry: ZipEntry?
                    while (zis.nextEntry.also { entry = it } != null) {
                        entry?.let { processEntry(it, zis, targetDir) }
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Calculate hash of compressed content for comparison
     */
    fun computeCompressedHash(data: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(data)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun compressDirectoryInternal(rootDir: File, currentDir: File, zos: ZipOutputStream) {
        currentDir.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> compressDirectoryInternal(rootDir, file, zos)
                file.isFile -> addFileToZip(file, rootDir, zos)
            }
        }
    }

    private fun addFileToZip(file: File, baseDir: File, zos: ZipOutputStream) {
        val relativePath = file.relativeTo(baseDir).path
        val entry = ZipEntry(relativePath)
        entry.time = file.lastModified()
        zos.putNextEntry(entry)
        file.inputStream().use { input ->
            input.copyTo(zos)
        }
        zos.closeEntry()
    }

    private fun processEntry(entry: ZipEntry, zis: ZipInputStream, targetDir: File) {
        val outputFile = File(targetDir, entry.name)
        if (entry.isDirectory) {
            outputFile.mkdirs()
        } else {
            outputFile.parentFile?.mkdirs()
            outputFile.outputStream().use { output ->
                zis.copyTo(output)
            }
            outputFile.setLastModified(entry.time)
        }
    }

    /**
     * Check if a directory should be compressed (too many files or too large)
     */
    fun shouldCompress(files: List<File>): Boolean {
        val totalSize = files.sumOf { it.length() }
        return files.size > 10 || totalSize > 10 * 1024 * 1024 // >10 files or >10MB
    }
}

package site.addzero.cloudfile.cache

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.io.DataInputOutputUtil
import site.addzero.cloudfile.settings.CloudFileSettings
import site.addzero.cloudfile.storage.StorageService
import site.addzero.cloudfile.util.FileHashUtil
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages local cache for offline mode
 * Files are cached locally and synced when network becomes available
 */
class OfflineCacheManager(private val project: Project) {

    private val logger = Logger.getInstance(OfflineCacheManager::class.java)
    private val settings = CloudFileSettings.getInstance()

    private val cacheDir: File by lazy {
        File(project.basePath, ".cloudfile/cache").apply {
            if (!exists()) mkdirs()
        }
    }

    private val metadataFile: File by lazy {
        File(cacheDir, "metadata.dat")
    }

    private val pendingSyncFile: File by lazy {
        File(cacheDir, "pending_sync.dat")
    }

    // In-memory cache of metadata
    private val fileMetadata = ConcurrentHashMap<String, CachedFileInfo>()
    private val pendingUploads = ConcurrentHashMap<String, PendingUpload>()

    init {
        loadMetadata()
        loadPendingSyncs()
    }

    /**
     * Store file to local cache
     */
    fun cacheFile(relativePath: String, content: ByteArray, namespace: String?): Boolean {
        return try {
            val cacheKey = getCacheKey(relativePath, namespace)
            val cacheFile = File(cacheDir, cacheKey)

            cacheFile.parentFile?.mkdirs()
            cacheFile.writeBytes(content)

            val metadata = CachedFileInfo(
                relativePath = relativePath,
                namespace = namespace,
                localPath = cacheFile.absolutePath,
                size = content.size.toLong(),
                cachedTimestamp = System.currentTimeMillis(),
                contentHash = computeHash(content),
                syncedToCloud = false
            )

            fileMetadata[cacheKey] = metadata
            saveMetadata()

            // Add to pending uploads
            pendingUploads[cacheKey] = PendingUpload(
                cacheKey = cacheKey,
                relativePath = relativePath,
                namespace = namespace,
                timestamp = System.currentTimeMillis()
            )
            savePendingSyncs()

            true
        } catch (e: Exception) {
            logger.error("Failed to cache file: $relativePath", e)
            false
        }
    }

    /**
     * Retrieve file from local cache
     */
    fun getCachedFile(relativePath: String, namespace: String?): ByteArray? {
        val cacheKey = getCacheKey(relativePath, namespace)
        val metadata = fileMetadata[cacheKey] ?: return null

        val cacheFile = File(metadata.localPath)
        if (!cacheFile.exists()) {
            fileMetadata.remove(cacheKey)
            saveMetadata()
            return null
        }

        return try {
            cacheFile.readBytes()
        } catch (e: Exception) {
            logger.error("Failed to read cached file: $cacheKey", e)
            null
        }
    }

    /**
     * Check if file exists in cache
     */
    fun isCached(relativePath: String, namespace: String?): Boolean {
        val cacheKey = getCacheKey(relativePath, namespace)
        val metadata = fileMetadata[cacheKey] ?: return false
        return File(metadata.localPath).exists()
    }

    /**
     * Get all cached files for a namespace
     */
    fun getCachedFiles(namespace: String?): List<CachedFileInfo> {
        return fileMetadata.values.filter { it.namespace == namespace }
    }

    /**
     * Mark file as synced to cloud
     */
    fun markAsSynced(relativePath: String, namespace: String?) {
        val cacheKey = getCacheKey(relativePath, namespace)
        fileMetadata[cacheKey]?.let { info ->
            info.syncedToCloud = true
            info.syncedTimestamp = System.currentTimeMillis()
            saveMetadata()
        }
        pendingUploads.remove(cacheKey)
        savePendingSyncs()
    }

    /**
     * Get list of pending uploads
     */
    fun getPendingUploads(): List<PendingUpload> {
        return pendingUploads.values.toList()
    }

    /**
     * Clear cache for a namespace
     */
    fun clearNamespaceCache(namespace: String?) {
        val toRemove = fileMetadata.filter { it.value.namespace == namespace }.keys
        toRemove.forEach { key ->
            fileMetadata[key]?.let { info ->
                try {
                    File(info.localPath).delete()
                } catch (e: Exception) {
                    logger.warn("Failed to delete cache file: ${info.localPath}", e)
                }
            }
            fileMetadata.remove(key)
            pendingUploads.remove(key)
        }
        saveMetadata()
        savePendingSyncs()
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        val totalSize = fileMetadata.values.sumOf { it.size }
        val pendingCount = pendingUploads.size
        val syncedCount = fileMetadata.count { it.value.syncedToCloud }

        return CacheStats(
            totalFiles = fileMetadata.size,
            totalSizeBytes = totalSize,
            pendingUploads = pendingCount,
            syncedFiles = syncedCount,
            cacheDirPath = cacheDir.absolutePath
        )
    }

    /**
     * Clean up old cache files
     */
    fun cleanupOldCache(maxAgeDays: Int = 30) {
        val cutoff = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000)

        val toRemove = fileMetadata.filter { it.value.cachedTimestamp < cutoff }.keys
        toRemove.forEach { key ->
            fileMetadata[key]?.let { info ->
                try {
                    File(info.localPath).delete()
                } catch (e: Exception) {
                    logger.warn("Failed to delete old cache file", e)
                }
            }
            fileMetadata.remove(key)
            pendingUploads.remove(key)
        }

        saveMetadata()
        savePendingSyncs()
    }

    private fun getCacheKey(relativePath: String, namespace: String?): String {
        val safePath = relativePath.replace("/", "_").replace("\\", "_")
        return if (namespace != null) {
            "ns_${namespace}_$safePath"
        } else {
            "global_$safePath"
        }
    }

    private fun computeHash(content: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(content)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun loadMetadata() {
        if (!metadataFile.exists()) return

        try {
            DataInputStream(FileInputStream(metadataFile)).use { input ->
                val count = input.readInt()
                repeat(count) {
                    val key = input.readUTF()
                    val info = CachedFileInfo(
                        relativePath = input.readUTF(),
                        namespace = input.readUTF().takeIf { it.isNotEmpty() },
                        localPath = input.readUTF(),
                        size = input.readLong(),
                        cachedTimestamp = input.readLong(),
                        contentHash = input.readUTF(),
                        syncedToCloud = input.readBoolean()
                    ).apply {
                        syncedTimestamp = input.readLong()
                    }
                    fileMetadata[key] = info
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load cache metadata", e)
        }
    }

    private fun saveMetadata() {
        try {
            DataOutputStream(FileOutputStream(metadataFile)).use { output ->
                output.writeInt(fileMetadata.size)
                fileMetadata.forEach { (key, info) ->
                    output.writeUTF(key)
                    output.writeUTF(info.relativePath)
                    output.writeUTF(info.namespace ?: "")
                    output.writeUTF(info.localPath)
                    output.writeLong(info.size)
                    output.writeLong(info.cachedTimestamp)
                    output.writeUTF(info.contentHash)
                    output.writeBoolean(info.syncedToCloud)
                    output.writeLong(info.syncedTimestamp)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to save cache metadata", e)
        }
    }

    private fun loadPendingSyncs() {
        if (!pendingSyncFile.exists()) return

        try {
            DataInputStream(FileInputStream(pendingSyncFile)).use { input ->
                val count = input.readInt()
                repeat(count) {
                    val upload = PendingUpload(
                        cacheKey = input.readUTF(),
                        relativePath = input.readUTF(),
                        namespace = input.readUTF().takeIf { it.isNotEmpty() },
                        timestamp = input.readLong()
                    )
                    pendingUploads[upload.cacheKey] = upload
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load pending syncs", e)
        }
    }

    private fun savePendingSyncs() {
        try {
            DataOutputStream(FileOutputStream(pendingSyncFile)).use { output ->
                output.writeInt(pendingUploads.size)
                pendingUploads.values.forEach { upload ->
                    output.writeUTF(upload.cacheKey)
                    output.writeUTF(upload.relativePath)
                    output.writeUTF(upload.namespace ?: "")
                    output.writeLong(upload.timestamp)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to save pending syncs", e)
        }
    }

    data class CachedFileInfo(
        val relativePath: String,
        val namespace: String?,
        val localPath: String,
        val size: Long,
        val cachedTimestamp: Long,
        val contentHash: String,
        var syncedToCloud: Boolean = false,
        var syncedTimestamp: Long = 0L
    )

    data class PendingUpload(
        val cacheKey: String,
        val relativePath: String,
        val namespace: String?,
        val timestamp: Long
    )

    data class CacheStats(
        val totalFiles: Int,
        val totalSizeBytes: Long,
        val pendingUploads: Int,
        val syncedFiles: Int,
        val cacheDirPath: String
    ) {
        fun totalSizeMB(): Double = totalSizeBytes / (1024.0 * 1024.0)
    }

    companion object {
        fun getInstance(project: Project): OfflineCacheManager {
            return project.getService(OfflineCacheManager::class.java)
        }
    }
}

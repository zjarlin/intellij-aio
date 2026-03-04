package site.addzero.cloudfile.cache

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.io.DataInputOutputUtil
import site.addzero.cloudfile.settings.CloudFileSettings
import site.addzero.cloudfile.storage.StorageService
import java.io.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages local cache for offline mode
 * Files are cached locally and synced when network becomes available
 *
 * Cache locations:
 * - Project cache: $PROJECT/.cloud-file-hosting/cache/
 * - Global cache: $IDE_SYSTEM_DIR/cloud-file-hosting/cache/global/
 */
class OfflineCacheManager(private val project: Project) {

    private val logger = Logger.getInstance(OfflineCacheManager::class.java)
    private val settings = CloudFileSettings.getInstance()

    companion object {
        const val CACHE_DIR_NAME = ".cloud-file-hosting"
        const val CACHE_SUBDIR = "cache"

        fun getInstance(project: Project): OfflineCacheManager {
            return project.getService(OfflineCacheManager::class.java)
        }
    }

    private val projectCacheDir: File by lazy {
        File(project.basePath, "$CACHE_DIR_NAME/$CACHE_SUBDIR").apply {
            if (!exists()) mkdirs()
        }
    }

    private val globalCacheDir: File by lazy {
        File(PathManager.getSystemPath(), "$CACHE_DIR_NAME/$CACHE_SUBDIR/global").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun getCacheDir(namespace: String?): File {
        return if (namespace == null) {
            globalCacheDir
        } else {
            projectCacheDir
        }
    }

    private fun getMetadataFile(namespace: String?): File {
        return File(getCacheDir(namespace), "metadata.dat")
    }

    private fun getPendingSyncFile(namespace: String?): File {
        return File(getCacheDir(namespace), "pending_sync.dat")
    }

    // In-memory cache of metadata - separated by namespace type
    private val projectFileMetadata = ConcurrentHashMap<String, CachedFileInfo>()
    private val globalFileMetadata = ConcurrentHashMap<String, CachedFileInfo>()
    private val pendingUploads = ConcurrentHashMap<String, PendingUpload>()

    init {
        loadMetadata(null) // Load global
        loadMetadata(project.name) // Load project
        loadPendingSyncs()
    }

    private fun getMetadataMap(namespace: String?): ConcurrentHashMap<String, CachedFileInfo> {
        return if (namespace == null) globalFileMetadata else projectFileMetadata
    }

    /**
     * Store file to local cache
     */
    fun cacheFile(relativePath: String, content: ByteArray, namespace: String?): Boolean {
        return try {
            val cacheKey = getCacheKey(relativePath, namespace)
            val cacheDir = getCacheDir(namespace)
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

            getMetadataMap(namespace)[cacheKey] = metadata
            saveMetadata(namespace)

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
        val metadataMap = getMetadataMap(namespace)
        val metadata = metadataMap[cacheKey] ?: return null

        val cacheFile = File(metadata.localPath)
        if (!cacheFile.exists()) {
            metadataMap.remove(cacheKey)
            saveMetadata(namespace)
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
        val metadataMap = getMetadataMap(namespace)
        val metadata = metadataMap[cacheKey] ?: return false
        return File(metadata.localPath).exists()
    }

    /**
     * Get all cached files for a namespace
     */
    fun getCachedFiles(namespace: String?): List<CachedFileInfo> {
        return getMetadataMap(namespace).values.toList()
    }

    /**
     * Get all cached files across all namespaces
     */
    fun getAllCachedFiles(): List<CachedFileInfo> {
        return (projectFileMetadata.values + globalFileMetadata.values).toList()
    }

    /**
     * Mark file as synced to cloud
     */
    fun markAsSynced(relativePath: String, namespace: String?) {
        val cacheKey = getCacheKey(relativePath, namespace)
        val metadataMap = getMetadataMap(namespace)
        metadataMap[cacheKey]?.let { info ->
            info.syncedToCloud = true
            info.syncedTimestamp = System.currentTimeMillis()
            saveMetadata(namespace)
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
        val metadataMap = getMetadataMap(namespace)
        val toRemove = metadataMap.filter { it.value.namespace == namespace }.keys
        toRemove.forEach { key ->
            metadataMap[key]?.let { info ->
                try {
                    File(info.localPath).delete()
                } catch (e: Exception) {
                    logger.warn("Failed to delete cache file: ${info.localPath}", e)
                }
            }
            metadataMap.remove(key)
            pendingUploads.remove(key)
        }
        saveMetadata(namespace)
        savePendingSyncs()
    }

    /**
     * Clear all cache (both global and project)
     */
    fun clearAllCache() {
        // Clear project cache
        clearNamespaceCache(project.name)
        // Clear global cache
        clearNamespaceCache(null)
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(namespace: String? = null): CacheStats {
        val metadata = if (namespace != null) {
            getMetadataMap(namespace).values.toList()
        } else {
            getAllCachedFiles()
        }

        val totalSize = metadata.sumOf { it.size }
        val pendingCount = if (namespace != null) {
            pendingUploads.count { it.value.namespace == namespace }
        } else {
            pendingUploads.size
        }
        val syncedCount = metadata.count { it.syncedToCloud }

        return CacheStats(
            totalFiles = metadata.size,
            totalSizeBytes = totalSize,
            pendingUploads = pendingCount,
            syncedFiles = syncedCount,
            cacheDirPath = if (namespace == null) globalCacheDir.absolutePath else projectCacheDir.absolutePath
        )
    }

    /**
     * Get global cache statistics
     */
    fun getGlobalCacheStats(): CacheStats {
        return getCacheStats(null)
    }

    /**
     * Get project cache statistics
     */
    fun getProjectCacheStats(): CacheStats {
        return getCacheStats(project.name)
    }

    /**
     * Clean up old cache files
     */
    fun cleanupOldCache(maxAgeDays: Int = 30) {
        val cutoff = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000)

        // Clean project cache
        projectFileMetadata.filter { it.value.cachedTimestamp < cutoff }.keys.forEach { key ->
            projectFileMetadata[key]?.let { info ->
                try {
                    File(info.localPath).delete()
                } catch (e: Exception) {
                    logger.warn("Failed to delete old cache file", e)
                }
            }
            projectFileMetadata.remove(key)
            pendingUploads.remove(key)
        }

        // Clean global cache
        globalFileMetadata.filter { it.value.cachedTimestamp < cutoff }.keys.forEach { key ->
            globalFileMetadata[key]?.let { info ->
                try {
                    File(info.localPath).delete()
                } catch (e: Exception) {
                    logger.warn("Failed to delete old global cache file", e)
                }
            }
            globalFileMetadata.remove(key)
            pendingUploads.remove(key)
        }

        saveMetadata(project.name)
        saveMetadata(null)
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

    private fun loadMetadata(namespace: String?) {
        val metadataFile = getMetadataFile(namespace)
        if (!metadataFile.exists()) return

        val metadataMap = getMetadataMap(namespace)

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
                    metadataMap[key] = info
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load cache metadata for ${namespace ?: "global"}", e)
        }
    }

    private fun saveMetadata(namespace: String?) {
        val metadataFile = getMetadataFile(namespace)
        val metadataMap = getMetadataMap(namespace)

        try {
            DataOutputStream(FileOutputStream(metadataFile)).use { output ->
                output.writeInt(metadataMap.size)
                metadataMap.forEach { (key, info) ->
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
            logger.error("Failed to save cache metadata for ${namespace ?: "global"}", e)
        }
    }

    private fun loadPendingSyncs() {
        // Load from both locations
        loadPendingSyncsFromDir(projectCacheDir)
        loadPendingSyncsFromDir(globalCacheDir)
    }

    private fun loadPendingSyncsFromDir(cacheDir: File) {
        val pendingFile = File(cacheDir, "pending_sync.dat")
        if (!pendingFile.exists()) return

        try {
            DataInputStream(FileInputStream(pendingFile)).use { input ->
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
            logger.error("Failed to load pending syncs from ${cacheDir.path}", e)
        }
    }

    private fun savePendingSyncs() {
        // Save to both locations
        savePendingSyncsToDir(projectCacheDir)
        savePendingSyncsToDir(globalCacheDir)
    }

    private fun savePendingSyncsToDir(cacheDir: File) {
        val pendingFile = File(cacheDir, "pending_sync.dat")
        try {
            DataOutputStream(FileOutputStream(pendingFile)).use { output ->
                output.writeInt(pendingUploads.size)
                pendingUploads.values.forEach { upload ->
                    output.writeUTF(upload.cacheKey)
                    output.writeUTF(upload.relativePath)
                    output.writeUTF(upload.namespace ?: "")
                    output.writeLong(upload.timestamp)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to save pending syncs to ${cacheDir.path}", e)
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
}

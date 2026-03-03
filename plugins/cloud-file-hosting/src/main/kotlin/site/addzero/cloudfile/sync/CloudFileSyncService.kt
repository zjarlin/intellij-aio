package site.addzero.cloudfile.sync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.*
import site.addzero.cloudfile.cache.OfflineCacheManager
import site.addzero.cloudfile.git.GitIntegrationService
import site.addzero.cloudfile.settings.CloudFileSettings
import site.addzero.cloudfile.settings.ProjectHostingSettings
import site.addzero.cloudfile.storage.StorageService
import site.addzero.cloudfile.storage.StorageServiceFactory
import site.addzero.cloudfile.util.FileHashUtil
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Paths

/**
 * Core synchronization service for Cloud File Hosting
 * Handles upload/download logic with offline caching support
 */
class CloudFileSyncService(private val project: Project) {

    private val logger = Logger.getInstance(CloudFileSyncService::class.java)
    private val settings = CloudFileSettings.getInstance()
    private val projectSettings = ProjectHostingSettings.getInstance(project)
    private val gitService = GitIntegrationService.getInstance(project)
    private val cacheManager = OfflineCacheManager.getInstance(project)

    private var storageService: StorageService? = null
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Initialize storage service
     */
    fun initializeStorage(): Boolean {
        if (storageService != null) return true

        return try {
            storageService = when (settings.state.provider) {
                CloudFileSettings.StorageProvider.S3 -> {
                    val accessKey = settings.getS3AccessKey() ?: return false
                    val secretKey = settings.getS3SecretKey() ?: return false
                    StorageServiceFactory.createS3Service(
                        endpoint = settings.state.s3Endpoint,
                        region = settings.state.s3Region,
                        bucket = settings.state.s3Bucket,
                        accessKey = accessKey,
                        secretKey = secretKey
                    )
                }
                CloudFileSettings.StorageProvider.OSS -> {
                    val accessKeyId = settings.getOssAccessKeyId() ?: return false
                    val accessKeySecret = settings.getOssAccessKeySecret() ?: return false
                    StorageServiceFactory.createOssService(
                        endpoint = settings.state.ossEndpoint,
                        bucket = settings.state.ossBucket,
                        accessKeyId = accessKeyId,
                        accessKeySecret = accessKeySecret
                    )
                }
            }
            storageService?.testConnection() ?: false
        } catch (e: Exception) {
            logger.error("Failed to initialize storage service", e)
            false
        }
    }

    /**
     * Sync files to cloud (upload)
     * Falls back to local cache if network is unavailable
     */
    fun syncToCloud(force: Boolean = false, indicator: ProgressIndicator? = null) {
        val namespace = projectSettings.getNamespace(project)
        val rules = projectSettings.getEffectiveRules(project)

        if (rules.isEmpty()) {
            showNotification("No hosting rules", "No files configured for cloud hosting")
            return
        }

        val filesToSync = collectMatchingFiles(rules)
        if (filesToSync.isEmpty()) {
            showNotification("No files to sync", "No files matched the hosting rules")
            return
        }

        // Try to initialize storage, but continue with caching if unavailable
        val hasNetwork = initializeStorage()
        val service = storageService

        if (!hasNetwork) {
            showNotification("Offline Mode", "Files will be cached locally and synced when network is available")
        }

        val syncedFiles = mutableListOf<ProjectHostingSettings.SyncedFileInfo>()
        var successCount = 0
        var failCount = 0
        var cachedCount = 0

        indicator?.isIndeterminate = false
        indicator?.fraction = 0.0

        filesToSync.forEachIndexed { index, file ->
            indicator?.fraction = (index + 1).toDouble() / filesToSync.size
            indicator?.text = "Processing ${file.name}..."

            val relativePath = file.relativeTo(project.basePath?.let { File(it) } ?: file.parentFile).path

            // Always cache the file locally
            val content = file.readBytes()
            val cached = cacheManager.cacheFile(relativePath, content, namespace)

            if (hasNetwork && service != null) {
                // Try to upload to cloud
                val result = service.uploadFile(file, relativePath, namespace)

                if (result.success) {
                    successCount++
                    cacheManager.markAsSynced(relativePath, namespace)
                    syncedFiles.add(ProjectHostingSettings.SyncedFileInfo(
                        relativePath = relativePath,
                        remoteEtag = result.etag ?: "",
                        lastModified = file.lastModified(),
                        size = file.length()
                    ))
                } else {
                    failCount++
                    logger.warn("Failed to upload ${file.path}: ${result.error}. File cached locally.")
                }
            } else if (cached) {
                // File cached locally for later sync
                cachedCount++
            }
        }

        projectSettings.recordSync(syncedFiles)

        when {
            !hasNetwork -> {
                showNotification("Cached Offline", "$cachedCount files cached locally. Will sync when network is available.")
            }
            failCount == 0 -> {
                showNotification("Sync Complete", "Successfully synced $successCount files to cloud")
            }
            else -> {
                showNotification("Sync Complete with Errors", "Success: $successCount, Failed: $failCount, Cached: $cachedCount")
            }
        }
    }

    /**
     * Sync files from cloud (download/overwrite local)
     * Falls back to cache if network is unavailable
     */
    fun syncFromCloud(indicator: ProgressIndicator? = null) {
        val namespace = projectSettings.getNamespace(project)
        val hasNetwork = initializeStorage()
        val service = storageService

        if (hasNetwork && service != null) {
            // List remote files
            indicator?.text = "Listing remote files..."
            val remoteFiles = service.listFiles("", namespace)

            if (remoteFiles.isEmpty()) {
                showNotification("No remote files", "No files found in cloud for this project")
                return
            }

            val basePath = project.basePath ?: return
            var successCount = 0
            var failCount = 0

            indicator?.isIndeterminate = false
            indicator?.fraction = 0.0

            remoteFiles.forEachIndexed { index, remoteFile ->
                indicator?.fraction = (index + 1).toDouble() / remoteFiles.size
                indicator?.text = "Downloading ${remoteFile.key}..."

                val localFile = File(basePath, remoteFile.key)

                // Backup existing file if different
                if (localFile.exists()) {
                    backupFile(localFile)
                }

                if (service.downloadFile(remoteFile.key, localFile, namespace)) {
                    successCount++
                    // Cache the downloaded file
                    cacheManager.cacheFile(remoteFile.key, localFile.readBytes(), namespace)
                    cacheManager.markAsSynced(remoteFile.key, namespace)

                    // Refresh VFS
                    ApplicationManager.getApplication().invokeLater {
                        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localFile)
                    }
                } else {
                    failCount++
                }
            }

            if (failCount == 0) {
                showNotification("Download Complete", "Successfully downloaded $successCount files from cloud")
            } else {
                showNotification("Download Complete with Errors", "Success: $successCount, Failed: $failCount")
            }
        } else {
            // Network unavailable - use cache
            showNotification("Offline Mode", "Using locally cached files")
            restoreFromCache(namespace)
        }
    }

    /**
     * Restore files from local cache when offline
     */
    fun restoreFromCache(namespace: String?) {
        val basePath = project.basePath ?: return
        val cachedFiles = cacheManager.getCachedFiles(namespace)

        var restoredCount = 0
        cachedFiles.forEach { cachedFile ->
            val localFile = File(basePath, cachedFile.relativePath)
            try {
                val content = cacheManager.getCachedFile(cachedFile.relativePath, namespace)
                if (content != null) {
                    localFile.parentFile?.mkdirs()
                    localFile.writeBytes(content)
                    restoredCount++

                    ApplicationManager.getApplication().invokeLater {
                        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localFile)
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to restore file from cache: ${cachedFile.relativePath}", e)
            }
        }

        showNotification("Cache Restored", "Restored $restoredCount files from local cache")
    }

    /**
     * Sync pending uploads when network becomes available
     */
    fun syncPendingUploads(indicator: ProgressIndicator? = null) {
        val service = storageService ?: return
        val namespace = projectSettings.getNamespace(project)
        val pendingUploads = cacheManager.getPendingUploads()

        if (pendingUploads.isEmpty()) return

        indicator?.text = "Syncing ${pendingUploads.size} pending uploads..."
        var successCount = 0

        pendingUploads.forEach { upload ->
            val content = cacheManager.getCachedFile(upload.relativePath, upload.namespace)
            if (content != null) {
                val tempFile = File.createTempFile("cloudfile_sync_", ".tmp")
                try {
                    tempFile.writeBytes(content)
                    val result = service.uploadFile(tempFile, upload.relativePath, upload.namespace)
                    if (result.success) {
                        successCount++
                        cacheManager.markAsSynced(upload.relativePath, upload.namespace)
                    }
                } finally {
                    tempFile.delete()
                }
            }
        }

        if (successCount > 0) {
            showNotification("Pending Sync Complete", "Synced $successCount/${pendingUploads.size} pending files")
        }
    }

    /**
     * Handle file change event - auto-sync if enabled
     */
    fun handleFileChange(event: VFileEvent) {
        if (!projectSettings.state.autoSyncOnChange) return
        if (!settings.state.autoSync) return

        val file = event.file ?: return
        if (!shouldSyncFile(file)) return

        // Debounce rapid changes
        syncScope.launch {
            delay(1000) // Wait 1 second after last change
            withContext(Dispatchers.IO) {
                val localFile = File(file.path)
                if (localFile.exists()) {
                    syncSingleFile(localFile)
                }
            }
        }
    }

    /**
     * Sync a single file with offline support
     */
    private fun syncSingleFile(file: File) {
        val namespace = projectSettings.getNamespace(project)
        val basePath = project.basePath?.let { File(it) } ?: return
        val relativePath = file.relativeTo(basePath).path

        // Always cache locally first
        val content = file.readBytes()
        cacheManager.cacheFile(relativePath, content, namespace)

        // Try to upload if network is available
        if (initializeStorage()) {
            val service = storageService ?: return
            val result = service.uploadFile(file, relativePath, namespace)
            if (result.success) {
                cacheManager.markAsSynced(relativePath, namespace)
            }
        }
    }

    /**
     * Collect all files matching the hosting rules
     */
    private fun collectMatchingFiles(rules: List<CloudFileSettings.HostingRule>): List<File> {
        val basePath = project.basePath?.let { File(it) } ?: return emptyList()
        val result = mutableSetOf<File>()

        rules.forEach { rule ->
            when (rule.type) {
                CloudFileSettings.HostingRule.RuleType.FILE -> {
                    val file = File(basePath, rule.pattern)
                    if (file.exists() && file.isFile) {
                        result.add(file)
                    }
                }
                CloudFileSettings.HostingRule.RuleType.DIRECTORY -> {
                    val dir = File(basePath, rule.pattern)
                    if (dir.exists() && dir.isDirectory) {
                        dir.walkTopDown()
                            .filter { it.isFile }
                            .filter { !isGitIgnored(it) }
                            .forEach { result.add(it) }
                    }
                }
                CloudFileSettings.HostingRule.RuleType.GLOB -> {
                    val matcher = FileSystems.getDefault().getPathMatcher("glob:${rule.pattern}")
                    basePath.walkTopDown()
                        .filter { it.isFile }
                        .filter { matcher.matches(Paths.get(it.relativeTo(basePath).path)) }
                        .filter { !isGitIgnored(it) }
                        .forEach { result.add(it) }
                }
            }
        }

        return result.toList()
    }

    /**
     * Check if file should be synced based on rules
     */
    private fun shouldSyncFile(file: VirtualFile): Boolean {
        val rules = projectSettings.getEffectiveRules(project)
        val basePath = project.basePath ?: return false

        return rules.any { rule ->
            val relativePath = file.path.removePrefix(basePath).removePrefix("/")
            when (rule.type) {
                CloudFileSettings.HostingRule.RuleType.FILE ->
                    relativePath == rule.pattern
                CloudFileSettings.HostingRule.RuleType.DIRECTORY ->
                    relativePath.startsWith(rule.pattern.removeSuffix("/") + "/")
                CloudFileSettings.HostingRule.RuleType.GLOB -> {
                    val matcher = FileSystems.getDefault().getPathMatcher("glob:${rule.pattern}")
                    matcher.matches(Paths.get(relativePath))
                }
            }
        }
    }

    /**
     * Check if file is ignored by Git
     */
    private fun isGitIgnored(file: File): Boolean {
        // Simplified check - would integrate with GitIndexUtil in full implementation
        return false
    }

    /**
     * Backup file before overwriting
     */
    private fun backupFile(file: File) {
        val backupDir = File(project.basePath, ".cloudfile/backups")
        backupDir.mkdirs()
        val backupFile = File(backupDir, "${file.name}.${System.currentTimeMillis()}.bak")
        file.copyTo(backupFile, overwrite = true)
    }

    /**
     * Show notification to user
     */
    private fun showNotification(title: String, content: String) {
        com.intellij.notification.NotificationGroupManager.getInstance()
            .getNotificationGroup("CloudFile Notifications")
            .createNotification(title, content, com.intellij.notification.NotificationType.INFORMATION)
            .notify(project)
    }

    fun dispose() {
        syncScope.cancel()
        storageService?.close()
    }

    companion object {
        fun getInstance(project: Project): CloudFileSyncService {
            return project.getService(CloudFileSyncService::class.java)
        }
    }
}

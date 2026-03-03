package site.addzero.cloudfile.share

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import site.addzero.cloudfile.cache.OfflineCacheManager
import site.addzero.cloudfile.settings.CloudFileSettings
import site.addzero.cloudfile.settings.ProjectHostingSettings
import site.addzero.cloudfile.storage.StorageService
import site.addzero.cloudfile.util.FileHashUtil
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Manages share links for cloud file hosting configurations and files
 * Supports: JSON export/import, Base64 encoded share strings, QR code generation
 */
class ShareLinkManager(private val project: Project) {

    private val logger = Logger.getInstance(ShareLinkManager::class.java)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Generate a share link/package for current configuration
     */
    fun generateSharePackage(
        includeGlobalRules: Boolean = true,
        includeProjectRules: Boolean = true,
        includeFiles: Boolean = false,
        filePatterns: List<String> = emptyList()
    ): SharePackage {
        val settings = CloudFileSettings.getInstance()
        val projectSettings = ProjectHostingSettings.getInstance(project)

        val globalRules = if (includeGlobalRules) {
            settings.state.globalRules
        } else {
            emptyList()
        }

        val customRules = if (includeGlobalRules) {
            settings.state.customRules
        } else {
            emptyList()
        }

        val projectRules = if (includeProjectRules) {
            projectSettings.state.projectRules
        } else {
            emptyList()
        }

        val files = if (includeFiles) {
            collectFilesToShare(filePatterns)
        } else {
            emptyList()
        }

        return SharePackage(
            version = SHARE_VERSION,
            createdAt = System.currentTimeMillis(),
            sharedBy = System.getProperty("user.name"),
            globalRules = globalRules.map { RuleExport.from(it) },
            customRules = customRules.map { CustomRuleExport.from(it) },
            projectRules = projectRules.map { RuleExport.from(it) },
            files = files,
            storageProvider = settings.state.provider.name,
            namespace = projectSettings.getNamespace(project)
        )
    }

    /**
     * Export share package to JSON string
     */
    fun exportToJson(packageData: SharePackage): String {
        return gson.toJson(packageData)
    }

    /**
     * Export share package to Base64 encoded string (compact format)
     */
    fun exportToBase64(packageData: SharePackage): String {
        val json = exportToJson(packageData)
        return Base64.getUrlEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Import from JSON string
     */
    fun importFromJson(json: String): SharePackage? {
        return try {
            gson.fromJson(json, SharePackage::class.java)
        } catch (e: Exception) {
            logger.error("Failed to parse share package JSON", e)
            null
        }
    }

    /**
     * Import from Base64 encoded string
     */
    fun importFromBase64(base64: String): SharePackage? {
        return try {
            val json = String(Base64.getUrlDecoder().decode(base64), StandardCharsets.UTF_8)
            importFromJson(json)
        } catch (e: Exception) {
            logger.error("Failed to decode share package Base64", e)
            null
        }
    }

    /**
     * Apply imported share package to current project
     */
    fun applySharePackage(
        packageData: SharePackage,
        applyGlobalRules: Boolean = false,
        applyProjectRules: Boolean = true,
        applyFiles: Boolean = true,
        conflictStrategy: ConflictStrategy = ConflictStrategy.ASK
    ): ApplyResult {
        val settings = CloudFileSettings.getInstance()
        val projectSettings = ProjectHostingSettings.getInstance(project)
        val applied = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // Apply global rules (only if explicitly requested)
        if (applyGlobalRules && packageData.globalRules.isNotEmpty()) {
            packageData.globalRules.forEach { ruleExport ->
                val exists = settings.state.globalRules.any { it.pattern == ruleExport.pattern }
                if (!exists) {
                    settings.state.globalRules.add(
                        CloudFileSettings.HostingRule(
                            pattern = ruleExport.pattern,
                            type = CloudFileSettings.HostingRule.RuleType.valueOf(ruleExport.type),
                            enabled = ruleExport.enabled
                        )
                    )
                    applied.add("Global rule: ${ruleExport.pattern}")
                } else {
                    skipped.add("Global rule: ${ruleExport.pattern} (already exists)")
                }
            }
        }

        // Apply custom rules
        if (applyGlobalRules && packageData.customRules.isNotEmpty()) {
            packageData.customRules.forEach { customExport ->
                val exists = settings.state.customRules.any {
                    it.gitAuthorPattern == customExport.gitAuthorPattern &&
                    it.projectNamePattern == customExport.projectNamePattern
                }
                if (!exists) {
                    settings.state.customRules.add(
                        CloudFileSettings.CustomHostingRule(
                            gitAuthorPattern = customExport.gitAuthorPattern,
                            projectNamePattern = customExport.projectNamePattern,
                            rules = customExport.rules.map {
                                CloudFileSettings.HostingRule(
                                    pattern = it.pattern,
                                    type = CloudFileSettings.HostingRule.RuleType.valueOf(it.type),
                                    enabled = it.enabled
                                )
                            }.toMutableList(),
                            priority = customExport.priority,
                            enabled = customExport.enabled
                        )
                    )
                    applied.add("Custom rule: ${customExport.gitAuthorPattern}/${customExport.projectNamePattern}")
                } else {
                    skipped.add("Custom rule: ${customExport.gitAuthorPattern} (already exists)")
                }
            }
        }

        // Apply project rules
        if (applyProjectRules && packageData.projectRules.isNotEmpty()) {
            packageData.projectRules.forEach { ruleExport ->
                val exists = projectSettings.state.projectRules.any { it.pattern == ruleExport.pattern }
                if (!exists) {
                    projectSettings.addProjectRule(
                        ruleExport.pattern,
                        CloudFileSettings.HostingRule.RuleType.valueOf(ruleExport.type)
                    )
                    applied.add("Project rule: ${ruleExport.pattern}")
                } else {
                    when (conflictStrategy) {
                        ConflictStrategy.OVERWRITE -> {
                            projectSettings.removeProjectRule(ruleExport.pattern)
                            projectSettings.addProjectRule(
                                ruleExport.pattern,
                                CloudFileSettings.HostingRule.RuleType.valueOf(ruleExport.type)
                            )
                            applied.add("Project rule: ${ruleExport.pattern} (overwritten)")
                        }
                        ConflictStrategy.SKIP -> {
                            skipped.add("Project rule: ${ruleExport.pattern} (skipped)")
                        }
                        ConflictStrategy.ASK -> {
                            skipped.add("Project rule: ${ruleExport.pattern} (needs user decision)")
                        }
                    }
                }
            }
        }

        // Apply files
        if (applyFiles && packageData.files.isNotEmpty()) {
            val cacheManager = OfflineCacheManager.getInstance(project)

            packageData.files.forEach { fileExport ->
                try {
                    val content = Base64.getDecoder().decode(fileExport.contentBase64)
                    val relativePath = fileExport.relativePath

                    // Write to local file
                    val localFile = File(project.basePath, relativePath)
                    val shouldWrite = when (conflictStrategy) {
                        ConflictStrategy.OVERWRITE -> true
                        ConflictStrategy.SKIP -> !localFile.exists()
                        ConflictStrategy.ASK -> !localFile.exists()
                    }

                    if (shouldWrite) {
                        localFile.parentFile?.mkdirs()
                        localFile.writeBytes(content)

                        // Also cache for offline use
                        cacheManager.cacheFile(relativePath, content, packageData.namespace)

                        applied.add("File: $relativePath")

                        // Refresh VFS
                        ApplicationManager.getApplication().invokeLater {
                            com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localFile)
                        }
                    } else {
                        skipped.add("File: $relativePath (already exists)")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to apply file: ${fileExport.relativePath}", e)
                    errors.add("File: ${fileExport.relativePath} - ${e.message}")
                }
            }
        }

        return ApplyResult(
            applied = applied,
            skipped = skipped,
            errors = errors,
            success = errors.isEmpty()
        )
    }

    /**
     * Upload share package to paste service and get URL
     * (Placeholder for future integration with paste services)
     */
    fun uploadToPasteService(packageData: SharePackage): String? {
        // This is a placeholder - could integrate with:
        // - GitHub Gists
        // - Pastebin
        // - Self-hosted paste service
        // - S3 with pre-signed URL
        return null
    }

    private fun collectFilesToShare(patterns: List<String>): List<FileExport> {
        val files = mutableListOf<FileExport>()
        val basePath = project.basePath ?: return files

        patterns.forEach { pattern ->
            val matcher = java.nio.file.FileSystems.getDefault()
                .getPathMatcher("glob:$pattern")

            File(basePath).walkTopDown()
                .filter { it.isFile }
                .filter { matcher.matches(java.nio.file.Paths.get(it.relativeTo(File(basePath)).path)) }
                .filter { !isGitIgnored(it) }
                .take(100) // Limit to prevent huge shares
                .forEach { file ->
                    try {
                        val content = file.readBytes()
                        files.add(
                            FileExport(
                                relativePath = file.relativeTo(File(basePath)).path,
                                contentBase64 = Base64.getEncoder().encodeToString(content),
                                size = file.length(),
                                lastModified = file.lastModified()
                            )
                        )
                    } catch (e: Exception) {
                        logger.warn("Failed to read file for sharing: ${file.path}", e)
                    }
                }
        }

        return files
    }

    private fun isGitIgnored(file: File): Boolean {
        // Simplified - would check .gitignore in full implementation
        return file.name.startsWith(".") &&
               file.name != ".github" &&
               file.name != ".gitignore"
    }

    data class SharePackage(
        val version: Int,
        val createdAt: Long,
        val sharedBy: String,
        val globalRules: List<RuleExport>,
        val customRules: List<CustomRuleExport>,
        val projectRules: List<RuleExport>,
        val files: List<FileExport>,
        val storageProvider: String,
        val namespace: String
    )

    data class RuleExport(
        val pattern: String,
        val type: String,
        val enabled: Boolean
    ) {
        companion object {
            fun from(rule: CloudFileSettings.HostingRule): RuleExport {
                return RuleExport(
                    pattern = rule.pattern,
                    type = rule.type.name,
                    enabled = rule.enabled
                )
            }
        }
    }

    data class CustomRuleExport(
        val gitAuthorPattern: String,
        val projectNamePattern: String,
        val rules: List<RuleExport>,
        val priority: Int,
        val enabled: Boolean
    ) {
        companion object {
            fun from(rule: CloudFileSettings.CustomHostingRule): CustomRuleExport {
                return CustomRuleExport(
                    gitAuthorPattern = rule.gitAuthorPattern,
                    projectNamePattern = rule.projectNamePattern,
                    rules = rule.rules.map { RuleExport.from(it) },
                    priority = rule.priority,
                    enabled = rule.enabled
                )
            }
        }
    }

    data class FileExport(
        val relativePath: String,
        val contentBase64: String,
        val size: Long,
        val lastModified: Long
    )

    data class ApplyResult(
        val applied: List<String>,
        val skipped: List<String>,
        val errors: List<String>,
        val success: Boolean
    )

    enum class ConflictStrategy {
        ASK,      // Ask user for each conflict
        OVERWRITE, // Overwrite existing
        SKIP      // Skip existing
    }

    companion object {
        private const val SHARE_VERSION = 1

        fun getInstance(project: Project): ShareLinkManager {
            return project.getService(ShareLinkManager::class.java)
        }
    }
}

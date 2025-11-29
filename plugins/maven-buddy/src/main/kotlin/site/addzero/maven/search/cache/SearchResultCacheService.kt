package site.addzero.maven.search.cache

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import site.addzero.maven.search.settings.MavenSearchSettings
import site.addzero.network.call.maven.util.MavenArtifact
import java.io.File

/**
 * Maven 搜索结果持久化缓存服务
 * 
 * 全局存储，与项目无关
 * 以 groupId:artifactId 为 key 缓存单个 artifact，避免 keyword 缓存导致的不精确问题
 */
@Service(Service.Level.APP)
class SearchResultCacheService {

    private val logger = Logger.getInstance(SearchResultCacheService::class.java)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private var data: CacheData = CacheData()
    private var lastLoadedPath: String? = null

    init {
        loadFromFile()
    }

    /** 最大缓存条目数 */
    var maxCacheSize: Int
        get() = data.maxCacheSize
        set(value) {
            data.maxCacheSize = value
            saveToFile()
        }

    /** 缓存过期时间（毫秒），默认7天 */
    var cacheTtlMs: Long
        get() = data.cacheTtlMs
        set(value) {
            data.cacheTtlMs = value
            saveToFile()
        }

    /** 是否启用缓存 */
    var enableCache: Boolean
        get() = data.enableCache
        set(value) {
            data.enableCache = value
            saveToFile()
        }

    /**
     * 获取当前存储路径
     */
    fun getStoragePath(): String = MavenSearchSettings.getInstance().cacheStoragePath

    /**
     * 确保数据已加载（路径变更时重新加载）
     */
    private fun ensureLoaded() {
        val currentPath = getStoragePath()
        if (lastLoadedPath != currentPath) {
            loadFromFile()
        }
    }

    /**
     * 从文件加载缓存数据
     */
    fun loadFromFile() {
        val path = getStoragePath()
        lastLoadedPath = path
        val file = File(path)

        if (!file.exists()) {
            data = CacheData()
            return
        }

        runCatching {
            val json = file.readText()
            data = gson.fromJson(json, CacheData::class.java) ?: CacheData()
            clearExpired()
        }.onFailure { e ->
            logger.warn("Failed to load cache from $path", e)
            e.printStackTrace()
            data = CacheData()
        }
    }

    /**
     * 保存缓存数据到文件
     */
    fun saveToFile() {
        val path = getStoragePath()
        val file = File(path)

        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(data))
        }.onFailure { e ->
            logger.warn("Failed to save cache to $path", e)
            e.printStackTrace()
        }
    }

    /**
     * 根据关键词从缓存中匹配 artifact
     * 匹配规则：groupId 或 artifactId 包含关键词（忽略大小写）
     */
    fun match(keyword: String, limit: Int = 50): List<MavenArtifact> {
        ensureLoaded()
        if (!enableCache || keyword.isBlank()) return emptyList()

        val lowerKeyword = keyword.lowercase().trim()
        val now = System.currentTimeMillis()

        return data.artifacts.values
            .asSequence()
            .filter { now - it.timestamp <= cacheTtlMs }
            .filter { cached ->
                cached.groupId.lowercase().contains(lowerKeyword) ||
                cached.artifactId.lowercase().contains(lowerKeyword) ||
                "${cached.groupId}:${cached.artifactId}".lowercase().contains(lowerKeyword)
            }
            .sortedByDescending { it.timestamp }
            .take(limit)
            .map { it.toMavenArtifact() }
            .toList()
    }

    /**
     * 批量添加 artifact 到缓存
     * key 为 groupId:artifactId，自动去重
     */
    fun addAll(artifacts: List<MavenArtifact>) {
        ensureLoaded()
        if (!enableCache || artifacts.isEmpty()) return

        artifacts.forEach { artifact ->
            val key = "${artifact.groupId}:${artifact.artifactId}"
            // 更新或添加，保留最新版本信息
            val existing = data.artifacts[key]
            if (existing == null || artifact.timestamp > existing.timestamp) {
                data.artifacts[key] = CachedArtifact.fromMavenArtifact(artifact)
            }
        }

        trimToSize()
        saveToFile()
    }

    /**
     * 检查缓存中是否有匹配的结果
     */
    fun hasMatch(keyword: String): Boolean {
        ensureLoaded()
        if (!enableCache || keyword.isBlank()) return false

        val lowerKeyword = keyword.lowercase().trim()
        val now = System.currentTimeMillis()

        return data.artifacts.values.any { cached ->
            now - cached.timestamp <= cacheTtlMs &&
            (cached.groupId.lowercase().contains(lowerKeyword) ||
             cached.artifactId.lowercase().contains(lowerKeyword) ||
             "${cached.groupId}:${cached.artifactId}".lowercase().contains(lowerKeyword))
        }
    }

    /**
     * 清除所有缓存
     */
    fun clearAll() {
        data.artifacts.clear()
        saveToFile()
    }

    /**
     * 清除过期缓存
     */
    fun clearExpired() {
        val now = System.currentTimeMillis()
        val removed = data.artifacts.entries.removeIf { now - it.value.timestamp > cacheTtlMs }
        if (removed) saveToFile()
    }

    /**
     * 获取缓存统计信息
     */
    fun stats(): CacheStats {
        ensureLoaded()
        clearExpired()
        return CacheStats(
            totalEntries = data.artifacts.size,
            totalArtifacts = data.artifacts.size
        )
    }

    private fun trimToSize() {
        if (data.artifacts.size > maxCacheSize) {
            // 按时间排序，移除最旧的
            val sorted = data.artifacts.entries.sortedBy { it.value.timestamp }
            val toRemove = sorted.take(data.artifacts.size - maxCacheSize)
            toRemove.forEach { data.artifacts.remove(it.key) }
        }
    }

    companion object {
        fun getInstance(): SearchResultCacheService =
            ApplicationManager.getApplication().getService(SearchResultCacheService::class.java)
    }
}

/**
 * 缓存数据容器
 * key: groupId:artifactId
 */
data class CacheData(
    var artifacts: MutableMap<String, CachedArtifact> = mutableMapOf(),
    var maxCacheSize: Int = 500,
    var cacheTtlMs: Long = 7 * 24 * 60 * 60 * 1000L,
    var enableCache: Boolean = true
)

/**
 * 可序列化的工件信息
 */
data class CachedArtifact(
    var id: String = "",
    var groupId: String = "",
    var artifactId: String = "",
    var version: String = "",
    var latestVersion: String = "",
    var packaging: String = "jar",
    var timestamp: Long = 0L,
    var repositoryId: String = "central"
) {
    constructor() : this("", "", "", "", "", "jar", 0L, "central")

    fun toMavenArtifact() = MavenArtifact(
        id = id,
        groupId = groupId,
        artifactId = artifactId,
        version = version,
        latestVersion = latestVersion,
        packaging = packaging,
        timestamp = timestamp,
        repositoryId = repositoryId
    )

    companion object {
        fun fromMavenArtifact(artifact: MavenArtifact) = CachedArtifact(
            id = artifact.id,
            groupId = artifact.groupId,
            artifactId = artifact.artifactId,
            version = artifact.version,
            latestVersion = artifact.latestVersion,
            packaging = artifact.packaging,
            timestamp = artifact.timestamp,
            repositoryId = artifact.repositoryId
        )
    }
}

data class CacheStats(
    val totalEntries: Int,
    val totalArtifacts: Int
)

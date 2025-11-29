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
 * 使用 JSON 文件存储，路径可在设置中配置
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
     * 获取缓存的搜索结果
     * @return 缓存的结果列表，如果没有或已过期则返回 null
     */
    operator fun get(keyword: String): List<MavenArtifact>? {
        ensureLoaded()
        if (!enableCache || keyword.isBlank()) return null

        val normalizedKey = keyword.lowercase().trim()
        val cached = data.cacheEntries[normalizedKey] ?: return null

        // 检查是否过期
        if (System.currentTimeMillis() - cached.timestamp > cacheTtlMs) {
            data.cacheEntries.remove(normalizedKey)
            saveToFile()
            return null
        }

        return cached.toArtifacts()
    }

    /**
     * 缓存搜索结果
     */
    operator fun set(keyword: String, artifacts: List<MavenArtifact>) {
        ensureLoaded()
        if (!enableCache || keyword.isBlank() || artifacts.isEmpty()) return

        val normalizedKey = keyword.lowercase().trim()

        // 移除旧条目（如果存在）
        data.cacheEntries.remove(normalizedKey)

        // 确保不超过最大缓存数
        trimToSize()

        // 添加新缓存
        data.cacheEntries[normalizedKey] = CachedSearchResult.fromArtifacts(artifacts)
        saveToFile()
    }

    /**
     * 检查是否有缓存
     */
    fun contains(keyword: String): Boolean {
        ensureLoaded()
        if (!enableCache || keyword.isBlank()) return false
        val normalizedKey = keyword.lowercase().trim()
        val cached = data.cacheEntries[normalizedKey] ?: return false

        // 检查是否过期
        if (System.currentTimeMillis() - cached.timestamp > cacheTtlMs) {
            data.cacheEntries.remove(normalizedKey)
            saveToFile()
            return false
        }
        return true
    }

    /**
     * 清除所有缓存
     */
    fun clearAll() {
        data.cacheEntries.clear()
        saveToFile()
    }

    /**
     * 清除过期缓存
     */
    fun clearExpired() {
        val now = System.currentTimeMillis()
        val removed = data.cacheEntries.entries.removeIf { now - it.value.timestamp > cacheTtlMs }
        if (removed) saveToFile()
    }

    /**
     * 获取缓存统计信息
     */
    fun stats(): CacheStats {
        ensureLoaded()
        clearExpired()
        return CacheStats(
            totalEntries = data.cacheEntries.size,
            totalArtifacts = data.cacheEntries.values.sumOf { it.artifacts.size }
        )
    }

    private fun trimToSize() {
        if (data.cacheEntries.size >= maxCacheSize) {
            // 按时间排序，移除最旧的
            val sorted = data.cacheEntries.entries.sortedBy { it.value.timestamp }
            val toRemove = sorted.take(data.cacheEntries.size - maxCacheSize + 1)
            toRemove.forEach { data.cacheEntries.remove(it.key) }
        }
    }

    companion object {
        fun getInstance(): SearchResultCacheService =
            ApplicationManager.getApplication().getService(SearchResultCacheService::class.java)
    }
}

/**
 * 缓存数据容器
 */
data class CacheData(
    var cacheEntries: MutableMap<String, CachedSearchResult> = mutableMapOf(),
    var maxCacheSize: Int = 200,
    var cacheTtlMs: Long = 7 * 24 * 60 * 60 * 1000L,
    var enableCache: Boolean = true
)

/**
 * 缓存的搜索结果
 */
data class CachedSearchResult(
    var artifacts: MutableList<CachedArtifact> = mutableListOf(),
    var timestamp: Long = 0L,
    var totalResults: Long = 0L
) {
    constructor() : this(mutableListOf(), 0L, 0L)

    fun toArtifacts(): List<MavenArtifact> = artifacts.map { it.toMavenArtifact() }

    companion object {
        fun fromArtifacts(list: List<MavenArtifact>, total: Long = list.size.toLong()) = CachedSearchResult(
            artifacts = list.map { CachedArtifact.fromMavenArtifact(it) }.toMutableList(),
            timestamp = System.currentTimeMillis(),
            totalResults = total
        )
    }
}

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

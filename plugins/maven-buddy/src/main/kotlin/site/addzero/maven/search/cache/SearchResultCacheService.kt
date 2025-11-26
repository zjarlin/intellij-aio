package site.addzero.maven.search.cache

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import site.addzero.network.call.maven.util.MavenArtifact

/**
 * Maven 搜索结果持久化缓存服务
 * 
 * 缓存搜索过的关键词和对应结果，避免重复调用 API
 */
@State(
    name = "MavenSearchResultCache",
    storages = [Storage("MavenSearchResultCache.xml")]
)
class SearchResultCacheService : PersistentStateComponent<SearchResultCacheService> {

    /** 缓存条目：keyword -> CachedSearchResult */
    var cacheEntries: MutableMap<String, CachedSearchResult> = mutableMapOf()

    /** 最大缓存条目数 */
    var maxCacheSize: Int = 200

    /** 缓存过期时间（毫秒），默认7天 */
    var cacheTtlMs: Long = 7 * 24 * 60 * 60 * 1000L

    /** 是否启用缓存 */
    var enableCache: Boolean = true

    override fun getState(): SearchResultCacheService = this

    override fun loadState(state: SearchResultCacheService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    /**
     * 获取缓存的搜索结果
     * @return 缓存的结果列表，如果没有或已过期则返回 null
     */
    operator fun get(keyword: String): List<MavenArtifact>? {
        if (!enableCache || keyword.isBlank()) return null
        
        val normalizedKey = keyword.lowercase().trim()
        val cached = cacheEntries[normalizedKey] ?: return null
        
        // 检查是否过期
        if (System.currentTimeMillis() - cached.timestamp > cacheTtlMs) {
            cacheEntries.remove(normalizedKey)
            return null
        }
        
        return cached.toArtifacts()
    }

    /**
     * 缓存搜索结果
     */
    operator fun set(keyword: String, artifacts: List<MavenArtifact>) {
        if (!enableCache || keyword.isBlank() || artifacts.isEmpty()) return
        
        val normalizedKey = keyword.lowercase().trim()
        
        // 移除旧条目（如果存在）
        cacheEntries.remove(normalizedKey)
        
        // 确保不超过最大缓存数
        trimToSize()
        
        // 添加新缓存
        cacheEntries[normalizedKey] = CachedSearchResult.fromArtifacts(artifacts)
    }

    /**
     * 检查是否有缓存
     */
    fun contains(keyword: String): Boolean {
        if (!enableCache || keyword.isBlank()) return false
        val normalizedKey = keyword.lowercase().trim()
        val cached = cacheEntries[normalizedKey] ?: return false
        
        // 检查是否过期
        if (System.currentTimeMillis() - cached.timestamp > cacheTtlMs) {
            cacheEntries.remove(normalizedKey)
            return false
        }
        return true
    }

    /**
     * 清除所有缓存
     */
    fun clearAll() = cacheEntries.clear()

    /**
     * 清除过期缓存
     */
    fun clearExpired() {
        val now = System.currentTimeMillis()
        cacheEntries.entries.removeIf { now - it.value.timestamp > cacheTtlMs }
    }

    /**
     * 获取缓存统计信息
     */
    fun stats(): CacheStats {
        clearExpired()
        return CacheStats(
            totalEntries = cacheEntries.size,
            totalArtifacts = cacheEntries.values.sumOf { it.artifacts.size }
        )
    }

    private fun trimToSize() {
        if (cacheEntries.size >= maxCacheSize) {
            // 按时间排序，移除最旧的
            val sorted = cacheEntries.entries.sortedBy { it.value.timestamp }
            val toRemove = sorted.take(cacheEntries.size - maxCacheSize + 1)
            toRemove.forEach { cacheEntries.remove(it.key) }
        }
    }

    companion object {
        fun getInstance(): SearchResultCacheService =
            ApplicationManager.getApplication().getService(SearchResultCacheService::class.java)
    }
}

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

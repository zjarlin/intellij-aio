package site.addzero.gradle.buddy.intentions.completion

import com.intellij.openapi.diagnostic.Logger

/**
 * 安全桥接 maven-buddy-core 的服务。
 *
 * gradle-buddy 对 maven-buddy-core 是 compileOnly 依赖，
 * 运行时 maven-buddy 插件可能未安装。
 * 所有对 maven-buddy-core 服务的访问都通过此桥接类，
 * ClassNotFoundException / NoClassDefFoundError 时静默降级。
 */
object MavenBuddyBridge {

    private val LOG = Logger.getInstance(MavenBuddyBridge::class.java)

    /** maven-buddy 插件是否可用（运行时检测，缓存结果） */
    val isAvailable: Boolean by lazy {
        try {
            Class.forName("site.addzero.maven.search.history.SearchHistoryService")
            true
        } catch (_: Throwable) {
            LOG.info("maven-buddy plugin not available, search history/cache features disabled")
            false
        }
    }

    // ── SearchHistoryService ────────────────────────────────────────

    val historyEnabled: Boolean
        get() = if (!isAvailable) false else try {
            site.addzero.maven.search.history.SearchHistoryService.getInstance().enableHistory
        } catch (_: Throwable) { false }

    fun recentArtifacts(limit: Int = 10): List<Any> =
        if (!isAvailable) emptyList() else try {
            site.addzero.maven.search.history.SearchHistoryService.getInstance().recentArtifacts(limit)
        } catch (_: Throwable) { emptyList() }

    fun matchArtifacts(query: String, limit: Int = 5): List<Any> =
        if (!isAvailable) emptyList() else try {
            site.addzero.maven.search.history.SearchHistoryService.getInstance().matchArtifacts(query, limit)
        } catch (_: Throwable) { emptyList() }

    fun recordHistory(groupId: String, artifactId: String, version: String) {
        if (!isAvailable) return
        try {
            site.addzero.maven.search.history.SearchHistoryService.getInstance().record(groupId, artifactId, version)
        } catch (_: Throwable) { /* silent */ }
    }

    // ── SearchResultCacheService ────────────────────────────────────

    fun cacheMatch(query: String, limit: Int = 20): List<Any> =
        if (!isAvailable) emptyList() else try {
            site.addzero.maven.search.cache.SearchResultCacheService.getInstance().match(query, limit)
        } catch (_: Throwable) { emptyList() }

    fun cacheAddAll(artifacts: List<Any>) {
        if (!isAvailable) return
        try {
            @Suppress("UNCHECKED_CAST")
            site.addzero.maven.search.cache.SearchResultCacheService.getInstance()
                .addAll(artifacts as List<site.addzero.network.call.maven.util.MavenArtifact>)
        } catch (_: Throwable) { /* silent */ }
    }

    // ── MavenSearchSettings ─────────────────────────────────────────

    val pageSize: Int
        get() = if (!isAvailable) 15 else try {
            site.addzero.maven.search.settings.MavenSearchSettings.getInstance().pageSize
        } catch (_: Throwable) { 15 }

    // ── ArtifactHistoryEntry helpers ────────────────────────────────

    /** 从 Any 安全提取 ArtifactHistoryEntry 的字段 */
    fun entryGroupId(entry: Any): String = try {
        (entry as site.addzero.maven.search.history.ArtifactHistoryEntry).groupId
    } catch (_: Throwable) { "" }

    fun entryArtifactId(entry: Any): String = try {
        (entry as site.addzero.maven.search.history.ArtifactHistoryEntry).artifactId
    } catch (_: Throwable) { "" }

    fun entryVersion(entry: Any): String = try {
        (entry as site.addzero.maven.search.history.ArtifactHistoryEntry).version
    } catch (_: Throwable) { "" }
}

package site.addzero.maven.search.history

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Maven 搜索历史持久化服务
 * 
 * 使用 LinkedHashSet 语义自动去重，保持插入顺序
 * 添加操作是透明的，自动处理去重和大小限制
 */
@State(
    name = "MavenSearchHistory",
    storages = [Storage("MavenSearchHistory.xml")]
)
class SearchHistoryService : PersistentStateComponent<SearchHistoryService> {

    /**
     * 搜索关键词历史（自动去重，最近的在前）
     */
    var searchKeywords: MutableList<String> = mutableListOf()

    /**
     * 选择的依赖历史（按 groupId:artifactId 去重，最近的在前）
     */
    var selectedArtifacts: MutableList<ArtifactHistoryEntry> = mutableListOf()

    var maxKeywordHistorySize: Int = 50
    var maxArtifactHistorySize: Int = 100
    var enableHistory: Boolean = true

    override fun getState(): SearchHistoryService = this

    override fun loadState(state: SearchHistoryService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    /**
     * 记录搜索关键词（自动去重，移到最前）
     */
    operator fun plusAssign(keyword: String) {
        if (!enableHistory || keyword.isBlank() || keyword.length < 2) return
        searchKeywords.remove(keyword)
        searchKeywords.add(0, keyword)
        trimToSize(searchKeywords, maxKeywordHistorySize)
    }

    /**
     * 记录选择的依赖（自动按 groupId:artifactId 去重，移到最前）
     */
    operator fun plusAssign(artifact: ArtifactHistoryEntry) {
        if (!enableHistory) return
        selectedArtifacts.removeIf { it.key == artifact.key }
        selectedArtifacts.add(0, artifact.copy(timestamp = System.currentTimeMillis()))
        trimToSize(selectedArtifacts, maxArtifactHistorySize)
    }

    /**
     * 快捷方式：记录依赖
     */
    fun record(groupId: String, artifactId: String, version: String) {
        this += ArtifactHistoryEntry(groupId, artifactId, version)
    }

    /**
     * 快捷方式：记录关键词
     */
    fun record(keyword: String) {
        this += keyword
    }

    /**
     * 获取最近的依赖历史
     */
    fun recentArtifacts(limit: Int = 15): List<ArtifactHistoryEntry> =
        selectedArtifacts.take(limit)

    /**
     * 获取最近的关键词历史
     */
    fun recentKeywords(limit: Int = 10): List<String> =
        searchKeywords.take(limit)

    /**
     * 匹配关键词历史
     */
    fun matchKeywords(query: String, limit: Int = 10): List<String> {
        if (!enableHistory || query.isBlank()) return emptyList()
        val lowerQuery = query.lowercase()
        return searchKeywords
            .filter { it.lowercase().contains(lowerQuery) }
            .take(limit)
    }

    /**
     * 匹配依赖历史
     */
    fun matchArtifacts(query: String, limit: Int = 10): List<ArtifactHistoryEntry> {
        if (!enableHistory || query.isBlank()) return emptyList()
        val lowerQuery = query.lowercase()
        return selectedArtifacts
            .filter { it.matches(lowerQuery) }
            .take(limit)
    }

    fun clearKeywords() = searchKeywords.clear()
    fun clearArtifacts() = selectedArtifacts.clear()
    fun clearAll() {
        clearKeywords()
        clearArtifacts()
    }

    private fun <T> trimToSize(list: MutableList<T>, maxSize: Int) {
        while (list.size > maxSize) list.removeLast()
    }

    companion object {
        fun getInstance(): SearchHistoryService =
            ApplicationManager.getApplication().getService(SearchHistoryService::class.java)
    }
}

/**
 * 依赖历史条目
 */
data class ArtifactHistoryEntry(
    var groupId: String = "",
    var artifactId: String = "",
    var version: String = "",
    var timestamp: Long = 0L
) {
    constructor() : this("", "", "", 0L)
    
    /** 用于去重的唯一键 */
    val key: String get() = "$groupId:$artifactId"
    
    val coordinate: String get() = "$groupId:$artifactId:$version"
    
    fun matches(query: String): Boolean =
        groupId.lowercase().contains(query) ||
        artifactId.lowercase().contains(query) ||
        key.lowercase().contains(query)
}

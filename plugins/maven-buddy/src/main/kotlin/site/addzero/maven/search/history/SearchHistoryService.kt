package site.addzero.maven.search.history

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import site.addzero.maven.search.settings.MavenSearchSettings
import java.io.File

/**
 * Maven 搜索历史持久化服务
 * 
 * 全局存储，与项目无关
 * 使用 JSON 文件存储，路径可在设置中配置
 */
@Service(Service.Level.APP)
class SearchHistoryService {

    private val logger = Logger.getInstance(SearchHistoryService::class.java)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private var data: HistoryData = HistoryData()
    private var lastLoadedPath: String? = null

    init {
        loadFromFile()
    }

    /**
     * 搜索关键词历史（自动去重，最近的在前）
     */
    val searchKeywords: MutableList<String>
        get() {
            ensureLoaded()
            return data.searchKeywords
        }

    /**
     * 选择的依赖历史（按 groupId:artifactId 去重，最近的在前）
     */
    val selectedArtifacts: MutableList<ArtifactHistoryEntry>
        get() {
            ensureLoaded()
            return data.selectedArtifacts
        }

    var maxKeywordHistorySize: Int
        get() = data.maxKeywordHistorySize
        set(value) {
            data.maxKeywordHistorySize = value
            saveToFile()
        }

    var maxArtifactHistorySize: Int
        get() = data.maxArtifactHistorySize
        set(value) {
            data.maxArtifactHistorySize = value
            saveToFile()
        }

    var enableHistory: Boolean
        get() = data.enableHistory
        set(value) {
            data.enableHistory = value
            saveToFile()
        }

    /**
     * 获取当前存储路径
     */
    fun getStoragePath(): String = MavenSearchSettings.getInstance().historyStoragePath

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
     * 从文件加载历史数据
     */
    fun loadFromFile() {
        val path = getStoragePath()
        lastLoadedPath = path
        val file = File(path)
        
        if (!file.exists()) {
            data = HistoryData()
            return
        }

        runCatching {
            val json = file.readText()
            data = gson.fromJson(json, HistoryData::class.java) ?: HistoryData()
        }.onFailure { e ->
            logger.warn("Failed to load history from $path", e)
            e.printStackTrace()
            data = HistoryData()
        }
    }

    /**
     * 保存历史数据到文件
     */
    fun saveToFile() {
        val path = getStoragePath()
        val file = File(path)
        
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(data))
        }.onFailure { e ->
            logger.warn("Failed to save history to $path", e)
            e.printStackTrace()
        }
    }

    /**
     * 记录搜索关键词（自动去重，移到最前）
     */
    operator fun plusAssign(keyword: String) {
        if (!enableHistory || keyword.isBlank() || keyword.length < 2) return
        searchKeywords.remove(keyword)
        searchKeywords.add(0, keyword)
        trimToSize(searchKeywords, maxKeywordHistorySize)
        saveToFile()
    }

    /**
     * 记录选择的依赖（自动按 groupId:artifactId 去重，移到最前）
     */
    operator fun plusAssign(artifact: ArtifactHistoryEntry) {
        if (!enableHistory) return
        selectedArtifacts.removeIf { it.key == artifact.key }
        selectedArtifacts.add(0, artifact.copy(timestamp = System.currentTimeMillis()))
        trimToSize(selectedArtifacts, maxArtifactHistorySize)
        saveToFile()
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

    fun clearKeywords() {
        searchKeywords.clear()
        saveToFile()
    }

    fun clearArtifacts() {
        selectedArtifacts.clear()
        saveToFile()
    }

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
 * 历史数据容器
 */
data class HistoryData(
    var searchKeywords: MutableList<String> = mutableListOf(),
    var selectedArtifacts: MutableList<ArtifactHistoryEntry> = mutableListOf(),
    var maxKeywordHistorySize: Int = 50,
    var maxArtifactHistorySize: Int = 100,
    var enableHistory: Boolean = true
)

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

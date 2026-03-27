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
 * 全局存储：~/.config/maven-buddy/history.json
 */
@Service(Service.Level.APP)
class SearchHistoryService {

    private val logger = Logger.getInstance(SearchHistoryService::class.java)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private var data: HistoryData = HistoryData()

    init {
        loadFromFile()
    }

    val searchKeywords: MutableList<String>
        get() = data.searchKeywords

    val selectedArtifacts: MutableList<ArtifactHistoryEntry>
        get() = data.selectedArtifacts

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

    private fun getStoragePath(): String = "${MavenSearchSettings.configDir}/history.json"

    fun loadFromFile() {
        val file = File(getStoragePath())
        if (!file.exists()) {
            data = HistoryData()
            return
        }

        runCatching {
            data = gson.fromJson(file.readText(), HistoryData::class.java) ?: HistoryData()
        }.onFailure { e ->
            logger.warn("Failed to load history", e)
            e.printStackTrace()
            data = HistoryData()
        }
    }

    private fun saveToFile() {
        val file = File(getStoragePath())
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(data))
        }.onFailure { e ->
            logger.warn("Failed to save history", e)
            e.printStackTrace()
        }
    }

    operator fun plusAssign(keyword: String) {
        if (!enableHistory || keyword.isBlank() || keyword.length < 2) return
        searchKeywords.remove(keyword)
        searchKeywords.add(0, keyword)
        trimToSize(searchKeywords, maxKeywordHistorySize)
        saveToFile()
    }

    operator fun plusAssign(artifact: ArtifactHistoryEntry) {
        if (!enableHistory) return
        selectedArtifacts.removeIf { it.key == artifact.key }
        selectedArtifacts.add(0, artifact.copy(timestamp = System.currentTimeMillis()))
        trimToSize(selectedArtifacts, maxArtifactHistorySize)
        saveToFile()
    }

    fun record(groupId: String, artifactId: String, version: String) {
        this += ArtifactHistoryEntry(groupId, artifactId, version)
    }

    fun record(keyword: String) {
        this += keyword
    }

    fun recentArtifacts(limit: Int = 15): List<ArtifactHistoryEntry> =
        selectedArtifacts.take(limit)

    @Suppress("unused")
    fun recentKeywords(limit: Int = 10): List<String> =
        searchKeywords.take(limit)

    @Suppress("unused")
    fun matchKeywords(query: String, limit: Int = 10): List<String> {
        if (!enableHistory || query.isBlank()) return emptyList()
        val lowerQuery = query.lowercase()
        return searchKeywords.filter { it.lowercase().contains(lowerQuery) }.take(limit)
    }

    fun matchArtifacts(query: String, limit: Int = 10): List<ArtifactHistoryEntry> {
        if (!enableHistory || query.isBlank()) return emptyList()
        val lowerQuery = query.lowercase()
        return selectedArtifacts.filter { it.matches(lowerQuery) }.take(limit)
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

data class HistoryData(
    var searchKeywords: MutableList<String> = mutableListOf(),
    var selectedArtifacts: MutableList<ArtifactHistoryEntry> = mutableListOf(),
    var maxKeywordHistorySize: Int = 50,
    var maxArtifactHistorySize: Int = 100,
    var enableHistory: Boolean = true
)

data class ArtifactHistoryEntry(
    var groupId: String = "",
    var artifactId: String = "",
    var version: String = "",
    var timestamp: Long = 0L
) {
    @Suppress("unused")
    constructor() : this("", "", "", 0L)
    
    val key: String get() = "$groupId:$artifactId"
    val coordinate: String get() = "$groupId:$artifactId:$version"
    
    fun matches(query: String): Boolean =
        groupId.lowercase().contains(query) ||
        artifactId.lowercase().contains(query) ||
        key.lowercase().contains(query)
}

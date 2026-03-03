package site.addzero.cloudfile.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

/**
 * Project-level settings for Cloud File Hosting
 * Each project has its own namespace for hosted files
 */
@Service(Service.Level.PROJECT)
@State(
    name = "ProjectHostingSettings",
    storages = [Storage("cloud-file-hosting-project.xml")]
)
class ProjectHostingSettings : PersistentStateComponent<ProjectHostingSettings.State> {

    private var state = State()

    data class State(
        @Tag("enabled")
        var enabled: Boolean = true,

        @Tag("namespace")
        var namespace: String = "",

        @XCollection(style = XCollection.Style.v2, elementName = "rule")
        var projectRules: MutableList<CloudFileSettings.HostingRule> = mutableListOf(),

        @Tag("inheritGlobal")
        var inheritGlobal: Boolean = true,

        @Tag("autoSyncOnChange")
        var autoSyncOnChange: Boolean = true,

        @Tag("lastSyncTimestamp")
        var lastSyncTimestamp: Long = 0L,

        @Tag("localFilesHash")
        var localFilesHash: String = "",

        @XCollection(style = XCollection.Style.v2, elementName = "syncedFile")
        var syncedFiles: MutableList<SyncedFileInfo> = mutableListOf()
    )

    data class SyncedFileInfo(
        @Tag("relativePath")
        var relativePath: String = "",

        @Tag("remoteEtag")
        var remoteEtag: String = "",

        @Tag("lastModified")
        var lastModified: Long = 0L,

        @Tag("size")
        var size: Long = 0L
    )

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    fun getNamespace(project: Project): String {
        if (state.namespace.isBlank()) {
            state.namespace = project.name
        }
        return state.namespace
    }

    fun setNamespace(namespace: String) {
        state.namespace = namespace
    }

    fun addProjectRule(pattern: String, type: CloudFileSettings.HostingRule.RuleType) {
        state.projectRules.add(CloudFileSettings.HostingRule(pattern, type))
    }

    fun removeProjectRule(pattern: String) {
        state.projectRules.removeIf { it.pattern == pattern }
    }

    fun getEffectiveRules(project: Project): List<CloudFileSettings.HostingRule> {
        val result = mutableListOf<CloudFileSettings.HostingRule>()

        // Add global rules first if inheriting
        if (state.inheritGlobal) {
            val globalSettings = CloudFileSettings.getInstance()
            result.addAll(globalSettings.state.globalRules.filter { it.enabled })

            // Add matching custom rules
            val gitInfo = GitProjectInfo.from(project)
            result.addAll(globalSettings.getMatchingCustomRules(gitInfo))
        }

        // Add project-specific rules
        result.addAll(state.projectRules.filter { it.enabled })

        return result
    }

    fun recordSync(files: List<SyncedFileInfo>) {
        state.syncedFiles.clear()
        state.syncedFiles.addAll(files)
        state.lastSyncTimestamp = System.currentTimeMillis()
    }

    companion object {
        fun getInstance(project: Project): ProjectHostingSettings =
            project.getService(ProjectHostingSettings::class.java)
    }
}

/**
 * Information about a Git project used for custom rule matching
 */
data class GitProjectInfo(
    val authors: List<String>,
    val projectName: String,
    val remoteUrl: String?
) {
    companion object {
        fun from(project: Project): GitProjectInfo {
            // Git info will be populated by GitIntegrationService
            return GitProjectInfo(
                authors = emptyList(),
                projectName = project.name,
                remoteUrl = null
            )
        }
    }
}

/**
 * Extension function to find matching custom rules
 */
fun CloudFileSettings.getMatchingCustomRules(gitInfo: GitProjectInfo): List<CloudFileSettings.HostingRule> {
    val result = mutableListOf<CloudFileSettings.HostingRule>()

    state.customRules
        .filter { it.enabled }
        .sortedByDescending { it.priority }
        .forEach { rule ->
            val authorMatch = rule.gitAuthorPattern.isBlank() ||
                    gitInfo.authors.any { it.contains(rule.gitAuthorPattern, ignoreCase = true) }

            val projectMatch = rule.projectNamePattern.isBlank() ||
                    gitInfo.projectName.contains(rule.projectNamePattern, ignoreCase = true)

            if (authorMatch && projectMatch) {
                result.addAll(rule.rules.filter { it.enabled })
            }
        }

    return result
}

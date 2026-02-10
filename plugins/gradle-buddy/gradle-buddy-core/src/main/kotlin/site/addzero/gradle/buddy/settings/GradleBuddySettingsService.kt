package site.addzero.gradle.buddy.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(
    name = "GradleBuddySettings",
    storages = [Storage("gradleBuddySettings.xml")]
)
class GradleBuddySettingsService : PersistentStateComponent<GradleBuddySettingsService.State> {

    data class State(
        var defaultTasks: MutableList<String> = DEFAULT_TASKS.toMutableList(),
        var versionCatalogPath: String = DEFAULT_VERSION_CATALOG_PATH,
        /** 智能补全时静默 upsert toml：选中后自动写入 toml 并回显 libs.xxx.xxx */
        var silentUpsertToml: Boolean = false
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    // 获取默认任务列表
    fun getDefaultTasks(): List<String> = myState.defaultTasks.toList()

    // 设置默认任务列表
    fun setDefaultTasks(tasks: List<String>) {
        myState.defaultTasks = tasks.toMutableList()
    }

    // 获取版本目录文件路径
    fun getVersionCatalogPath(): String = myState.versionCatalogPath

    // 设置版本目录文件路径
    fun setVersionCatalogPath(path: String) {
        myState.versionCatalogPath = path
    }

    // 添加默认任务
    fun addDefaultTask(task: String) {
        if (task !in myState.defaultTasks) {
            myState.defaultTasks.add(task)
        }
    }

    // 移除默认任务
    fun removeDefaultTask(task: String) {
        myState.defaultTasks.remove(task)
    }

    // 获取是否静默 upsert toml
    fun isSilentUpsertToml(): Boolean = myState.silentUpsertToml

    // 设置是否静默 upsert toml
    fun setSilentUpsertToml(enabled: Boolean) {
        myState.silentUpsertToml = enabled
    }

    // 重置为默认值
    fun resetToDefaults() {
        myState.defaultTasks = DEFAULT_TASKS.toMutableList()
        myState.versionCatalogPath = DEFAULT_VERSION_CATALOG_PATH
    }

    companion object {
        val DEFAULT_TASKS = listOf(
            "clean",
            "compileKotlin",
            "build",
            "test",
            "jar",
            "publishToMavenLocal",
            "publishToMavenCentral",
            "kspKotlin",
            "kspCommonMainMetadata",
            "signPlugin",
            "publishPlugin",
            "runIde"
        )

        const val DEFAULT_VERSION_CATALOG_PATH = "gradle/libs.versions.toml"

        fun getInstance(project: Project): GradleBuddySettingsService = project.service()
    }
}

package site.addzero.gradle.sleep.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(
    name = "ModuleSleepSettings",
    storages = [Storage("moduleSleepSettings.xml")]
)
class ModuleSleepSettingsService : PersistentStateComponent<ModuleSleepSettingsService.State> {

    data class State(
        var moduleIdleTimeoutMinutes: Int = 5,
        var autoSleepEnabled: Boolean? = null, // null = auto-detect based on module count
        var manualFolderNames: String = ""
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    fun getModuleIdleTimeoutMinutes(): Int = myState.moduleIdleTimeoutMinutes

    fun setModuleIdleTimeoutMinutes(minutes: Int) {
        myState.moduleIdleTimeoutMinutes = minutes
    }

    /**
     * 获取自动睡眠设置
     * @return null = 自动检测, true = 开启, false = 关闭
     */
    fun getAutoSleepEnabled(): Boolean? = myState.autoSleepEnabled

    /**
     * 设置自动睡眠
     * @param enabled null = 自动检测, true = 开启, false = 关闭
     */
    fun setAutoSleepEnabled(enabled: Boolean?) {
        myState.autoSleepEnabled = enabled
    }

    fun getManualFolderNames(): Set<String> {
        return myState.manualFolderNames
            .split(',', ';', '\n')
            .map { it.trim().trim(':', '/', '\\') }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun getManualFolderNamesRaw(): String = myState.manualFolderNames

    fun setManualFolderNames(raw: String) {
        myState.manualFolderNames = raw
    }

    companion object {
        // 大型项目阈值：超过此数量模块自动开启睡眠
        const val LARGE_PROJECT_THRESHOLD = 30

        fun getInstance(project: Project): ModuleSleepSettingsService = project.service()
    }
}

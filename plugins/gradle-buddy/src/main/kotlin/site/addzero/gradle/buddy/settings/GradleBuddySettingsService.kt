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
        var moduleIdleTimeoutMinutes: Int = 5,
        var autoSyncOnFileOpen: Boolean = false
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    fun getDefaultTasks(): List<String> = myState.defaultTasks.toList()

    fun setDefaultTasks(tasks: List<String>) {
        myState.defaultTasks = tasks.toMutableList()
    }

    fun addDefaultTask(task: String) {
        if (task !in myState.defaultTasks) {
            myState.defaultTasks.add(task)
        }
    }

    fun removeDefaultTask(task: String) {
        myState.defaultTasks.remove(task)
    }

    fun resetToDefaults() {
        myState.defaultTasks = DEFAULT_TASKS.toMutableList()
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

        fun getInstance(project: Project): GradleBuddySettingsService = project.service()
    }
}

package site.addzero.projecttabs.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent settings for Project Tabs plugin
 */
@State(
    name = "ProjectTabsSettings",
    storages = [Storage("projectTabs.xml")]
)
class ProjectTabsSettings : PersistentStateComponent<ProjectTabsSettings.State> {

    data class State(
        var enabled: Boolean = true,
        var showCloseButton: Boolean = true,
        var maxTabWidth: Int = 200,
        var showProjectPath: Boolean = false
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    companion object {
        @JvmStatic
        fun getInstance(): ProjectTabsSettings {
            return ApplicationManager.getApplication().getService(ProjectTabsSettings::class.java)
        }
    }

    var enabled: Boolean
        get() = myState.enabled
        set(value) { myState.enabled = value }

    var showCloseButton: Boolean
        get() = myState.showCloseButton
        set(value) { myState.showCloseButton = value }

    var maxTabWidth: Int
        get() = myState.maxTabWidth
        set(value) { myState.maxTabWidth = value.coerceIn(100, 400) }

    var showProjectPath: Boolean
        get() = myState.showProjectPath
        set(value) { myState.showProjectPath = value }
}

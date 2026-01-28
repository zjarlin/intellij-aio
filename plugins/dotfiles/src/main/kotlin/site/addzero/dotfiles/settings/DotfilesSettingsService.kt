package site.addzero.dotfiles.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.RoamingType
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "DotfilesSettings",
    storages = [Storage(value = "dotfiles.xml", roamingType = RoamingType.DEFAULT)]
)
class DotfilesSettingsService : PersistentStateComponent<DotfilesSettingsService.State> {

    data class State(
        var dotfilesDirName: String = ".dotfiles",
        var specFileName: String = "dotfiles.toml",
        var templatesDirName: String = "templates",
    )

    private val state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        fun getInstance(): DotfilesSettingsService =
            ApplicationManager.getApplication().getService(DotfilesSettingsService::class.java)
    }
}

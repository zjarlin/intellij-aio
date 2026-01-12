package site.addzero.autoupdate

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level auto-update settings service
 */
@Service(Service.Level.APP)
@State(
    name = "AutoUpdateSettings",
    storages = [Storage("vscAutoUpdate.xml")]
)
class AutoUpdateSettings : PersistentStateComponent<AutoUpdateState> {

    private var state = AutoUpdateState()

    override fun getState(): AutoUpdateState = state

    override fun loadState(state: AutoUpdateState) {
        this.state = state
    }

    var autoPullBeforePush: Boolean
        get() = state.autoPullBeforePush
        set(value) { state.autoPullBeforePush = value }

    var showNotification: Boolean
        get() = state.showNotification
        set(value) { state.showNotification = value }

    var pullRebase: Boolean
        get() = state.pullRebase
        set(value) { state.pullRebase = value }

    companion object {
        fun getInstance(): AutoUpdateSettings =
            com.intellij.openapi.application.ApplicationManager.getApplication().getService(AutoUpdateSettings::class.java)
    }
}

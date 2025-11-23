package site.addzero.shitcode.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "ShitCodeSettings",
    storages = [Storage("ShitCodeSettings.xml")]
)
class ShitCodeSettingsService : PersistentStateComponent<ShitCodeSettings> {

    private var settings: ShitCodeSettings = ShitCodeSettings()

    override fun getState(): ShitCodeSettings {
        return settings
    }

    override fun loadState(state: ShitCodeSettings) {
        settings = state
    }

    companion object {
        fun getInstance(): ShitCodeSettingsService {
            return ApplicationManager.getApplication().getService(ShitCodeSettingsService::class.java)
        }
    }
}

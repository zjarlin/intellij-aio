package site.addzero.aiannotator.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "AiAnnotatorSettings",
    storages = [Storage("AiAnnotatorSettings.xml")]
)
class AiAnnotatorSettingsService : PersistentStateComponent<AiAnnotatorSettings> {

    private var settings: AiAnnotatorSettings = AiAnnotatorSettings()

    override fun getState(): AiAnnotatorSettings {
        return settings
    }

    override fun loadState(state: AiAnnotatorSettings) {
        settings = state
    }

    companion object {
        fun getInstance(): AiAnnotatorSettingsService {
            return ApplicationManager.getApplication().getService(AiAnnotatorSettingsService::class.java)
        }
    }
}

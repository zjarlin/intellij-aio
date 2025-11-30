package site.addzero.lsi.analyzer.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "PojoMetaSettings",
    storages = [Storage("PojoMetaSettings.xml")]
)
class PojoMetaSettingsService : PersistentStateComponent<PojoMetaSettings> {

    private var settings: PojoMetaSettings = PojoMetaSettings()

    override fun getState(): PojoMetaSettings = settings

    override fun loadState(state: PojoMetaSettings) {
        settings = state
    }

    companion object {
        fun getInstance(): PojoMetaSettingsService =
            ApplicationManager.getApplication().getService(PojoMetaSettingsService::class.java)
    }
}

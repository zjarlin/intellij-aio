package site.addzero.composeblocks.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(name = "ComposeBlocksSettings", storages = [Storage("composeBlocks.xml")])
class ComposeBlocksSettingsService : PersistentStateComponent<ComposeBlocksSettingsService.State> {
    data class State(
        var enableComposeBlocksEditorByDefault: Boolean = true,
        var composeBlocksEditorSettingTouched: Boolean = false,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        if (!this.state.composeBlocksEditorSettingTouched) {
            this.state.enableComposeBlocksEditorByDefault = true
        }
    }

    companion object {
        fun getInstance(): ComposeBlocksSettingsService = ApplicationManager.getApplication().service()
    }
}

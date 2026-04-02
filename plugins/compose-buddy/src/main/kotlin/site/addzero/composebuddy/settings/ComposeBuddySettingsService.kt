package site.addzero.composebuddy.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(name = "ComposeBuddySettings", storages = [Storage("composeBuddy.xml")])
class ComposeBuddySettingsService : PersistentStateComponent<ComposeBuddySettingsService.State> {
    data class State(
        var parameterThreshold: Int = 8,
        var callbackThreshold: Int = 4,
        var statePairThreshold: Int = 3,
        var preferPropsWrapper: Boolean = true,
        var keepCompatibilityByDefault: Boolean = false,
        var addTemplateParameters: Boolean = true,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): ComposeBuddySettingsService = ApplicationManager.getApplication().service()
    }
}

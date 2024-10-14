package com.addzero.addl.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * MyPluginSettings 类负责持久化设置。
 */
@State(
    name = "com.addzero.addl.settings.MyPluginSettings",
    storages = [Storage("com.addzero.addl.settings.MyPluginSettings.xml")]
)
@Service
class MyPluginSettings : PersistentStateComponent<Settings> {

    private var myState: Settings = Settings()

    override fun getState(): Settings {
        return myState
    }

    override fun loadState(state: Settings) {
        this.myState = state
    }

    companion object {
        val instance: MyPluginSettings
            get() = ApplicationManager.getApplication().getService(MyPluginSettings::class.java)
    }
}
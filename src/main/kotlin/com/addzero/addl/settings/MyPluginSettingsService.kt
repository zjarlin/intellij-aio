package com.addzero.addl.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "com.myplugin.MyPluginSettings",
    storages = [Storage("MyPluginSettings.xml")]
)
class MyPluginSettingsService : PersistentStateComponent<MyPluginSettings> {

    private var settings: MyPluginSettings = MyPluginSettings()

    override fun getState(): MyPluginSettings {
        return settings
    }

    override fun loadState(state: MyPluginSettings) {
        settings = state
    }

    companion object {
        fun getInstance(): MyPluginSettingsService {
            return com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(MyPluginSettingsService::class.java)
        }
    }
}
package com.addzero.addl.settings

import MyPluginSettings
import com.intellij.openapi.options.Configurable
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.gradleTooling.get
import javax.swing.JComponent
import javax.swing.JTextField

class MyPluginSettingsConfigurable : Configurable {
    private var settingsComponent: MyPluginSettingsComponent? = null

    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String {
        return "AutoDDL设置"
    }

    override fun createComponent(): JComponent? {
        settingsComponent = MyPluginSettingsComponent()
        return settingsComponent!!.panel
    }

    override fun isModified(): Boolean {
        val settings = MyPluginSettings.instance.state
        return settingsComponent?.getSettings() != settings // 比较当前设置与保存的设置
    }

    override fun apply() {
        val settings = MyPluginSettings.instance
        settings.loadState(settingsComponent!!.getSettings())
    }

    override fun reset() {
        // 在设置组件中加载当前设置
        settingsComponent?.panel?.components
            ?.filterIsInstance<JTextField>()
            ?.forEach { it.text = MyPluginSettings.instance.state[it.name]?.toString() ?: "" }
    }

    override fun disposeUIResources() {
        settingsComponent = null
    }
}
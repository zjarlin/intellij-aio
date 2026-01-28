package site.addzero.dotfiles.settings

import com.intellij.openapi.options.Configurable
import com.intellij.util.ui.FormBuilder
import com.intellij.ui.components.JBTextField
import javax.swing.JComponent

class DotfilesConfigurable : Configurable {
    private val settings = DotfilesSettingsService.getInstance()
    private val dotfilesDirField = JBTextField()
    private val specFileField = JBTextField()
    private val templatesDirField = JBTextField()

    private var panel: JComponent? = null

    override fun getDisplayName(): String = "Dotfiles"

    override fun createComponent(): JComponent {
        reset()
        val built = FormBuilder.createFormBuilder()
            .addLabeledComponent("Dotfiles directory", dotfilesDirField)
            .addLabeledComponent("Spec file name", specFileField)
            .addLabeledComponent("Templates directory", templatesDirField)
            .panel
        panel = built
        return built
    }

    override fun isModified(): Boolean {
        val state = settings.state
        return dotfilesDirField.text != state.dotfilesDirName ||
            specFileField.text != state.specFileName ||
            templatesDirField.text != state.templatesDirName
    }

    override fun apply() {
        val state = settings.state
        state.dotfilesDirName = dotfilesDirField.text.trim()
        state.specFileName = specFileField.text.trim()
        state.templatesDirName = templatesDirField.text.trim()
    }

    override fun reset() {
        val state = settings.state
        dotfilesDirField.text = state.dotfilesDirName
        specFileField.text = state.specFileName
        templatesDirField.text = state.templatesDirName
    }

    override fun disposeUIResources() {
        panel = null
    }
}

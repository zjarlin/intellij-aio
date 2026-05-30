package site.addzero.split.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import site.addzero.split.services.PathCalculator
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 模块名称输入对话框
 */
class ModuleNameDialog(
    project: Project,
    private val defaultName: String
) : DialogWrapper(project) {

    private val nameField = JBTextField(defaultName, 30)

    init {
        title = "Input New Module Name"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Module Name:"), nameField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun doValidate(): ValidationInfo? {
        val name = nameField.text.trim()

        if (name.isBlank()) {
            return ValidationInfo("Module name cannot be empty", nameField)
        }

        if (!PathCalculator.isValidModuleName(name)) {
            return ValidationInfo("Module name can only contain letters, numbers, hyphens, and underscores", nameField)
        }

        return null
    }

    override fun getPreferredFocusedComponent(): JComponent = nameField

    fun getModuleName(): String = nameField.text.trim()
}

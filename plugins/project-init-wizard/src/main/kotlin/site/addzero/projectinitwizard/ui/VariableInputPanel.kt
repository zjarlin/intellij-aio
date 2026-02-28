package site.addzero.projectinitwizard.ui

import com.intellij.ui.components.JBTextField
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import site.addzero.projectinitwizard.model.TemplateVariable
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JCheckBox
import javax.swing.JComponent

class VariableInputPanel(variables: List<TemplateVariable>) : JBPanel<VariableInputPanel>(GridBagLayout()) {

    private val fieldMap = mutableMapOf<String, JComponent>()
    private val variables: List<TemplateVariable>

    init {
        this.variables = variables.filter { it.name != "projectName" }
        
        var row = 0
        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = Insets(4, 8, 4, 8)
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        }

        if (this.variables.isEmpty()) {
            add(JBLabel("No variables required for this template"), gbc)
        } else {
            this.variables.forEach { variable ->
                gbc.gridy = row
                gbc.gridwidth = 1
                gbc.weightx = 0.0
                
                val label = JBLabel("${variable.name}:")
                add(label, gbc)

                gbc.gridx = 1
                gbc.weightx = 1.0

                val field = createField(variable)
                fieldMap[variable.name] = field
                add(field, gbc)

                row++
            }
        }
    }

    private fun createField(variable: TemplateVariable): JComponent {
        return when (variable.type) {
            "boolean" -> {
                val checkBox = JCheckBox()
                if (variable.defaultValue.isNotEmpty()) {
                    checkBox.isSelected = variable.defaultValue.toBoolean()
                }
                checkBox
            }
            "choice" -> {
                val choices = variable.defaultValue.split(",").map { it.trim() }.toTypedArray()
                val comboBox = ComboBox(choices)
                comboBox
            }
            "textarea" -> {
                val textArea = JBTextArea(variable.defaultValue, 3, 30)
                textArea
            }
            else -> {
                val textField = JBTextField(variable.defaultValue, 20)
                textField
            }
        }
    }

    fun getVariableValues(): Map<String, Any> {
        val values = mutableMapOf<String, Any>()

        variables.forEach { variable ->
            val field = fieldMap[variable.name]
            val value = when (field) {
                is JBTextField -> field.text
                is JBTextArea -> field.text
                is JCheckBox -> field.isSelected
                is ComboBox<*> -> field.selectedItem?.toString() ?: ""
                else -> ""
            }

            if (value.toString().isNotEmpty() || variable.required) {
                values[variable.name] = value
            }
        }

        return values
    }
}

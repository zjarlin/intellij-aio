package site.addzero.projectinitwizard.wizard

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import site.addzero.projectinitwizard.model.Template
import site.addzero.projectinitwizard.service.ProjectGeneratorService
import site.addzero.projectinitwizard.ui.VariableInputPanel
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import com.intellij.openapi.util.Key

class ProjectInitWizardStep(
    private val templates: List<Template>,
    private val generatorService: ProjectGeneratorService,
    private val wizardContext: WizardContext
) : ModuleWizardStep() {

    private lateinit var mainPanel: JPanel
    private lateinit var templateCombo: ComboBox<String>
    private lateinit var descriptionLabel: JBLabel
    private lateinit var projectNameField: JBTextField
    private lateinit var moduleNameField: JBTextField
    private lateinit var moduleDirField: TextFieldWithBrowseButton
    private lateinit var variablePanel: VariableInputPanel

    private var selectedTemplate: Template? = templates.firstOrNull()

    companion object {
        val TEMPLATES_KEY: Key<Template> = Key.create("PROJECT_INIT_WIZARD_TEMPLATE")
        val PROJECT_NAME_KEY: Key<String> = Key.create("PROJECT_INIT_WIZARD_PROJECT_NAME")
    }

    override fun getComponent(): JComponent {
        mainPanel = JPanel(GridBagLayout())

        val gbc = GridBagConstraints().apply {
            insets = Insets(8, 12, 8, 12)
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        }

        // Template selection
        gbc.gridy = 0
        gbc.gridx = 0
        gbc.weightx = 0.0
        mainPanel.add(JBLabel("Template:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0

        // Show templates or error message
        if (templates.isEmpty()) {
            // No templates - show message
            mainPanel.add(JBLabel("No templates available. Create templates in ~/.project-init-wizard/templates/"), gbc)
        } else {
            templateCombo = ComboBox(templates.map {
                val prefix = if (it.isBuiltIn) "[Built-in] " else ""
                "$prefix${it.name}"
            }.toTypedArray())

            if (templates.isNotEmpty()) {
                templateCombo.selectedIndex = 0
            }

            templateCombo.addActionListener {
                val idx = templateCombo.selectedIndex
                if (idx >= 0 && idx < templates.size) {
                    selectedTemplate = templates[idx]
                    updateUI()
                }
            }

            mainPanel.add(templateCombo, gbc)

            // Description
            gbc.gridy = 1
            gbc.gridx = 0
            gbc.weightx = 0.0
            mainPanel.add(JBLabel("Description:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            descriptionLabel = JBLabel(selectedTemplate?.description ?: "")
            mainPanel.add(descriptionLabel, gbc)

            // Project name
            gbc.gridy = 2
            gbc.gridx = 0
            gbc.weightx = 0.0
            mainPanel.add(JBLabel("Project Name:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            projectNameField = JBTextField(selectedTemplate?.name ?: "my-project", 25)
            mainPanel.add(projectNameField, gbc)

            // Module name
            gbc.gridy = 3
            gbc.gridx = 0
            gbc.weightx = 0.0
            mainPanel.add(JBLabel("Module Name:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            moduleNameField = JBTextField(selectedTemplate?.name ?: "my-project", 25)
            mainPanel.add(moduleNameField, gbc)

            // Module directory
            gbc.gridy = 4
            gbc.gridx = 0
            gbc.weightx = 0.0
            mainPanel.add(JBLabel("Module Location:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            moduleDirField = TextFieldWithBrowseButton()
            moduleDirField.textField.text = System.getProperty("user.home")
            mainPanel.add(moduleDirField, gbc)

            // Variables
            gbc.gridy = 5
            gbc.gridx = 0
            gbc.weightx = 0.0
            gbc.anchor = GridBagConstraints.NORTHWEST
            mainPanel.add(JBLabel("Variables:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            gbc.fill = GridBagConstraints.BOTH
            gbc.weighty = 1.0

            variablePanel = VariableInputPanel(selectedTemplate?.variables ?: emptyList())
            mainPanel.add(variablePanel, gbc)
        }

        return mainPanel
    }

    private fun updateUI() {
        val template = selectedTemplate ?: return
        descriptionLabel.text = template.description
        projectNameField.text = template.name
        moduleNameField.text = template.name
    }

    override fun updateDataModel() {
        val template = selectedTemplate ?: return
        val projectName = projectNameField.text

        val vars = variablePanel.getVariableValues().toMutableMap()
        vars["projectName"] = projectName
        vars["moduleName"] = moduleNameField.text

        wizardContext.putUserData(TEMPLATES_KEY, template)
        wizardContext.putUserData(PROJECT_NAME_KEY, projectName)
    }
}

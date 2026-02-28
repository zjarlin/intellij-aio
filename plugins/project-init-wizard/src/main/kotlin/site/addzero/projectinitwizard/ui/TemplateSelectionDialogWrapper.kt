package site.addzero.projectinitwizard.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import site.addzero.projectinitwizard.model.Template
import site.addzero.projectinitwizard.service.ProjectGeneratorService
import site.addzero.projectinitwizard.service.TemplateService
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel

class TemplateSelectionDialogWrapper(
    private val templates: List<Template>,
    private val templateService: TemplateService,
    private val generatorService: ProjectGeneratorService
) : DialogWrapper(true) {

    private lateinit var templateCombo: ComboBox<String>
    private lateinit var descriptionLabel: JBLabel
    private lateinit var projectNameField: JBTextField
    private lateinit var targetDirField: TextFieldWithBrowseButton
    private lateinit var variablePanel: VariableInputPanel

    private var selectedTemplate: Template? = null

    init {
        title = "Project Init Wizard"
        setSize(600, 450)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(8, 12, 8, 12)
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        }

        // Template selection
        gbc.gridy = 0
        gbc.gridx = 0
        gbc.weightx = 0.0
        panel.add(JBLabel("Template:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        
        templateCombo = ComboBox(templates.map { 
            val prefix = if (it.isBuiltIn) "[Built-in] " else ""
            "$prefix${it.name}"
        }.toTypedArray())
        
        templateCombo.addActionListener {
            updateVariablePanel()
        }
        panel.add(templateCombo, gbc)

        // Description
        gbc.gridy = 1
        gbc.gridx = 0
        gbc.weightx = 0.0
        panel.add(JBLabel("Description:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        descriptionLabel = JBLabel("")
        panel.add(descriptionLabel, gbc)

        // Project name
        gbc.gridy = 2
        gbc.gridx = 0
        gbc.weightx = 0.0
        panel.add(JBLabel("Project Name:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        projectNameField = JBTextField("my-project", 20)
        panel.add(projectNameField, gbc)

        // Target directory
        gbc.gridy = 3
        gbc.gridx = 0
        gbc.weightx = 0.0
        panel.add(JBLabel("Target Directory:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        targetDirField = TextFieldWithBrowseButton()
        targetDirField.textField.text = System.getProperty("user.home")
        panel.add(targetDirField, gbc)

        // Variables
        gbc.gridy = 4
        gbc.gridx = 0
        gbc.weightx = 0.0
        gbc.anchor = GridBagConstraints.NORTHWEST
        panel.add(JBLabel("Variables:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.BOTH
        gbc.weighty = 1.0
        variablePanel = VariableInputPanel(emptyList())
        panel.add(variablePanel, gbc)

        // Initialize
        if (templates.isNotEmpty()) {
            templateCombo.selectedIndex = 0
            selectedTemplate = templates[0]
            descriptionLabel.text = templates[0].description
            projectNameField.text = templates[0].name
        }

        return panel
    }

    private fun updateVariablePanel() {
        val selectedIndex = templateCombo.selectedIndex
        if (selectedIndex >= 0 && selectedIndex < templates.size) {
            selectedTemplate = templates[selectedIndex]
            val template = templates[selectedIndex]
            
            descriptionLabel.text = template.description
            projectNameField.text = template.name
        }
    }

    override fun doOKAction() {
        val template = selectedTemplate ?: run {
            close(OK_EXIT_CODE)
            return
        }

        var projectName = projectNameField.text.trim()
        if (projectName.isEmpty()) {
            projectName = template.name
        }

        val targetDir = File(targetDirField.text)
        if (!targetDir.exists() || !targetDir.isDirectory) {
            targetDirField.text = System.getProperty("user.home")
        }

        // Collect variables
        val variables = mutableMapOf<String, Any>(
            "projectName" to projectName
        )
        variables.putAll(variablePanel.getVariableValues())

        try {
            generatorService.generateProject(
                template = template,
                variables = variables,
                targetDir = targetDir,
                projectName = projectName
            )
            close(OK_EXIT_CODE)
        } catch (e: Exception) {
            Messages.showErrorDialog(
                "Failed to create project: ${e.message}",
                "Error"
            )
        }
    }

    override fun getPreferredSize(): java.awt.Dimension {
        return java.awt.Dimension(600, 500)
    }
}

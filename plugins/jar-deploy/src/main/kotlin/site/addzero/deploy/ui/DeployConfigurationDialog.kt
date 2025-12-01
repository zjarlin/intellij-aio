package site.addzero.deploy.ui

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import site.addzero.deploy.BuildArtifact
import site.addzero.deploy.DeployConfiguration
import site.addzero.deploy.DeployTarget
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.ListSelectionModel

/**
 * 部署配置对话框 - 支持多选构建物
 */
class DeployConfigurationDialog(
    private val project: Project,
    private val existingConfig: DeployConfiguration?,
    private val availableTargets: List<DeployTarget>
) : DialogWrapper(project) {

    private val nameField = JBTextField()
    private var selectedTarget: String = availableTargets.firstOrNull()?.name ?: ""
    
    private val artifactsModel = DefaultListModel<String>()
    private val artifactsList = JBList(artifactsModel).apply {
        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    }
    
    private val artifacts = mutableListOf<BuildArtifact>()

    init {
        title = if (existingConfig == null) "Add Deploy Configuration" else "Edit Deploy Configuration"
        
        existingConfig?.let { config ->
            nameField.text = config.name ?: ""
            selectedTarget = config.targetName ?: availableTargets.firstOrNull()?.name ?: ""
            config.artifacts.forEach { artifact ->
                artifacts.add(artifact)
                artifactsModel.addElement(artifact.getDisplayName())
            }
        }
        
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Name:") {
            cell(nameField)
                .columns(COLUMNS_MEDIUM)
                .comment("Unique name for this configuration")
        }
        
        row("Target:") {
            comboBox(availableTargets.map { it.name ?: "" })
                .bindItem(
                    getter = { selectedTarget },
                    setter = { selectedTarget = it ?: "" }
                )
                .comment("SSH target to deploy to")
        }
        
        group("Build Artifacts") {
            row {
                val decorator = ToolbarDecorator.createDecorator(artifactsList)
                    .setAddAction { addArtifact() }
                    .setRemoveAction { removeArtifact() }
                    .createPanel()
                
                cell(decorator)
                    .align(Align.FILL)
                    .resizableColumn()
            }.resizableRow()
            
            row {
                comment("Select files or folders to deploy (multi-select supported)")
            }
        }
    }

    private fun addArtifact() {
        val descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
            .withTitle("Select Build Artifacts")
            .withDescription("Select files or folders to deploy")
            .withRoots(project.baseDir)
        
        val files = FileChooser.chooseFiles(descriptor, project, null)
        files.forEach { file ->
            val path = file.path
            if (artifacts.none { it.path == path }) {
                val artifact = BuildArtifact().apply {
                    this.path = path
                    this.isDirectory = file.isDirectory
                    this.enabled = true
                }
                artifacts.add(artifact)
                artifactsModel.addElement(artifact.getDisplayName())
            }
        }
    }

    private fun removeArtifact() {
        val selectedIndices = artifactsList.selectedIndices.sortedDescending()
        selectedIndices.forEach { index ->
            artifacts.removeAt(index)
            artifactsModel.remove(index)
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (nameField.text.isBlank()) {
            return ValidationInfo("Name is required", nameField)
        }
        if (selectedTarget.isBlank()) {
            return ValidationInfo("Please select a target")
        }
        if (artifacts.isEmpty()) {
            return ValidationInfo("Please add at least one artifact")
        }
        return null
    }

    fun getConfiguration(): DeployConfiguration = DeployConfiguration().apply {
        name = nameField.text
        targetName = selectedTarget
        artifacts.forEach { this.artifacts.add(it) }
        enabled = true
    }
}

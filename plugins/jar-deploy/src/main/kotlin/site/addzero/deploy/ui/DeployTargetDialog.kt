package site.addzero.deploy.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import site.addzero.deploy.DeployTarget
import site.addzero.deploy.SshDeployService
import javax.swing.JComponent

/**
 * 部署目标配置对话框
 */
class DeployTargetDialog(
    private val project: Project,
    private val existingTarget: DeployTarget?
) : DialogWrapper(project) {

    private val nameField = JBTextField()
    private val remoteDirField = JBTextField()
    private val preDeployField = JBTextField()
    private val postDeployField = JBTextField()
    
    private var selectedSshConfig: String = ""
    private val sshConfigs: List<String>

    init {
        title = if (existingTarget == null) "Add Deploy Target" else "Edit Deploy Target"
        sshConfigs = SshDeployService.getInstance(project).getAvailableSshConfigs()
        
        existingTarget?.let { target ->
            nameField.text = target.name ?: ""
            selectedSshConfig = target.sshConfigName ?: ""
            remoteDirField.text = target.remoteDir ?: "/usr/local/app"
            preDeployField.text = target.preDeployCommand ?: ""
            postDeployField.text = target.postDeployCommand ?: ""
        }
        
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Name:") {
            cell(nameField)
                .columns(COLUMNS_MEDIUM)
                .comment("Unique name for this deploy target")
        }
        
        row("SSH Config:") {
            if (sshConfigs.isEmpty()) {
                label("No SSH configs found. Please configure SSH in IDE settings.")
            } else {
                comboBox(sshConfigs)
                    .bindItem(
                        getter = { selectedSshConfig.ifEmpty { sshConfigs.firstOrNull() } },
                        setter = { selectedSshConfig = it ?: "" }
                    )
                    .comment("Select from IDE SSH configurations")
            }
        }
        
        row("Remote Directory:") {
            cell(remoteDirField)
                .columns(COLUMNS_MEDIUM)
                .comment("e.g., /usr/local/iot/server/logs")
        }
        
        collapsibleGroup("Advanced") {
            row("Pre-Deploy Command:") {
                cell(preDeployField)
                    .columns(COLUMNS_MEDIUM)
                    .comment("Command to run before upload (e.g., stop service)")
            }
            
            row("Post-Deploy Command:") {
                cell(postDeployField)
                    .columns(COLUMNS_MEDIUM)
                    .comment("Command to run after upload (e.g., restart service)")
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (nameField.text.isBlank()) {
            return ValidationInfo("Name is required", nameField)
        }
        if (sshConfigs.isEmpty()) {
            return ValidationInfo("No SSH configuration available. Please configure SSH in IDE settings first.")
        }
        if (remoteDirField.text.isBlank()) {
            return ValidationInfo("Remote directory is required", remoteDirField)
        }
        return null
    }

    fun getTarget(): DeployTarget = DeployTarget().apply {
        name = nameField.text
        sshConfigName = selectedSshConfig.ifEmpty { sshConfigs.firstOrNull() ?: "" }
        remoteDir = remoteDirField.text
        preDeployCommand = preDeployField.text
        postDeployCommand = postDeployField.text
        enabled = true
    }
}

package site.addzero.deploy.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.deploy.*
import site.addzero.deploy.pipeline.DeployExecutor
import site.addzero.deploy.ui.DeployConfigurationDialog

/**
 * 右键菜单部署文件/文件夹的 Action
 */
class DeployJarAction : AnAction(
    "Deploy to Server",
    "Deploy files/folders to remote server via SSH",
    null
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        
        if (files.isEmpty()) return
        
        val settings = JarDeploySettings.getInstance(project)
        val targets = settings.getTargets()
        
        if (targets.isEmpty()) {
            val createNew = Messages.showYesNoDialog(
                project,
                "No deploy targets configured. Would you like to create one?",
                "No Deploy Targets",
                "Create",
                "Cancel",
                Messages.getQuestionIcon()
            )
            
            if (createNew == Messages.YES) {
                showQuickDeployDialog(project, files, targets)
            }
            return
        }
        
        // 如果只选中一个文件，检查是否有现有配置
        if (files.size == 1) {
            val existingConfig = settings.getConfigurations()
                .find { config -> config.artifacts.any { it.path == files[0].path } }
            
            if (existingConfig != null) {
                val target = settings.getTargetByName(existingConfig.targetName ?: "")
                if (target != null) {
                    DeployExecutor.deploy(project, existingConfig, target)
                    return
                }
            }
        }
        
        // 显示快速部署对话框
        showQuickDeployDialog(project, files, targets)
    }

    private fun showQuickDeployDialog(
        project: Project,
        files: Array<VirtualFile>,
        targets: List<DeployTarget>
    ) {
        val settings = JarDeploySettings.getInstance(project)
        
        // 如果没有目标，先创建
        if (targets.isEmpty()) {
            val dialog = site.addzero.deploy.ui.DeployTargetDialog(project, null)
            if (!dialog.showAndGet()) return
            settings.addTarget(dialog.getTarget())
        }
        
        val updatedTargets = settings.getTargets()
        if (updatedTargets.isEmpty()) return
        
        // 创建临时配置
        val tempConfig = DeployConfiguration().apply {
            name = "Quick Deploy"
            targetName = updatedTargets.first().name
            files.forEach { file ->
                artifacts.add(BuildArtifact().apply {
                    path = file.path
                    isDirectory = file.isDirectory
                    enabled = true
                })
            }
            enabled = true
        }
        
        val configDialog = DeployConfigurationDialog(project, tempConfig, updatedTargets)
        if (configDialog.showAndGet()) {
            val config = configDialog.getConfiguration()
            val target = settings.getTargetByName(config.targetName ?: "") ?: return
            
            // 询问是否保存配置
            val save = Messages.showYesNoDialog(
                project,
                "Deploy now. Save this configuration for future use?",
                "Save Configuration",
                "Save & Deploy",
                "Deploy Only",
                Messages.getQuestionIcon()
            )
            
            if (save == Messages.YES) {
                settings.addConfiguration(config)
            }
            
            DeployExecutor.deploy(project, config, target)
        }
    }

    override fun update(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabledAndVisible = e.project != null && !files.isNullOrEmpty()
    }
}

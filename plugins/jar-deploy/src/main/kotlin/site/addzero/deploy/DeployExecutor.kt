package site.addzero.deploy

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.io.File

/**
 * 部署执行器
 */
object DeployExecutor {

    private val log = Logger.getInstance(DeployExecutor::class.java)

    fun deploy(project: Project, config: DeployConfiguration, target: DeployTarget) {
        val enabledArtifacts = config.artifacts.filter { it.enabled }
        if (enabledArtifacts.isEmpty()) {
            showNotification(project, "No Artifacts", "No enabled artifacts in configuration", NotificationType.WARNING)
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Deploying ${config.name}",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = "Connecting to ${target.sshConfigName}..."
                indicator.fraction = 0.1
                
                val service = SshDeployService.getInstance(project)
                val results = mutableListOf<Pair<String, DeployResult>>()
                
                val totalArtifacts = enabledArtifacts.size
                enabledArtifacts.forEachIndexed { index, artifact ->
                    val file = File(artifact.path ?: "")
                    if (!file.exists()) {
                        results.add(artifact.path!! to DeployResult.failure("File not found: ${artifact.path}"))
                        return@forEachIndexed
                    }
                    
                    indicator.text = "Deploying ${file.name} (${index + 1}/$totalArtifacts)..."
                    
                    val result = if (artifact.isDirectory) {
                        service.deployDirectory(file, target, indicator)
                    } else {
                        service.deployFile(file, target, indicator)
                    }
                    
                    results.add(file.name to result)
                    indicator.fraction = 0.1 + 0.9 * (index + 1) / totalArtifacts
                }
                
                val successCount = results.count { it.second.isSuccess() }
                val failCount = results.size - successCount
                
                val message = buildString {
                    appendLine("Deployed to ${target.sshConfigName}:${target.remoteDir}")
                    appendLine("Success: $successCount, Failed: $failCount")
                    if (failCount > 0) {
                        appendLine()
                        appendLine("Failures:")
                        results.filter { !it.second.isSuccess() }.forEach { (name, result) ->
                            appendLine("  - $name: ${result.getMessage()}")
                        }
                    }
                }
                
                val type = when {
                    failCount == 0 -> NotificationType.INFORMATION
                    successCount == 0 -> NotificationType.ERROR
                    else -> NotificationType.WARNING
                }
                
                showNotification(project, "Deploy ${config.name}", message, type)
            }
        })
    }

    private fun showNotification(
        project: Project,
        title: String,
        content: String,
        type: NotificationType
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("JarDeploy")
            .createNotification(title, content, type)
            .notify(project)
    }
}

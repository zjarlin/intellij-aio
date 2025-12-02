package site.addzero.deploy.pipeline

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import site.addzero.deploy.*
import java.io.File

/**
 * 部署流水线 - 多阶段部署执行器
 */
class DeployPipeline(
    private val project: Project,
    private val config: DeployConfiguration,
    private val target: DeployTarget
) {
    private val log = Logger.getInstance(DeployPipeline::class.java)
    private val sessionService = DeploySessionService.getInstance(project)
    private lateinit var session: DeploySession

    fun execute() {
        val configName = config.name ?: "Unknown"
        val targetName = target.name ?: "Unknown"

        session = sessionService.createSession(configName, targetName)

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Deploying $configName",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                session.status = DeployStatus.RUNNING
                sessionService.updateSession(session)

                try {
                    // 阶段1: 构建
                    if (!executeBuildPhase(indicator)) {
                        finishWithFailure("Build failed")
                        return
                    }

                    // 阶段2: 上传
                    if (!executeUploadPhase(indicator)) {
                        finishWithFailure("Upload failed")
                        return
                    }

                    // 阶段3: 部署
                    if (!executeDeployPhase(indicator)) {
                        finishWithFailure("Deploy failed")
                        return
                    }

                    finishWithSuccess()

                } catch (e: Exception) {
                    log.error("Deploy pipeline failed", e)
                    addLog(session.currentPhase, LogLevel.ERROR, "Pipeline failed: ${e.message}")
                    finishWithFailure(e.message ?: "Unknown error")
                }
            }

            override fun onCancel() {
                sessionService.cancelSession(session.id)
                showNotification("Deploy Cancelled", "Deployment was cancelled", NotificationType.WARNING)
            }
        })
    }

    private fun executeBuildPhase(indicator: ProgressIndicator): Boolean {
        session.startPhase(DeployPhase.BUILD)
        sessionService.updateSession(session)

        val buildTool = detectBuildTool()
        if (buildTool is BuildTool.None) {
            addLog(DeployPhase.BUILD, LogLevel.INFO, "No build required, skipping build phase")
            session.completePhase(DeployPhase.BUILD, true)
            sessionService.updateSession(session)
            return true
        }

        indicator.text = "[BUILD] ${buildTool.displayName}..."
        addLog(DeployPhase.BUILD, LogLevel.INFO, "Starting ${buildTool.displayName} build")

        val buildExecutor = BuildExecutor(project, sessionService, session.id)
        val result = buildExecutor.execute(buildTool) { progress, message ->
            session.updatePhase(DeployPhase.BUILD, progress, message)
            indicator.fraction = DeployPhase.PHASE_WEIGHTS[DeployPhase.BUILD]!! * progress
            indicator.text = "[BUILD] $message"
            sessionService.updateSession(session)
        }

        session.completePhase(DeployPhase.BUILD, result.success)
        sessionService.updateSession(session)
        return result.success
    }

    private fun executeUploadPhase(indicator: ProgressIndicator): Boolean {
        session.startPhase(DeployPhase.UPLOAD)
        sessionService.updateSession(session)

        val enabledArtifacts = config.artifacts.filter { it.enabled }
        if (enabledArtifacts.isEmpty()) {
            addLog(DeployPhase.UPLOAD, LogLevel.WARN, "No artifacts to upload")
            session.completePhase(DeployPhase.UPLOAD, false)
            sessionService.updateSession(session)
            return false
        }

        indicator.text = "[UPLOAD] Connecting to ${target.sshConfigName}..."
        addLog(DeployPhase.UPLOAD, LogLevel.INFO, "Connecting to ${target.sshConfigName}")

        val sshService = SshDeployService.getInstance(project)
        val baseProgress = DeployPhase.PHASE_WEIGHTS[DeployPhase.BUILD]!!
        val uploadWeight = DeployPhase.PHASE_WEIGHTS[DeployPhase.UPLOAD]!!

        var successCount = 0
        val totalArtifacts = enabledArtifacts.size

        enabledArtifacts.forEachIndexed { index, artifact ->
            val filePath = artifact.path
            if (filePath.isBlank()) {
                addLog(DeployPhase.UPLOAD, LogLevel.ERROR, "Empty file path")
                return@forEachIndexed
            }

            val file = File(filePath)
            if (!file.exists()) {
                addLog(DeployPhase.UPLOAD, LogLevel.ERROR, "File not found: $filePath")
                return@forEachIndexed
            }

            val progress = (index.toDouble() / totalArtifacts)
            session.updatePhase(DeployPhase.UPLOAD, progress, "Uploading ${file.name}")
            indicator.text = "[UPLOAD] ${file.name} (${index + 1}/$totalArtifacts)"
            indicator.fraction = baseProgress + uploadWeight * progress
            sessionService.updateSession(session)

            addLog(DeployPhase.UPLOAD, LogLevel.INFO, "Uploading ${file.name} (${formatFileSize(file.length())})")

            val result = if (artifact.isDirectory) {
                sshService.deployDirectory(file, target, indicator)
            } else {
                sshService.deployFile(file, target, indicator)
            }

            if (result.isSuccess()) {
                successCount++
                addLog(DeployPhase.UPLOAD, LogLevel.INFO, "Uploaded ${file.name} successfully")
            } else {
                addLog(DeployPhase.UPLOAD, LogLevel.ERROR, "Failed to upload ${file.name}: ${result.getMessage()}")
            }
        }

        val success = successCount == totalArtifacts
        session.updatePhase(DeployPhase.UPLOAD, 1.0, "Uploaded $successCount/$totalArtifacts files")
        session.completePhase(DeployPhase.UPLOAD, success)
        sessionService.updateSession(session)

        return success
    }

    private fun executeDeployPhase(indicator: ProgressIndicator): Boolean {
        session.startPhase(DeployPhase.DEPLOY)
        sessionService.updateSession(session)

        val baseProgress = DeployPhase.PHASE_WEIGHTS[DeployPhase.BUILD]!! + DeployPhase.PHASE_WEIGHTS[DeployPhase.UPLOAD]!!
        val deployWeight = DeployPhase.PHASE_WEIGHTS[DeployPhase.DEPLOY]!!

        // 执行部署后命令
        val postCmd = target.postDeployCommand
        if (!postCmd.isNullOrBlank()) {
            indicator.text = "[DEPLOY] Executing post-deploy command..."
            addLog(DeployPhase.DEPLOY, LogLevel.INFO, "Executing: $postCmd")

            session.updatePhase(DeployPhase.DEPLOY, 0.5, "Running post-deploy command")
            indicator.fraction = baseProgress + deployWeight * 0.5
            sessionService.updateSession(session)

            // 命令执行在 SshDeployService 中已经处理
        }

        session.updatePhase(DeployPhase.DEPLOY, 1.0, "Deployment completed")
        session.completePhase(DeployPhase.DEPLOY, true)
        indicator.fraction = 1.0
        sessionService.updateSession(session)

        addLog(DeployPhase.DEPLOY, LogLevel.INFO, "Deployment completed successfully")
        return true
    }

    private fun detectBuildTool(): BuildTool {
        val basePath = project.basePath ?: return BuildTool.None

        // 检查 pom.xml
        val pomFile = File(basePath, "pom.xml")
        if (pomFile.exists()) {
            return BuildTool.Maven(pomFile.absolutePath)
        }

        // 检查 build.gradle 或 build.gradle.kts
        val gradleFile = File(basePath, "build.gradle")
        val gradleKtsFile = File(basePath, "build.gradle.kts")
        if (gradleFile.exists() || gradleKtsFile.exists()) {
            return BuildTool.Gradle(basePath)
        }

        return BuildTool.None
    }

    private fun finishWithSuccess() {
        sessionService.completeSession(session.id, true)
        showNotification(
            "Deploy Success",
            "Deployed ${config.name} to ${target.name} in ${session.getDurationFormatted()}",
            NotificationType.INFORMATION
        )
    }

    private fun finishWithFailure(reason: String) {
        sessionService.completeSession(session.id, false)
        showNotification(
            "Deploy Failed",
            "Failed to deploy ${config.name}: $reason",
            NotificationType.ERROR
        )
    }

    private fun addLog(phase: DeployPhase, level: LogLevel, message: String) {
        sessionService.addLog(session.id, phase, level, message)
    }

    private fun showNotification(title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("JarDeploy")
            .createNotification(title, content, type)
            .notify(project)
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}

/**
 * 部署执行器入口 - 兼容旧接口
 */
object DeployExecutor {
    fun deploy(project: Project, config: DeployConfiguration, target: DeployTarget) {
        DeployPipeline(project, config, target).execute()
    }
}

package site.addzero.cloudfile.sync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.delay
import site.addzero.cloudfile.git.GitIntegrationService
import site.addzero.cloudfile.settings.CloudFileSettings
import site.addzero.cloudfile.settings.ProjectHostingSettings

/**
 * Startup activity that initializes cloud file hosting for the project
 * Performs initial sync from cloud to local (remote is always authoritative)
 */
class CloudFileStartupActivity : ProjectActivity {

    private val logger = Logger.getInstance(CloudFileStartupActivity::class.java)

    override suspend fun execute(project: Project) {
        val settings = CloudFileSettings.getInstance()
        val projectSettings = ProjectHostingSettings.getInstance(project)

        if (!projectSettings.state.enabled) {
            logger.info("Cloud File Hosting is disabled for project: ${project.name}")
            return
        }

        // Wait for project to fully load
        delay(2000)

        logger.info("Initializing Cloud File Hosting for project: ${project.name}")

        // Initialize Git info for custom rules
        val gitService = GitIntegrationService.getInstance(project)
        val gitInfo = gitService.getGitInfo()
        logger.info("Git info: authors=${gitInfo.authors}, remote=${gitInfo.remoteUrl}")

        // Perform initial sync from cloud (remote is authoritative) with progress
        if (settings.state.autoSync) {
            ApplicationManager.getApplication().invokeLater {
                ProgressManager.getInstance().run(
                    object : Task.Backgroundable(project, "Cloud File Hosting - Initial Sync", true) {
                        override fun run(indicator: ProgressIndicator) {
                            indicator.isIndeterminate = false
                            indicator.text = "Connecting to cloud storage..."

                            val syncService = CloudFileSyncService.getInstance(project)
                            try {
                                syncService.syncFromCloud(indicator)
                            } catch (e: Exception) {
                                logger.error("Initial sync failed", e)
                            }
                        }
                    }
                )
            }
        }
    }
}

package site.addzero.cloudfile.sync

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        // Initialize sync service
        val syncService = CloudFileSyncService.getInstance(project)

        // Perform initial sync from cloud (remote is authoritative)
        if (settings.state.autoSync) {
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                try {
                    syncService.syncFromCloud(null)
                } catch (e: Exception) {
                    logger.error("Initial sync failed", e)
                }
            }
        }
    }
}

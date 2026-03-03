package site.addzero.cloudfile.sync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.*
import site.addzero.cloudfile.settings.CloudFileSettings
import site.addzero.cloudfile.settings.ProjectHostingSettings
import java.util.concurrent.TimeUnit

/**
 * Background periodic sync task
 * Runs at configured intervals to sync changes to cloud
 */
class CloudFileBackgroundSync : ProjectActivity {

    private val logger = Logger.getInstance(CloudFileBackgroundSync::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun execute(project: Project) {
        val settings = CloudFileSettings.getInstance()
        val projectSettings = ProjectHostingSettings.getInstance(project)

        if (!settings.state.autoSync) return
        if (!projectSettings.state.enabled) return

        val intervalMinutes = settings.state.syncIntervalMinutes
        if (intervalMinutes <= 0) return

        logger.info("Starting background sync for ${project.name} with interval ${intervalMinutes}min")

        // Start periodic sync
        scope.launch {
            while (isActive) {
                delay(TimeUnit.MINUTES.toMillis(intervalMinutes.toLong()))

                if (project.isDisposed) {
                    cancel()
                    return@launch
                }

                performBackgroundSync(project)
            }
        }
    }

    private fun performBackgroundSync(project: Project) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                "Cloud File Hosting - Background Sync",
                false // non-cancellable
            ) {
                override fun run(indicator: ProgressIndicator) {
                    val syncService = CloudFileSyncService.getInstance(project)
                    syncService.syncToCloud(force = false, indicator = indicator)
                }
            }
        )
    }

    fun dispose() {
        scope.cancel()
    }
}

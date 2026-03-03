package site.addzero.cloudfile.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import site.addzero.cloudfile.sync.CloudFileSyncService

/**
 * Action to sync selected files/directories to cloud storage
 */
class SyncToCloudAction : AnAction(
    "Sync to Cloud",
    "Sync all configured files to cloud storage",
    com.intellij.icons.AllIcons.Actions.Upload
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        performSyncWithProgress(project)
    }

    private fun performSyncWithProgress(project: Project) {
        // Use modal task for visible progress dialog
        ProgressManager.getInstance().run(
            object : Task.Modal(project, "Syncing to Cloud", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = false
                    indicator.text = "Initializing..."
                    indicator.fraction = 0.0

                    val syncService = CloudFileSyncService.getInstance(project)
                    syncService.syncToCloud(force = true, indicator = indicator)
                }
            }
        )
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }
}

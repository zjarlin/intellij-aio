package site.addzero.cloudfile.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import site.addzero.cloudfile.sync.CloudFileSyncService

/**
 * Action to sync files from cloud storage (overwrites local)
 * Remote is authoritative
 */
class SyncFromCloudAction : AnAction(
    "Sync from Cloud (Overwrite Local)",
    "Download files from cloud and overwrite local files",
    com.intellij.icons.AllIcons.Actions.Download
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val result = Messages.showYesNoDialog(
            project,
            "This will overwrite local files with remote versions. Continue?",
            "Confirm Download",
            Messages.getWarningIcon()
        )

        if (result != Messages.YES) return

        // Use modal task for visible progress dialog
        ProgressManager.getInstance().run(
            object : Task.Modal(project, "Syncing from Cloud", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = false
                    indicator.text = "Connecting to cloud..."
                    indicator.fraction = 0.0

                    val syncService = CloudFileSyncService.getInstance(project)
                    syncService.syncFromCloud(indicator)
                }
            }
        )
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }
}

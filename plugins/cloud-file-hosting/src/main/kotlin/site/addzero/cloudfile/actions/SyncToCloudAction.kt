package site.addzero.cloudfile.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.ui.Messages
import site.addzero.cloudfile.sync.CloudFileSyncService

/**
 * Action to sync selected files/directories to cloud storage
 */
class SyncToCloudAction : AnAction(
    "Sync to Cloud",
    "Sync selected files to cloud storage",
    com.intellij.icons.AllIcons.Actions.Upload
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)

        if (virtualFiles.isNullOrEmpty()) {
            Messages.showWarningDialog(project, "Please select files to sync", "No Selection")
            return
        }

        val syncService = CloudFileSyncService.getInstance(project)

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Syncing to Cloud", true) {
                override fun run(indicator: ProgressIndicator) {
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

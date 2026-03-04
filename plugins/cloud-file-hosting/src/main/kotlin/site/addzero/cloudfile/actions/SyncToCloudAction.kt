package site.addzero.cloudfile.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.cloudfile.sync.CloudFileSyncService

/**
 * Action to sync selected files/directories to cloud storage
 * Automatically adds rules for files not yet configured
 */
class SyncToCloudAction : AnAction(
    "Sync to Cloud",
    "Sync selected files/folders to cloud storage (auto-adds if not configured)",
    com.intellij.icons.AllIcons.Actions.Upload
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)

        performSyncWithProgress(project, virtualFiles?.toList())
    }

    private fun performSyncWithProgress(project: Project, selectedFiles: List<VirtualFile>?) {
        ProgressManager.getInstance().run(
            object : Task.Modal(project, "Syncing to Cloud", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = false
                    indicator.text = "Checking files..."
                    indicator.fraction = 0.0

                    val syncService = CloudFileSyncService.getInstance(project)
                    syncService.syncToCloud(
                        force = true,
                        indicator = indicator,
                        selectedFiles = selectedFiles
                    )
                }
            }
        )
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }
}

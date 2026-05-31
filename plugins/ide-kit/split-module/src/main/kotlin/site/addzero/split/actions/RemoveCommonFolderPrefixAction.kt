package site.addzero.split.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.split.services.CommonFolderPrefixCleaner

class RemoveCommonFolderPrefixAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val selectedDirectories = e.selectedDirectories()
        e.presentation.isEnabledAndVisible = project != null && selectedDirectories.isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedDirectories = e.selectedDirectories()
        if (selectedDirectories.isEmpty()) {
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Preparing common folder prefix cleanup",
            true,
        ) {
            override fun run(indicator: ProgressIndicator) {
                val cleaner = CommonFolderPrefixCleaner(project)
                val plan = try {
                    indicator.text = "Inferring repeated folder prefixes..."
                    cleaner.createPlan(selectedDirectories)
                } catch (e: IllegalStateException) {
                    cleaner.notifyError("Common Prefix Cleanup Failed", e.message ?: "Unable to create cleanup plan.")
                    return
                }

                var confirmed = false
                ApplicationManager.getApplication().invokeAndWait(
                    {
                        confirmed = showConfirmation(project, plan)
                    },
                    ModalityState.any(),
                )
                if (!confirmed) {
                    return
                }

                try {
                    indicator.text = "Removing repeated folder prefixes..."
                    val result = cleaner.apply(plan, indicator)
                    cleaner.notifySuccess(
                        "Common Prefix Cleanup Complete",
                        "Removed '${plan.prefixSummary()}' from ${result.renamedFolderCount} folders, " +
                            "updated ${result.updatedCodeFileCount} code files.",
                    )
                } catch (e: Exception) {
                    cleaner.notifyError("Common Prefix Cleanup Failed", e.message ?: "Unknown error.")
                }
            }
        })
    }

    private fun AnActionEvent.selectedDirectories(): List<VirtualFile> {
        return getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
            .orEmpty()
            .filter { it.isDirectory }
            .distinctBy { it.path }
    }

    private fun showConfirmation(project: Project, plan: CommonFolderPrefixCleaner.CleanupPlan): Boolean {
        val renamePreview = plan.renameEntries
            .take(20)
            .joinToString("\n") { entry -> "${entry.oldName} -> ${entry.newName}" }
        val more = plan.renameEntries.size - 20
        val moreLine = if (more > 0) {
            "\n... and $more more"
        } else {
            ""
        }
        val packageLine = if (plan.packageMappings.isEmpty()) {
            "No source-root package mappings were inferred."
        } else {
            "${plan.packageMappings.size} package prefixes will be replaced across project code files."
        }
        val message = buildString {
            appendLine("Remove inferred folder prefix:")
            appendLine(plan.groupPlans.joinToString("\n") { group -> "${group.parentPath}: '${group.prefix}'" })
            appendLine()
            appendLine("Rename preview:")
            appendLine(renamePreview)
            appendLine(moreLine)
            appendLine()
            append(packageLine)
        }

        return Messages.showOkCancelDialog(
            project,
            message,
            "Remove Common Folder Prefix",
            "Remove Prefix",
            "Cancel",
            Messages.getQuestionIcon(),
        ) == Messages.OK
    }
}

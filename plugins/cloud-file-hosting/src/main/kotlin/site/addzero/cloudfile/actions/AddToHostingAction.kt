package site.addzero.cloudfile.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.cloudfile.settings.CloudFileSettings
import site.addzero.cloudfile.settings.ProjectHostingSettings

/**
 * Action to add selected file/directory to cloud hosting rules
 */
class AddToHostingAction : AnAction(
    "Add to Cloud Hosting",
    "Add selected file or directory to cloud hosting rules",
    com.intellij.icons.AllIcons.Nodes.PpLibFolder
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val options = arrayOf("Add to Project Rules", "Add to Global Rules")
        val choice = Messages.showChooseDialog(
            project,
            "Where do you want to add '${virtualFile.name}'?",
            "Add to Cloud Hosting",
            Messages.getQuestionIcon(),
            options,
            options[0]
        )

        if (choice == -1) return

        val relativePath = getRelativePath(project, virtualFile)
        if (relativePath == null) {
            Messages.showErrorDialog(project, "Could not determine relative path", "Error")
            return
        }

        when (choice) {
            0 -> addToProjectRules(project, virtualFile, relativePath)
            1 -> addToGlobalRules(project, virtualFile, relativePath)
        }
    }

    private fun addToProjectRules(project: Project, file: VirtualFile, relativePath: String) {
        val settings = ProjectHostingSettings.getInstance(project)

        val type = when {
            file.isDirectory -> CloudFileSettings.HostingRule.RuleType.DIRECTORY
            relativePath.contains("*") -> CloudFileSettings.HostingRule.RuleType.GLOB
            else -> CloudFileSettings.HostingRule.RuleType.FILE
        }

        // Ensure directory paths end with /
        val pattern = if (type == CloudFileSettings.HostingRule.RuleType.DIRECTORY && !relativePath.endsWith("/")) {
            "$relativePath/"
        } else {
            relativePath
        }

        settings.addProjectRule(pattern, type)
        Messages.showInfoMessage(project as com.intellij.openapi.project.Project, "Added '$pattern' to project hosting rules", "Success")
    }

    private fun addToGlobalRules(project: Project, file: VirtualFile, relativePath: String) {
        val settings = CloudFileSettings.getInstance()

        val type = when {
            file.isDirectory -> CloudFileSettings.HostingRule.RuleType.DIRECTORY
            relativePath.contains("*") -> CloudFileSettings.HostingRule.RuleType.GLOB
            else -> CloudFileSettings.HostingRule.RuleType.FILE
        }

        // Ensure directory paths end with /
        val pattern = if (type == CloudFileSettings.HostingRule.RuleType.DIRECTORY && !relativePath.endsWith("/")) {
            "$relativePath/"
        } else {
            relativePath
        }

        settings.addGlobalRule(pattern, type)
        Messages.showInfoMessage(project as com.intellij.openapi.project.Project, "Added '$pattern' to global hosting rules", "Success")
    }

    private fun getRelativePath(project: Project, file: VirtualFile): String? {
        val basePath = project.basePath ?: return null
        return if (file.path.startsWith(basePath)) {
            file.path.substring(basePath.length).removePrefix("/")
        } else {
            null
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabled = project != null && virtualFile != null
    }
}

package site.addzero.dotfiles.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import site.addzero.dotfiles.manifest.EntryMode
import site.addzero.dotfiles.manifest.EntryScope
import site.addzero.dotfiles.manifest.ManifestEntry
import site.addzero.dotfiles.sync.DotfilesProjectSyncService
import java.nio.file.Paths

class AddToDotfilesBackupAction : AnAction("Add to Dotfiles Backup") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val basePath = project.basePath ?: return

        val options = arrayOf("User (shared)", "Project (local)")
        val choice = Messages.showDialog(
            project,
            "Backup scope",
            "Dotfiles",
            options,
            0,
            null
        )
        if (choice < 0) return
        val mode = if (choice == 1) EntryMode.PROJECT else EntryMode.USER

        val root = Paths.get(basePath)
        val relative = root.relativize(Paths.get(file.path)).toString().replace('\\', '/')
        val isIgnored = FileStatusManager.getInstance(project).getStatus(file) == FileStatus.IGNORED
        val includeIgnored = if (isIgnored) {
            val choice = Messages.showYesNoDialog(
                project,
                "This path is ignored by VCS. Back it up anyway?",
                "Ignored File",
                null
            )
            choice == Messages.YES
        } else {
            false
        }
        val entry = ManifestEntry(
            id = relative.replace('/', '_'),
            path = relative,
            scope = EntryScope.PROJECT_ROOT,
            mode = mode,
            includeIgnored = includeIgnored,
            excludeFromGit = true,
        )
        project.getService(DotfilesProjectSyncService::class.java)
            .addEntry(entry, toUserManifest = mode == EntryMode.USER)
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null
    }
}

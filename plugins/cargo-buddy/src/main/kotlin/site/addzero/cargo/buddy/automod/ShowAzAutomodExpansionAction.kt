package site.addzero.cargo.buddy.automod

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import site.addzero.cargo.buddy.model.CargoCrateResolver
import kotlin.io.path.Path

class ShowAzAutomodExpansionAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val document = file?.let { FileDocumentManager.getInstance().getDocument(it) }
        val isAvailable = project != null &&
            file != null &&
            file.extension == "rs" &&
            project.let { CargoCrateResolver.isCargoFile(it, file) } &&
            document?.text?.let(AzAutomodExpander::hasAutomodCall) == true

        e.presentation.isEnabledAndVisible = isAvailable
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        val cargoCrate = CargoCrateResolver.resolveForFile(project, file) ?: return

        try {
            val result = AzAutomodExpander.expandText(
                source = document.text,
                manifestDirectory = Path(cargoCrate.rootPath),
            )
            if (result.expansionCount == 0) {
                notify(project, "No az-automod calls found", NotificationType.INFORMATION)
                return
            }

            val virtualFile = LightVirtualFile(
                expansionFileName(file),
                result.expandedText,
            ).apply {
                isWritable = false
            }
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        } catch (e: AzAutomodExpansionException) {
            notify(project, e.message ?: "Unable to expand az-automod", NotificationType.ERROR)
        } catch (e: Exception) {
            notify(project, e.message ?: e.javaClass.simpleName, NotificationType.ERROR)
        }
    }

    companion object {
        private fun expansionFileName(file: VirtualFile): String {
            return "${file.nameWithoutExtension}.az-automod.expanded.rs"
        }

        private fun notify(
            project: Project,
            content: String,
            type: NotificationType,
        ) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("CargoBuddy")
                .createNotification("Cargo Buddy", content, type)
                .notify(project)
        }
    }
}

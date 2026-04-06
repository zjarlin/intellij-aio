package site.addzero.gradle.buddy.filter

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.gradle.buddy.i18n.GradleBuddyActionI18n
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import java.awt.datatransfer.StringSelection

class CopyPublishCommandsAction : AnAction() {

    init {
        syncPresentation()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val targets = resolveTargets(e)
        if (targets.isEmpty()) {
            notify(
                project = project,
                title = GradleBuddyBundle.message("action.copy.publish.commands.none.title"),
                content = GradleBuddyBundle.message("action.copy.publish.commands.none.content"),
                type = NotificationType.WARNING,
            )
            return
        }

        val commands = targets.joinToString(separator = "\n") { it.command }
        CopyPasteManager.getInstance().setContents(StringSelection(commands))

        notify(
            project = project,
            title = GradleBuddyBundle.message("action.copy.publish.commands.copied.title"),
            content = GradleBuddyBundle.message(
                "action.copy.publish.commands.copied.content",
                targets.size,
            ),
            type = NotificationType.INFORMATION,
        )
    }

    override fun update(e: AnActionEvent) {
        syncPresentation(e.presentation)
        val project = e.project
        val selections = getSelections(e)
        e.presentation.isEnabledAndVisible = project != null &&
            selections.isNotEmpty() &&
            GradlePublishSelectionResolver.canResolve(project, selections)
    }

    private fun syncPresentation(presentation: com.intellij.openapi.actionSystem.Presentation? = null) {
        GradleBuddyActionI18n.sync(
            this,
            presentation,
            "action.copy.publish.commands.title",
            "action.copy.publish.commands.description",
        )
    }

    private fun resolveTargets(e: AnActionEvent): List<GradlePublishSelectionResolver.PublishTarget> {
        val project = e.project ?: return emptyList()
        return GradlePublishSelectionResolver.resolve(project, getSelections(e))
    }

    private fun getSelections(e: AnActionEvent): List<VirtualFile> {
        return e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
            ?.toList()
            ?.filterNotNull()
            ?.takeIf { it.isNotEmpty() }
            ?: e.getData(CommonDataKeys.VIRTUAL_FILE)?.let(::listOf).orEmpty()
    }

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(title, content, type)
            .notify(project)
    }
}

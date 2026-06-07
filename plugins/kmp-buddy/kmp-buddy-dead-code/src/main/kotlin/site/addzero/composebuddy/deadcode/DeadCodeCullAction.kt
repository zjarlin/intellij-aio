package site.addzero.composebuddy.deadcode

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class DeadCodeCullAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val file = event.getData(CommonDataKeys.PSI_FILE) as? KtFile
        val project = event.project
        event.presentation.isEnabledAndVisible = project != null && file != null
        event.presentation.text = "KMP Buddy: Cull Dead Code From Entry"
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val ktFile = event.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return
        val editor = event.getData(CommonDataKeys.EDITOR)
        val entryFunction = editor
            ?.let { ktFile.findElementAt(it.caretModel.offset) }
            ?.getStrictParentOfType<KtNamedFunction>()
        val entryVirtualFile = ktFile.virtualFile
        val sourceModuleRoot = DeadCodePaths.defaultSourceModuleRoot(project, entryVirtualFile)
        if (sourceModuleRoot == null) {
            notify(project, "Unable to resolve a source module for dead-code culling.", NotificationType.WARNING)
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Culling KMP Buddy dead code",
            true,
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Analyzing reachable declarations..."
                val analysis = ReadAction.compute<DeadCodeAnalysisResult, RuntimeException> {
                    DeadCodeReachabilityAnalyzer(project).analyze(
                        entryFile = ktFile,
                        entryFunction = entryFunction,
                        sourceModuleRoot = sourceModuleRoot,
                    )
                }

                if (analysis.movableDeadFiles.isEmpty() && analysis.mixedFiles.isEmpty()) {
                    notify(project, "No dead-code candidates were found.", NotificationType.INFORMATION)
                    return
                }

                indicator.text = "Writing dead-code mirror module..."
                val result = WriteCommandAction.writeCommandAction(project)
                    .withName("Cull KMP Buddy dead code")
                    .compute<DeadCodeCullResult?, RuntimeException> {
                        DeadCodeMirrorWriter(project).write(sourceModuleRoot, analysis)
                    }
                if (result == null) {
                    notify(project, "Unable to write the dead-code mirror module.", NotificationType.WARNING)
                    return
                }

                notify(
                    project = project,
                    message = "Moved ${result.movedFileCount} dead file(s). Mixed files left in report: ${result.mixedFileCount}.",
                    type = if (result.movedFileCount > 0) NotificationType.INFORMATION else NotificationType.WARNING,
                )
            }
        })
    }

    private fun notify(
        project: Project,
        message: String,
        type: NotificationType,
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("KMP Buddy Notifications")
            .createNotification(message, type)
            .notify(project)
    }
}

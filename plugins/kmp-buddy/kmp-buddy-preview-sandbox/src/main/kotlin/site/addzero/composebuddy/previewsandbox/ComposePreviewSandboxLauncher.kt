package site.addzero.composebuddy.previewsandbox

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtNamedFunction

object ComposePreviewSandboxLauncher {
    fun open(anchor: PsiElement) {
        val project = anchor.project
        val prepared = ReadAction.compute<PreparedPreviewSandbox?, Throwable> {
            val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(anchor)
            val currentAnchor = pointer.element ?: return@compute null
            val function = PsiTreeUtil.getParentOfType(currentAnchor, KtNamedFunction::class.java)
                ?: return@compute null
            if (!ComposePreviewSandboxSupport.isPreviewFunction(function)) {
                return@compute null
            }
            val snapshot = PreviewReachableAstCollector.collect(function) ?: return@compute null
            PreparedPreviewSandbox(
                snapshot = snapshot,
                previewFunctionPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(function),
            )
        }

        if (prepared == null) {
            notify(project, "KMP Buddy could not resolve this Compose preview.", NotificationType.WARNING)
            return
        }

        val written = ComposePreviewSandboxWriter.write(project, prepared.snapshot)
        if (written == null) {
            notify(project, "KMP Buddy could not create a preview sandbox for this project.", NotificationType.ERROR)
            return
        }

        ComposePreviewSandboxService.getInstance(project).updateSession(
            ComposePreviewSandboxSession(
                snapshot = prepared.snapshot,
                written = written,
                previewFunctionPointer = prepared.previewFunctionPointer,
            ),
        )

        showPreviewToolWindow(project)

        notify(
            project = project,
            message = "KMP Buddy preview sandbox ready: ${written.declarationCount} declarations in ${written.generatedFileCount} files.",
            type = NotificationType.INFORMATION,
        )
    }

    private fun showPreviewToolWindow(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }
            val toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow(ComposePreviewSandboxToolWindowFactory.TOOL_WINDOW_ID)
            toolWindow?.show()
        }
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

private data class PreparedPreviewSandbox(
    val snapshot: PreviewSandboxSnapshot,
    val previewFunctionPointer: com.intellij.psi.SmartPsiElementPointer<KtNamedFunction>,
)

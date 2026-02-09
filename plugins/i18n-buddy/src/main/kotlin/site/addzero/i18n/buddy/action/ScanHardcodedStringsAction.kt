package site.addzero.i18n.buddy.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import site.addzero.i18n.buddy.refactor.I18nRefactorEngine
import site.addzero.i18n.buddy.scanner.HardcodedStringScanner
import site.addzero.i18n.buddy.scanner.ScanResult
import site.addzero.i18n.buddy.ui.I18nPreviewDialog

/**
 * Main entry point: Tools → I18n Buddy: Scan Hardcoded Strings
 *
 * 1. Scans the project for hardcoded string literals
 * 2. Shows a preview dialog for user to confirm/edit keys
 * 3. Generates constant file + replaces strings + adds imports
 */
class ScanHardcodedStringsAction : AnAction(
    "I18n Buddy: Scan Hardcoded Strings",
    "Scan project for hardcoded strings and extract to i18n constants",
    null
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Scanning hardcoded strings...", true) {
            private var results: List<ScanResult> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                results = HardcodedStringScanner(project).scan()
            }

            override fun onSuccess() {
                if (results.isEmpty()) {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("I18nBuddy")
                        .createNotification("No hardcoded strings found.", NotificationType.INFORMATION)
                        .notify(project)
                    return
                }

                // Show preview dialog on EDT
                val dialog = I18nPreviewDialog(project, results)
                if (dialog.showAndGet()) {
                    val confirmed = dialog.getResults()
                    val selectedCount = confirmed.count { it.selected }
                    if (selectedCount == 0) return

                    I18nRefactorEngine(project).execute(confirmed)

                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("I18nBuddy")
                        .createNotification(
                            "I18n Buddy: Extracted $selectedCount strings.",
                            NotificationType.INFORMATION
                        )
                        .notify(project)
                }
            }
        })
    }

    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
}

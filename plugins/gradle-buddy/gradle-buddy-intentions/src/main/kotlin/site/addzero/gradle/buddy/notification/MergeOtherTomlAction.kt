package site.addzero.gradle.buddy.notification

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import site.addzero.gradle.buddy.i18n.GradleBuddyActionI18n
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

/**
 * 将外部 TOML 内容合并到当前版本目录文件。
 */
class MergeOtherTomlAction : AnAction(
    GradleBuddyBundle.message("action.merge.other.toml.title"),
    GradleBuddyBundle.message("action.merge.other.toml.description"),
    null
), DumbAware {

    init {
        syncPresentation()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val dialog = MergeOtherTomlDialog(project)
        if (!dialog.showAndGet()) {
            return
        }

        val importedContent = dialog.getTomlContent().trim()
        val documentManager = FileDocumentManager.getInstance()
        val document = documentManager.getDocument(file)
        val currentContent = document?.text ?: String(file.contentsToByteArray())

        try {
            val mergeResult = VersionCatalogMergeUtil.merge(currentContent, importedContent)
            val organizedContent = VersionCatalogSorter(project).organizeContent(mergeResult.content)
            if (organizedContent == currentContent) {
                Messages.showInfoMessage(
                    project,
                    GradleBuddyBundle.message("action.merge.other.toml.result.no.change"),
                    GradleBuddyBundle.message("action.merge.other.toml.dialog.title")
                )
                return
            }

            WriteCommandAction.runWriteCommandAction(
                project,
                GradleBuddyBundle.message("action.merge.other.toml.command"),
                null,
                Runnable {
                    if (document != null) {
                        document.replaceString(0, document.textLength, organizedContent)
                        PsiDocumentManager.getInstance(project).commitDocument(document)
                        documentManager.saveDocument(document)
                    } else {
                        file.setBinaryContent(organizedContent.toByteArray())
                    }
                }
            )

            Messages.showInfoMessage(
                project,
                GradleBuddyBundle.message(
                    "action.merge.other.toml.result.success",
                    mergeResult.summary.versionsChanged,
                    mergeResult.summary.librariesChanged,
                    mergeResult.summary.pluginsChanged,
                    mergeResult.summary.bundlesChanged
                ),
                GradleBuddyBundle.message("action.merge.other.toml.result.success.title")
            )
        } catch (t: Throwable) {
            Messages.showErrorDialog(
                project,
                GradleBuddyBundle.message(
                    "action.merge.other.toml.result.error",
                    t.message ?: t::class.java.simpleName
                ),
                GradleBuddyBundle.message("action.merge.other.toml.result.error.title")
            )
        }
    }

    override fun update(e: AnActionEvent) {
        syncPresentation(e.presentation)
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private fun syncPresentation(presentation: com.intellij.openapi.actionSystem.Presentation? = null) {
        GradleBuddyActionI18n.sync(
            this,
            presentation,
            "action.merge.other.toml.title",
            "action.merge.other.toml.description"
        )
    }
}

package site.addzero.gradle.buddy.intentions.convert

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

/**
 * 将当前插件声明在整个仓库内统一注释掉。
 */
class GradleKtsCommentOutPluginProjectWideIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.plugin.comment.out.project.wide")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            GradleBuddyBundle.message("intention.plugin.comment.out.project.wide.preview")
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null || !GradlePluginCommentSupport.isTargetGradleKtsFile(file)) {
            return false
        }

        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val targetPlugin = GradlePluginCommentSupport.detectTargetPlugin(element) ?: return false
        val candidates = GradlePluginCommentSupport.collectFileCandidates(file, targetPlugin.pluginId)
        if (candidates.lineNumbers.isNotEmpty()) {
            return true
        }

        return collectRewritePlan(project, targetPlugin).filePlans.isNotEmpty()
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val element = file.findElementAt(editor?.caretModel?.offset ?: return) ?: return
        val targetPlugin = GradlePluginCommentSupport.detectTargetPlugin(element) ?: return

        val plan = collectRewritePlan(project, targetPlugin)
        if (plan.filePlans.isEmpty()) {
            Messages.showInfoMessage(
                project,
                GradleBuddyBundle.message(
                    "intention.plugin.comment.out.project.wide.none",
                    targetPlugin.displayName
                ),
                GradleBuddyBundle.message("intention.plugin.comment.out.project.wide.title")
            )
            return
        }

        val confirm = Messages.showYesNoDialog(
            project,
            buildConfirmMessage(targetPlugin, plan),
            GradleBuddyBundle.message("intention.plugin.comment.out.project.wide.confirm.title"),
            GradleBuddyBundle.message("intention.plugin.comment.out.project.wide.confirm.ok"),
            GradleBuddyBundle.message("intention.plugin.comment.out.project.wide.confirm.cancel"),
            Messages.getWarningIcon()
        )
        if (confirm != Messages.YES) {
            return
        }

        applyRewritePlan(project, plan)

        Messages.showInfoMessage(
            project,
            buildResultMessage(targetPlugin, plan),
            GradleBuddyBundle.message("intention.plugin.comment.out.project.wide.title")
        )
    }

    private fun collectRewritePlan(
        project: Project,
        targetPlugin: GradlePluginCommentSupport.TargetPlugin
    ): RewritePlan {
        val psiManager = PsiManager.getInstance(project)
        val filePlans = mutableListOf<FilePlan>()

        for (virtualFile in GradlePluginCommentSupport.collectTargetGradleKtsFiles(project)) {
            val psiFile = psiManager.findFile(virtualFile) ?: continue
            val candidates = GradlePluginCommentSupport.collectFileCandidates(psiFile, targetPlugin.pluginId)
            if (candidates.lineNumbers.isEmpty()) {
                continue
            }

            filePlans += FilePlan(
                file = virtualFile,
                lineNumbers = candidates.lineNumbers
            )
        }

        return RewritePlan(filePlans)
    }

    private fun applyRewritePlan(project: Project, plan: RewritePlan) {
        val documentManager = FileDocumentManager.getInstance()

        WriteCommandAction.runWriteCommandAction(
            project,
            GradleBuddyBundle.message("intention.plugin.comment.out.project.wide.command"),
            null
        ) {
            val psiDocumentManager = com.intellij.psi.PsiDocumentManager.getInstance(project)

            for (filePlan in plan.filePlans) {
                val document = documentManager.getDocument(filePlan.file) ?: continue
                val rewrittenText = GradlePluginCommentSupport.rewriteText(document.text, filePlan.lineNumbers)
                if (rewrittenText == document.text) {
                    continue
                }

                document.replaceString(0, document.textLength, rewrittenText)
                psiDocumentManager.commitDocument(document)
                documentManager.saveDocument(document)
            }
        }
    }

    private fun buildConfirmMessage(
        targetPlugin: GradlePluginCommentSupport.TargetPlugin,
        plan: RewritePlan
    ): String {
        val fileCount = plan.filePlans.size
        val occurrenceCount = plan.filePlans.sumOf { it.lineNumbers.size }

        return GradleBuddyBundle.message(
            "intention.plugin.comment.out.project.wide.confirm.body",
            targetPlugin.displayName,
            fileCount,
            occurrenceCount
        )
    }

    private fun buildResultMessage(
        targetPlugin: GradlePluginCommentSupport.TargetPlugin,
        plan: RewritePlan
    ): String {
        val fileCount = plan.filePlans.size
        val occurrenceCount = plan.filePlans.sumOf { it.lineNumbers.size }

        return GradleBuddyBundle.message(
            "intention.plugin.comment.out.project.wide.result.body",
            targetPlugin.displayName,
            fileCount,
            occurrenceCount
        )
    }

    private data class FilePlan(
        val file: VirtualFile,
        val lineNumbers: List<Int>
    )

    private data class RewritePlan(
        val filePlans: List<FilePlan>
    )
}

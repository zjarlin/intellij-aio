package site.addzero.gradle.buddy.intentions.convert

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

/**
 * 复用“将某个插件声明在整个仓库内统一注释掉”的项目级执行逻辑。
 */
internal object GradlePluginCommentProjectWideSupport {

    fun collectRewritePlan(project: Project, pluginId: String): RewritePlan {
        val psiManager = PsiManager.getInstance(project)
        val filePlans = mutableListOf<FilePlan>()

        for (virtualFile in GradlePluginCommentSupport.collectTargetGradleKtsFiles(project)) {
            val psiFile = psiManager.findFile(virtualFile) ?: continue
            val candidates = GradlePluginCommentSupport.collectFileCandidates(psiFile, pluginId)
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

    fun runWithConfirmation(project: Project, targetPlugin: GradlePluginCommentSupport.TargetPlugin): Boolean {
        val plan = collectRewritePlan(project, targetPlugin.pluginId)
        if (plan.filePlans.isEmpty()) {
            Messages.showInfoMessage(
                project,
                GradleBuddyBundle.message(
                    "intention.plugin.comment.out.project.wide.none",
                    targetPlugin.displayName
                ),
                GradleBuddyBundle.message("intention.plugin.comment.out.project.wide.title")
            )
            return false
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
            return false
        }

        applyRewritePlan(project, plan)

        Messages.showInfoMessage(
            project,
            buildResultMessage(targetPlugin, plan),
            GradleBuddyBundle.message("intention.plugin.comment.out.project.wide.title")
        )
        return true
    }

    private fun applyRewritePlan(project: Project, plan: RewritePlan) {
        val documentManager = FileDocumentManager.getInstance()

        WriteCommandAction.writeCommandAction(project)
            .withName(GradleBuddyBundle.message("intention.plugin.comment.out.project.wide.command"))
            .run<Throwable> {
            val psiDocumentManager = PsiDocumentManager.getInstance(project)

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

    data class FilePlan(
        val file: VirtualFile,
        val lineNumbers: List<Int>
    )

    data class RewritePlan(
        val filePlans: List<FilePlan>
    )
}

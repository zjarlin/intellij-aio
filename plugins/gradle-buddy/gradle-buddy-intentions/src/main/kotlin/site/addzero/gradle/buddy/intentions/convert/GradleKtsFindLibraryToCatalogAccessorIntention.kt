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
 * 将 `libs.findLibrary("alias").get()` 批量还原为强类型访问器 `libs.xxx.yyy`。
 */
class GradleKtsFindLibraryToCatalogAccessorIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.find.library.to.accessor")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            GradleBuddyBundle.message("intention.find.library.to.accessor.preview")
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null || !FindLibraryToCatalogAccessorSupport.isTargetGradleKtsFile(file)) {
            return false
        }

        val availableAliases = FindLibraryToCatalogAccessorSupport.loadAvailableLibraryAliases(project)
        val element = file.findElementAt(editor.caretModel.offset)
        if (element != null && FindLibraryToCatalogAccessorSupport.detectTargetReplacement(element, availableAliases) != null) {
            return true
        }

        if (FindLibraryToCatalogAccessorSupport.collectFileCandidates(file, availableAliases).replacements.isNotEmpty()) {
            return true
        }

        return FindLibraryToCatalogAccessorSupport.containsDynamicFindLibraryText(file.text)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val plan = collectRewritePlan(project)
        if (plan.filePlans.isEmpty()) {
            Messages.showInfoMessage(
                project,
                GradleBuddyBundle.message("intention.find.library.to.accessor.none"),
                GradleBuddyBundle.message("intention.find.library.to.accessor.title")
            )
            return
        }

        val confirm = Messages.showYesNoDialog(
            project,
            buildConfirmMessage(plan),
            GradleBuddyBundle.message("intention.find.library.to.accessor.confirm.title"),
            GradleBuddyBundle.message("intention.find.library.to.accessor.confirm.ok"),
            GradleBuddyBundle.message("intention.find.library.to.accessor.confirm.cancel"),
            Messages.getWarningIcon()
        )
        if (confirm != Messages.YES) {
            return
        }

        applyRewritePlan(project, plan)

        Messages.showInfoMessage(
            project,
            buildResultMessage(plan),
            GradleBuddyBundle.message("intention.find.library.to.accessor.title")
        )
    }

    private fun collectRewritePlan(project: Project): RewritePlan {
        val psiManager = PsiManager.getInstance(project)
        val availableAliases = FindLibraryToCatalogAccessorSupport.loadAvailableLibraryAliases(project)
        val filePlans = mutableListOf<FilePlan>()
        val unresolved = mutableListOf<String>()

        for (virtualFile in FindLibraryToCatalogAccessorSupport.collectTargetGradleKtsFiles(project)) {
            val psiFile = psiManager.findFile(virtualFile) ?: continue
            val fileCandidates = FindLibraryToCatalogAccessorSupport.collectFileCandidates(psiFile, availableAliases)
            unresolved += fileCandidates.unresolved

            if (fileCandidates.replacements.isEmpty()) {
                continue
            }

            filePlans += FilePlan(
                file = virtualFile,
                replacements = fileCandidates.replacements,
                hasShadowingLibsDeclaration = fileCandidates.hasShadowingLibsDeclaration
            )
        }

        return RewritePlan(
            filePlans = filePlans,
            unresolvedCount = unresolved.size,
            unresolvedSamples = unresolved.take(MAX_UNRESOLVED_SAMPLES)
        )
    }

    private fun applyRewritePlan(project: Project, plan: RewritePlan) {
        val documentManager = FileDocumentManager.getInstance()

        WriteCommandAction.runWriteCommandAction(
            project,
            GradleBuddyBundle.message("intention.find.library.to.accessor.command"),
            null,
            {
                val psiDocumentManager = com.intellij.psi.PsiDocumentManager.getInstance(project)

                for (filePlan in plan.filePlans) {
                    val document = documentManager.getDocument(filePlan.file) ?: continue
                    val rewrittenText = FindLibraryToCatalogAccessorSupport.rewriteText(
                        originalText = document.text,
                        replacements = filePlan.replacements,
                        cleanupShadowingDeclaration = filePlan.hasShadowingLibsDeclaration
                    )
                    if (rewrittenText == document.text) {
                        continue
                    }
                    document.replaceString(0, document.textLength, rewrittenText)
                    psiDocumentManager.commitDocument(document)
                    documentManager.saveDocument(document)
                }
            }
        )
    }

    private fun buildConfirmMessage(plan: RewritePlan): String {
        val fileCount = plan.filePlans.size
        val replacementCount = plan.filePlans.sumOf { it.replacements.size }
        val shadowFixCount = plan.filePlans.count { it.hasShadowingLibsDeclaration }
        val unresolvedLine = if (plan.unresolvedCount > 0) {
            GradleBuddyBundle.message(
                "intention.find.library.to.accessor.confirm.unresolved",
                plan.unresolvedCount,
                plan.unresolvedSamples.joinToString(separator = "\n") { "  $it" }
            )
        } else {
            ""
        }

        return GradleBuddyBundle.message(
            "intention.find.library.to.accessor.confirm.body",
            fileCount,
            replacementCount,
            if (shadowFixCount > 0) {
                GradleBuddyBundle.message("intention.find.library.to.accessor.confirm.shadow", shadowFixCount)
            } else {
                ""
            },
            unresolvedLine
        )
    }

    private fun buildResultMessage(plan: RewritePlan): String {
        val fileCount = plan.filePlans.size
        val replacementCount = plan.filePlans.sumOf { it.replacements.size }
        val shadowFixCount = plan.filePlans.count { it.hasShadowingLibsDeclaration }
        val extra = buildString {
            if (shadowFixCount > 0) {
                append(GradleBuddyBundle.message("intention.find.library.to.accessor.result.shadow", shadowFixCount))
            }
            if (plan.unresolvedCount > 0) {
                append(GradleBuddyBundle.message("intention.find.library.to.accessor.result.unresolved", plan.unresolvedCount))
            }
        }

        return GradleBuddyBundle.message(
            "intention.find.library.to.accessor.result.body",
            fileCount,
            replacementCount,
            extra
        )
    }

    private data class FilePlan(
        val file: VirtualFile,
        val replacements: List<FindLibraryToCatalogAccessorSupport.Replacement>,
        val hasShadowingLibsDeclaration: Boolean
    )

    private data class RewritePlan(
        val filePlans: List<FilePlan>,
        val unresolvedCount: Int,
        val unresolvedSamples: List<String>
    )

    companion object {
        private const val MAX_UNRESOLVED_SAMPLES = 5
    }
}

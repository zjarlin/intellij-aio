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
 * 将 Gradle Kotlin DSL 中依赖里的 `libs.xxx` 强类型访问器，
 * 批量替换为 `findLibrary("alias").get()`。
 */
class GradleKtsCatalogAccessorToFindLibraryIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.catalog.accessor.dynamic")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(GradleBuddyBundle.message("intention.catalog.accessor.dynamic.preview"))
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null || !CatalogAccessorToFindLibrarySupport.isTargetGradleKtsFile(file)) {
            return false
        }

        val element = file.findElementAt(editor.caretModel.offset)
        if (element != null && CatalogAccessorToFindLibrarySupport.detectTargetReplacement(project, element) != null) {
            return true
        }

        val fileCandidates = CatalogAccessorToFindLibrarySupport.collectFileCandidates(project, file)
        if (fileCandidates.replacements.isNotEmpty()) {
            return true
        }

        return CatalogAccessorToFindLibrarySupport.containsCatalogAccessorText(file.text)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val plan = collectRewritePlan(project)
        if (plan.filePlans.isEmpty()) {
            Messages.showInfoMessage(
                project,
                GradleBuddyBundle.message("intention.catalog.accessor.dynamic.none"),
                GradleBuddyBundle.message("intention.catalog.accessor.dynamic.title")
            )
            return
        }

        val confirm = Messages.showYesNoDialog(
            project,
            buildConfirmMessage(plan),
            GradleBuddyBundle.message("intention.catalog.accessor.dynamic.confirm.title"),
            GradleBuddyBundle.message("intention.catalog.accessor.dynamic.confirm.ok"),
            GradleBuddyBundle.message("intention.catalog.accessor.dynamic.confirm.cancel"),
            Messages.getWarningIcon()
        )
        if (confirm != Messages.YES) {
            return
        }

        applyRewritePlan(project, plan)

        Messages.showInfoMessage(
            project,
            buildResultMessage(plan),
            GradleBuddyBundle.message("intention.catalog.accessor.dynamic.title")
        )
    }

    private fun collectRewritePlan(project: Project): RewritePlan {
        val psiManager = PsiManager.getInstance(project)
        val filePlans = mutableListOf<FilePlan>()
        val unresolved = mutableListOf<String>()

        for (virtualFile in CatalogAccessorToFindLibrarySupport.collectTargetGradleKtsFiles(project)) {
            val psiFile = psiManager.findFile(virtualFile) ?: continue
            val fileCandidates = CatalogAccessorToFindLibrarySupport.collectFileCandidates(project, psiFile)
            unresolved += fileCandidates.unresolved

            val rewritePlan = CatalogAccessorToFindLibrarySupport.buildRewritePlan(psiFile, fileCandidates.replacements)
                ?: continue

            filePlans += FilePlan(
                file = virtualFile,
                variableName = rewritePlan.variableName,
                insertOffset = rewritePlan.insertOffset,
                replacements = rewritePlan.replacements
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

        WriteCommandAction.runWriteCommandAction(project, GradleBuddyBundle.message("intention.catalog.accessor.dynamic.command"), null, {
            val psiDocumentManager = com.intellij.psi.PsiDocumentManager.getInstance(project)

            for (filePlan in plan.filePlans) {
                val document = documentManager.getDocument(filePlan.file) ?: continue
                val rewrittenText = CatalogAccessorToFindLibrarySupport.rewriteText(
                    originalText = document.text,
                    plan = CatalogAccessorToFindLibrarySupport.FileRewritePlan(
                        variableName = filePlan.variableName,
                        insertOffset = filePlan.insertOffset,
                        replacements = filePlan.replacements
                    )
                )
                if (rewrittenText == document.text) {
                    continue
                }
                document.replaceString(0, document.textLength, rewrittenText)
                psiDocumentManager.commitDocument(document)
                documentManager.saveDocument(document)
            }
        })
    }

    private fun buildConfirmMessage(plan: RewritePlan): String {
        val fileCount = plan.filePlans.size
        val replacementCount = plan.filePlans.sumOf { it.replacements.size }
        val libraryCount = replacementCount
        val pluginCount = 0
        val insertCount = plan.filePlans.count { it.insertOffset != null }
        val fallbackVarCount = plan.filePlans.count { it.insertOffset != null && it.variableName != DEFAULT_CATALOG_VAR_NAME }
        val unresolvedLine = if (plan.unresolvedCount > 0) {
            GradleBuddyBundle.message(
                "intention.catalog.accessor.dynamic.confirm.unresolved.line",
                plan.unresolvedCount,
                plan.unresolvedSamples.joinToString(separator = "\n") { "  $it" }
            )
        } else {
            ""
        }

        return GradleBuddyBundle.message(
            "intention.catalog.accessor.dynamic.confirm.body",
            fileCount,
            replacementCount,
            if (libraryCount > 0) GradleBuddyBundle.message("intention.catalog.accessor.dynamic.confirm.library.line", libraryCount) else "",
            if (pluginCount > 0) GradleBuddyBundle.message("intention.catalog.accessor.dynamic.confirm.plugin.line", pluginCount) else "",
            if (insertCount > 0) GradleBuddyBundle.message("intention.catalog.accessor.dynamic.confirm.insert.line", insertCount) else "",
            if (fallbackVarCount > 0) GradleBuddyBundle.message("intention.catalog.accessor.dynamic.confirm.fallback.line", fallbackVarCount) else "",
            unresolvedLine
        )
    }

    private fun buildResultMessage(plan: RewritePlan): String {
        val fileCount = plan.filePlans.size
        val replacementCount = plan.filePlans.sumOf { it.replacements.size }
        val libraryCount = replacementCount
        val pluginCount = 0
        val insertCount = plan.filePlans.count { it.insertOffset != null }
        val fallbackVarCount = plan.filePlans.count { it.insertOffset != null && it.variableName != DEFAULT_CATALOG_VAR_NAME }
        val extraLines = buildString {
            if (libraryCount > 0) {
                append(GradleBuddyBundle.message("intention.catalog.accessor.dynamic.result.library.line", libraryCount))
            }
            if (pluginCount > 0) {
                append(GradleBuddyBundle.message("intention.catalog.accessor.dynamic.result.plugin.line", pluginCount))
            }
            if (insertCount > 0) {
                append(GradleBuddyBundle.message("intention.catalog.accessor.dynamic.result.insert.line", insertCount))
            }
            if (fallbackVarCount > 0) {
                append(GradleBuddyBundle.message("intention.catalog.accessor.dynamic.result.fallback.line", fallbackVarCount))
            }
            if (plan.unresolvedCount > 0) {
                append(GradleBuddyBundle.message("intention.catalog.accessor.dynamic.result.unresolved.line", plan.unresolvedCount))
            }
        }

        return GradleBuddyBundle.message(
            "intention.catalog.accessor.dynamic.result.body",
            fileCount,
            replacementCount,
            extraLines
        )
    }

    private data class FilePlan(
        val file: VirtualFile,
        val variableName: String,
        val insertOffset: Int?,
        val replacements: List<CatalogAccessorToFindLibrarySupport.Replacement>
    )

    private data class RewritePlan(
        val filePlans: List<FilePlan>,
        val unresolvedCount: Int,
        val unresolvedSamples: List<String>
    )

    companion object {
        private const val DEFAULT_CATALOG_VAR_NAME = "libs"
        private const val MAX_UNRESOLVED_SAMPLES = 5
    }
}

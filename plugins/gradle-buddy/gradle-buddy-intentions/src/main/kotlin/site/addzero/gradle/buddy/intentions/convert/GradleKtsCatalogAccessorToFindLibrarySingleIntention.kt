package site.addzero.gradle.buddy.intentions.convert

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

/**
 * 将当前光标所在的 `libs.xxx` 强类型访问器替换为 `findLibrary("alias").get()`。
 */
class GradleKtsCatalogAccessorToFindLibrarySingleIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.catalog.accessor.dynamic.single")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(GradleBuddyBundle.message("intention.catalog.accessor.dynamic.single.preview"))
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null || !CatalogAccessorToFindLibrarySupport.isTargetGradleKtsFile(file)) {
            return false
        }

        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        return CatalogAccessorToFindLibrarySupport.detectTargetReplacement(project, element) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (editor == null) {
            return
        }

        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val replacement = CatalogAccessorToFindLibrarySupport.detectTargetReplacement(project, element) ?: return
        val rewritePlan = CatalogAccessorToFindLibrarySupport.buildRewritePlan(file, listOf(replacement)) ?: return

        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile) ?: return
        val rewrittenText = CatalogAccessorToFindLibrarySupport.rewriteText(document.text, rewritePlan)
        if (rewrittenText == document.text) {
            return
        }

        WriteCommandAction.writeCommandAction(project)
            .withName(GradleBuddyBundle.message("intention.catalog.accessor.dynamic.single.command"))
            .run<Throwable> {
                document.replaceString(0, document.textLength, rewrittenText)
                PsiDocumentManager.getInstance(project).commitDocument(document)
                FileDocumentManager.getInstance().saveDocument(document)
            }
    }
}

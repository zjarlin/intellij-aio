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
 * 将当前光标所在的 `libs.findLibrary("alias").get()` 还原为强类型访问器 `libs.xxx.yyy`。
 */
class GradleKtsFindLibraryToCatalogAccessorSingleIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.find.library.to.accessor.single")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            GradleBuddyBundle.message("intention.find.library.to.accessor.single.preview")
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null || !FindLibraryToCatalogAccessorSupport.isTargetGradleKtsFile(file)) {
            return false
        }

        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val availableAliases = FindLibraryToCatalogAccessorSupport.loadAvailableLibraryAliases(project)
        return FindLibraryToCatalogAccessorSupport.detectTargetReplacement(element, availableAliases) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (editor == null) {
            return
        }

        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val availableAliases = FindLibraryToCatalogAccessorSupport.loadAvailableLibraryAliases(project)
        val replacement = FindLibraryToCatalogAccessorSupport.detectTargetReplacement(element, availableAliases) ?: return

        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile) ?: return
        val rewrittenText = FindLibraryToCatalogAccessorSupport.rewriteText(
            originalText = document.text,
            replacements = listOf(replacement),
            cleanupShadowingDeclaration = FindLibraryToCatalogAccessorSupport.hasShadowingLibsDeclaration(document.text)
        )
        if (rewrittenText == document.text) {
            return
        }

        WriteCommandAction.writeCommandAction(project)
            .withName(GradleBuddyBundle.message("intention.find.library.to.accessor.single.command"))
            .run<Throwable> {
                document.replaceString(0, document.textLength, rewrittenText)
                PsiDocumentManager.getInstance(project).commitDocument(document)
                FileDocumentManager.getInstance().saveDocument(document)
            }
    }
}

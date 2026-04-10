package site.addzero.composebuddy.features.previewplayground

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtNamedFunction
import site.addzero.composebuddy.ComposeBuddyBundle

class PreviewPlaygroundIntention : PsiElementBaseIntentionAction(), DumbAware {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getText(): String = ComposeBuddyBundle.message("intention.generate.quick.preview.playground")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val function = targetFunction(element, editor) ?: return false
        return PreviewPlaygroundAnalysis.analyze(function) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val function = targetFunction(element, editor) ?: return
        val analysis = PreviewPlaygroundAnalysis.analyze(function) ?: return
        PreviewPlaygroundRefactor(project).apply(analysis)
    }

    private fun targetFunction(element: PsiElement, editor: Editor?): KtNamedFunction? {
        targetSelectedFunction(element, editor)?.let { return it }
        return targetCaretFunction(element)
    }

    private fun targetSelectedFunction(element: PsiElement, editor: Editor?): KtNamedFunction? {
        if (editor == null || !editor.selectionModel.hasSelection()) {
            return null
        }
        val file = element.containingFile ?: return null
        val selectionRange = normalizedSelectionRange(editor)
        val selectedFunctions = PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java)
            .filter { selectionRange.contains(it.textRange) }
        return selectedFunctions.singleOrNull()
    }

    private fun targetCaretFunction(element: PsiElement): KtNamedFunction? {
        val function = PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java, false) ?: return null
        val nameIdentifier = function.nameIdentifier ?: return null
        if (!PsiTreeUtil.isAncestor(nameIdentifier, element, false)) {
            return null
        }
        return function
    }

    private fun normalizedSelectionRange(editor: Editor): TextRange {
        val text = editor.document.charsSequence
        var start = editor.selectionModel.selectionStart
        var end = editor.selectionModel.selectionEnd
        while (start < end && text[start].isWhitespace()) {
            start++
        }
        while (end > start && text[end - 1].isWhitespace()) {
            end--
        }
        return TextRange(start, end)
    }
}

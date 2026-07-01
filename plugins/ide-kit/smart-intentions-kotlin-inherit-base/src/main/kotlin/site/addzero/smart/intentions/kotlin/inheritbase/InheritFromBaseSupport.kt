package site.addzero.smart.intentions.kotlin.inheritbase

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtClass
import site.addzero.smart.intentions.core.SmartIntentionsMessages
import site.addzero.smart.intentions.core.SmartPsiWriteSupport

internal object InheritFromBaseSupport {
    fun isApplicable(klass: KtClass, caretOffset: Int): Boolean {
        if (klass.isEnum() || klass.isAnnotation()) {
            return false
        }
        val classHeaderEndOffset = klass.body?.lBrace?.textOffset ?: klass.textRange.endOffset
        return caretOffset <= classHeaderEndOffset
    }

    fun startEditorInput(project: Project, editor: Editor, klass: KtClass): Int? {
        var caretOffset: Int? = null
        SmartPsiWriteSupport.runWriteCommand(project, SmartIntentionsMessages.INHERIT_FROM_BASE) {
            val insertion = resolveEditorInputInsertion(editor, klass)
            editor.document.insertString(insertion.offset, insertion.text)
            PsiDocumentManager.getInstance(project).commitDocument(editor.document)
            caretOffset = insertion.offset + insertion.text.length
        }
        val resolvedOffset = caretOffset ?: return null
        editor.caretModel.moveToOffset(resolvedOffset)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        ApplicationManager.getApplication().invokeLater {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        }
        return resolvedOffset
    }

    private fun resolveEditorInputInsertion(editor: Editor, klass: KtClass): EditorInputInsertion {
        val superTypeEntries = klass.superTypeListEntries
        if (superTypeEntries.isNotEmpty()) {
            val lastEntry = superTypeEntries.last()
            return EditorInputInsertion(lastEntry.textRange.endOffset, ", ")
        }
        val rawOffset = klass.typeConstraintList?.textRange?.startOffset
            ?: klass.body?.lBrace?.textRange?.startOffset
            ?: klass.textRange.endOffset
        val insertionOffset = skipPreviousWhitespace(editor, klass, rawOffset)
        return EditorInputInsertion(insertionOffset, " : ")
    }

    private fun skipPreviousWhitespace(editor: Editor, klass: KtClass, rawOffset: Int): Int {
        val text = editor.document.immutableCharSequence
        val minOffset = klass.textRange.startOffset
        var offset = rawOffset.coerceAtMost(text.length)
        while (offset > minOffset && text[offset - 1].isWhitespace()) {
            offset -= 1
        }
        return offset
    }

    private data class EditorInputInsertion(
        val offset: Int,
        val text: String,
    )
}

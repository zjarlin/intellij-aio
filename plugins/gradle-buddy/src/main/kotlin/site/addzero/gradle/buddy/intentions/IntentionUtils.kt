package site.addzero.gradle.buddy.intentions

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Utility functions for intention actions
 */
object IntentionUtils {

    /**
     * Gets the text of the line containing the given element
     */
    fun getLineText(element: PsiElement): String {
        val file = element.containingFile ?: return ""
        val document = file.viewProvider.document ?: return ""
        val offset = element.textOffset
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        return document.getText(TextRange(lineStart, lineEnd))
    }

    /**
     * Gets the line number for a given offset in a document
     */
    fun getLineNumber(document: com.intellij.openapi.editor.Document, offset: Int): Int {
        return document.getLineNumber(offset)
    }
}
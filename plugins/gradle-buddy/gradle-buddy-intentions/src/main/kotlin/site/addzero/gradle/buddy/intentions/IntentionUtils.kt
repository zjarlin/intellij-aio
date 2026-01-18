package site.addzero.gradle.buddy.intentions

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * 意图操作工具函数
 */
object IntentionUtils {

    /**
     * 获取包含给定元素所在行的文本
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
     * 获取文档中给定偏移量的行号
     */
    fun getLineNumber(document: com.intellij.openapi.editor.Document, offset: Int): Int {
        return document.getLineNumber(offset)
    }
}

package site.addzero.koog.agent

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement

internal object KoogAgentContextCollector {
    fun collect(document: Document, commentLine: Int): KoogAgentContext {
        val beforeStartLine = (commentLine - CONTEXT_LINES).coerceAtLeast(0)
        val beforeEndLine = commentLine
        val afterStartLine = (commentLine + 1).coerceAtMost(document.lineCount)
        val afterEndLine = (commentLine + 1 + CONTEXT_LINES).coerceAtMost(document.lineCount)

        return KoogAgentContext(
            before = lineNumberedTextForLineRange(document, beforeStartLine, beforeEndLine),
            after = lineNumberedTextForLineRange(document, afterStartLine, afterEndLine),
        )
    }

    fun fullFileContent(document: Document): String {
        return lineNumberedTextForLineRange(document, 0, document.lineCount)
    }

    fun focusedContext(
        project: Project,
        document: Document,
        offset: Int,
    ): KoogAgentFocusedContext? {
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return null
        val elementOffset = offset.coerceIn(0, (document.textLength - 1).coerceAtLeast(0))
        val element = psiFile.findElementAt(elementOffset) ?: return null
        val ancestors = generateSequence(element) { current -> current.parent }
            .filter { candidate -> candidate.textRange != null }
            .toList()
        val focusedElement = ancestors.firstOrNull(::isPrimaryFocusedContextElement)
            ?: ancestors.firstOrNull(::isFocusedContextElement)
            ?: return null
        val range = focusedElement.textRange ?: return null
        val startLine = document.getLineNumber(range.startOffset.coerceIn(0, document.textLength))
        val endLine = document.getLineNumber((range.endOffset - 1).coerceIn(0, (document.textLength - 1).coerceAtLeast(0)))
        return KoogAgentFocusedContext(
            kind = focusedElement.javaClass.simpleName,
            startLineNumber = startLine + 1,
            endLineNumber = endLine + 1,
            content = lineNumberedTextForLineRange(document, startLine, endLine + 1),
        )
    }

    private fun isFocusedContextElement(element: PsiElement): Boolean {
        val kind = element.javaClass.simpleName
        return PRIMARY_FOCUSED_ELEMENT_KIND_MARKERS.any { marker -> kind.contains(marker, ignoreCase = true) } ||
            SECONDARY_FOCUSED_ELEMENT_KIND_MARKERS.any { marker -> kind.contains(marker, ignoreCase = true) }
    }

    private fun isPrimaryFocusedContextElement(element: PsiElement): Boolean {
        val kind = element.javaClass.simpleName
        return PRIMARY_FOCUSED_ELEMENT_KIND_MARKERS.any { marker -> kind.contains(marker, ignoreCase = true) }
    }

    private fun lineNumberedTextForLineRange(
        document: Document,
        startLineInclusive: Int,
        endLineExclusive: Int,
    ): String {
        if (startLineInclusive >= endLineExclusive || document.lineCount == 0) {
            return ""
        }
        return buildString {
            for (line in startLineInclusive until endLineExclusive) {
                val lineStart = document.getLineStartOffset(line)
                val lineEnd = document.getLineEndOffset(line)
                append((line + 1).toString().padStart(4, ' '))
                append(" | ")
                append(document.getText(TextRange(lineStart, lineEnd)))
                appendLine()
            }
        }.trimEnd()
    }

    private const val CONTEXT_LINES = 80
    private val PRIMARY_FOCUSED_ELEMENT_KIND_MARKERS = listOf(
        "Function",
        "Method",
        "Constructor",
        "Accessor",
        "Lambda",
    )
    private val SECONDARY_FOCUSED_ELEMENT_KIND_MARKERS = listOf(
        "Block",
        "Property",
        "Class",
        "Object",
    )
}

internal data class KoogAgentContext(
    val before: String,
    val after: String,
)

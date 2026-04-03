package site.addzero.composeblocks.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.SeparatorPlacement
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import site.addzero.composeblocks.model.ComposeBlockKind
import site.addzero.composeblocks.model.ComposeBlockNode
import java.awt.Color

internal class ComposeBlockDecorationController(
    private val document: Document,
) : Disposable {

    private val highlighters = mutableListOf<RangeHighlighter>()

    fun apply(
        editor: EditorEx,
        selectedNode: ComposeBlockNode?,
        parentNode: ComposeBlockNode?,
        selectedPath: List<ComposeBlockNode>,
        caretOffset: Int,
        semanticRanges: List<ComposeInlineSemanticRange> = emptyList(),
    ) {
        clear()
        if (selectedNode == null) {
            return
        }

        parentNode?.let { node ->
            addRangeBackground(
                editor = editor,
                startOffset = node.renderRange.startOffset,
                endOffset = node.renderRange.endOffset,
                color = backgroundColor(node.kind, 24),
            )
        }

        addRangeBackground(
            editor = editor,
            startOffset = selectedNode.renderRange.startOffset,
            endOffset = selectedNode.renderRange.endOffset,
            color = backgroundColor(selectedNode.kind, 38),
        )
        addGuideBars(
            editor = editor,
            path = selectedPath,
            selectedNode = selectedNode,
        )

        addLineBackground(
            editor = editor,
            line = safeLineNumberForOffset(caretOffset),
            color = backgroundColor(selectedNode.kind, 54),
        )

        val selectedRange = clampRange(
            startOffset = selectedNode.renderRange.startOffset,
            endOffset = selectedNode.renderRange.endOffset,
        ) ?: return
        val selectedStartLine = safeLineNumberForOffset(selectedRange.first)
        val selectedEndLine = safeLineNumberForOffset(
            (selectedRange.second - 1).coerceAtLeast(selectedRange.first),
        )
        addLineBackground(
            editor = editor,
            line = selectedStartLine,
            color = backgroundColor(selectedNode.kind, 68),
        )
        addBoundaryLine(
            editor = editor,
            line = selectedStartLine,
            color = backgroundColor(selectedNode.kind, 148),
            placement = SeparatorPlacement.TOP,
        )
        if (selectedEndLine != selectedStartLine) {
            addLineBackground(
                editor = editor,
                line = selectedEndLine,
                color = backgroundColor(selectedNode.kind, 62),
            )
        }
        addBoundaryLine(
            editor = editor,
            line = selectedEndLine,
            color = backgroundColor(selectedNode.kind, 132),
            placement = SeparatorPlacement.BOTTOM,
        )

        semanticRanges.forEach { range ->
            addSemanticRangeBackground(
                editor = editor,
                startOffset = range.startOffset,
                endOffset = range.endOffset,
                color = range.color,
            )
        }
    }

    fun clear() {
        highlighters.forEach(RangeHighlighter::dispose)
        highlighters.clear()
    }

    override fun dispose() {
        clear()
    }

    private fun addRangeBackground(
        editor: EditorEx,
        startOffset: Int,
        endOffset: Int,
        color: Color,
    ) {
        val safeRange = clampRange(startOffset, endOffset) ?: return
        if (safeRange.first >= safeRange.second) {
            return
        }

        highlighters += editor.markupModel.addRangeHighlighter(
            safeRange.first,
            safeRange.second,
            HighlighterLayer.SELECTION - 10,
            TextAttributes().apply {
                backgroundColor = color
            },
            HighlighterTargetArea.EXACT_RANGE,
        )
    }

    private fun addLineBackground(
        editor: EditorEx,
        line: Int,
        color: Color,
    ) {
        if (document.lineCount <= 0) {
            return
        }
        val safeLine = line.coerceIn(0, document.lineCount - 1)
        val startOffset = document.getLineStartOffset(safeLine)
        val endOffset = document.getLineEndOffset(safeLine)
        highlighters += editor.markupModel.addRangeHighlighter(
            startOffset,
            endOffset,
            HighlighterLayer.CARET_ROW - 1,
            TextAttributes().apply {
                backgroundColor = color
                fontType = EditorFontType.PLAIN.ordinal
            },
            HighlighterTargetArea.EXACT_RANGE,
        )
    }

    private fun addSemanticRangeBackground(
        editor: EditorEx,
        startOffset: Int,
        endOffset: Int,
        color: Color,
    ) {
        val safeRange = clampRange(startOffset, endOffset) ?: return
        highlighters += editor.markupModel.addRangeHighlighter(
            safeRange.first,
            safeRange.second,
            HighlighterLayer.SELECTION - 6,
            TextAttributes().apply {
                backgroundColor = color
            },
            HighlighterTargetArea.EXACT_RANGE,
        )
    }

    private fun addBoundaryLine(
        editor: EditorEx,
        line: Int,
        color: Color,
        placement: SeparatorPlacement,
    ) {
        if (document.lineCount <= 0) {
            return
        }
        val safeLine = line.coerceIn(0, document.lineCount - 1)
        val lineStartOffset = document.getLineStartOffset(safeLine)
        val lineEndOffset = document.getLineEndOffset(safeLine)
        val highlighter = editor.markupModel.addRangeHighlighter(
            lineStartOffset,
            lineEndOffset,
            HighlighterLayer.SELECTION - 8,
            null,
            HighlighterTargetArea.LINES_IN_RANGE,
        )
        highlighter.lineSeparatorColor = color
        highlighter.lineSeparatorPlacement = placement
        highlighters += highlighter
    }

    private fun addGuideBars(
        editor: EditorEx,
        path: List<ComposeBlockNode>,
        selectedNode: ComposeBlockNode,
    ) {
        return
    }

    private fun clampRange(
        startOffset: Int,
        endOffset: Int,
    ): Pair<Int, Int>? {
        if (document.textLength <= 0) {
            return null
        }
        val safeStart = startOffset.coerceIn(0, document.textLength)
        val safeEnd = endOffset.coerceIn(0, document.textLength)
        if (safeStart >= safeEnd) {
            return null
        }
        return safeStart to safeEnd
    }

    private fun safeLineNumberForOffset(offset: Int): Int {
        if (document.lineCount <= 0) {
            return 0
        }
        if (document.textLength <= 0) {
            return 0
        }
        return document.getLineNumber(
            offset.coerceIn(0, document.textLength - 1),
        )
    }

    private fun backgroundColor(
        kind: ComposeBlockKind,
        alpha: Int,
    ): Color {
        val base = when (kind) {
            ComposeBlockKind.ROOT -> JBColor(Color(75, 122, 196), Color(117, 158, 222))
            ComposeBlockKind.CONTAINER -> JBColor(Color(60, 136, 114), Color(104, 178, 154))
            ComposeBlockKind.LEAF -> JBColor(Color(118, 126, 138), Color(156, 165, 176))
            ComposeBlockKind.SHELL -> JBColor(Color(175, 132, 60), Color(222, 182, 103))
        }
        return Color(base.red, base.green, base.blue, alpha)
    }

}

internal data class ComposeInlineSemanticRange(
    val startOffset: Int,
    val endOffset: Int,
    val color: Color,
)

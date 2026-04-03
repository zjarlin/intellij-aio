package site.addzero.composeblocks.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.SeparatorPlacement
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import site.addzero.composeblocks.model.ComposeBlockKind
import site.addzero.composeblocks.model.ComposeBlockNode
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

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
            line = document.getLineNumber(caretOffset.coerceIn(0, document.textLength)),
            color = backgroundColor(selectedNode.kind, 54),
        )

        val selectedStartLine = document.getLineNumber(selectedNode.renderRange.startOffset)
        val selectedEndLine = document.getLineNumber((selectedNode.renderRange.endOffset - 1).coerceAtLeast(selectedNode.renderRange.startOffset))
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
        if (startOffset >= endOffset) {
            return
        }

        highlighters += editor.markupModel.addRangeHighlighter(
            startOffset,
            endOffset,
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
        if (startOffset >= endOffset) {
            return
        }
        highlighters += editor.markupModel.addRangeHighlighter(
            startOffset,
            endOffset,
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
        path.forEachIndexed { index, node ->
            val highlighter = editor.markupModel.addRangeHighlighter(
                node.renderRange.startOffset,
                node.renderRange.endOffset,
                HighlighterLayer.SELECTION - 12,
                null,
                HighlighterTargetArea.LINES_IN_RANGE,
            )
            highlighter.lineMarkerRenderer = GuideBarRenderer(
                editor = editor,
                depth = index,
                color = backgroundColor(node.kind, if (node.id == selectedNode.id) 216 else 156),
                rangeStart = node.renderRange.startOffset,
                rangeEnd = node.renderRange.endOffset,
            )
            highlighters += highlighter
        }
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

    private inner class GuideBarRenderer(
        private val editor: EditorEx,
        private val depth: Int,
        private val color: Color,
        private val rangeStart: Int,
        private val rangeEnd: Int,
    ) : LineMarkerRenderer {
        override fun paint(
            rawEditor: com.intellij.openapi.editor.Editor,
            graphics: Graphics,
            rectangle: Rectangle,
        ) {
            val safeEndOffset = (rangeEnd - 1).coerceAtLeast(rangeStart)
            val startLine = document.getLineNumber(rangeStart)
            val endLine = document.getLineNumber(safeEndOffset)
            val startLineOffset = document.getLineStartOffset(startLine)
            val endLineOffset = document.getLineStartOffset(endLine)
            val startY = rawEditor.offsetToXY(startLineOffset).y
            val endY = rawEditor.offsetToXY(endLineOffset).y + rawEditor.lineHeight
            val x = editor.gutterComponentEx.whitespaceSeparatorOffset + 8 + depth * 10
            graphics.color = color
            graphics.fillRoundRect(x, startY + 1, 5, maxOf(rawEditor.lineHeight / 2, endY - startY - 2), 5, 5)
        }
    }
}

internal data class ComposeInlineSemanticRange(
    val startOffset: Int,
    val endOffset: Int,
    val color: Color,
)

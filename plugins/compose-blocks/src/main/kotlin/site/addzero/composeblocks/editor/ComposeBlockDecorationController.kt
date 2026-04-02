package site.addzero.composeblocks.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
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
        caretOffset: Int,
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
        if (selectedEndLine != selectedStartLine) {
            addLineBackground(
                editor = editor,
                line = selectedEndLine,
                color = backgroundColor(selectedNode.kind, 62),
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

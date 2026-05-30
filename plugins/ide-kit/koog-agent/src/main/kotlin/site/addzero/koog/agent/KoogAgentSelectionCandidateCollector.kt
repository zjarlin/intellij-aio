package site.addzero.koog.agent

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement

internal object KoogAgentSelectionCandidateCollector {
    fun collect(
        project: Project,
        document: Document,
        triggerOffset: Int,
    ): List<KoogAgentSelectionCandidate> {
        if (document.textLength == 0 || document.lineCount == 0) {
            return emptyList()
        }

        val instructionLine = findInstructionLine(document, triggerOffset)
        val accumulator = CandidateAccumulator(document, instructionLine)
        addLineCandidates(document, instructionLine, accumulator)

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
            ?: return accumulator.resultWithFullFile()
        for (offset in candidateOffsets(document, instructionLine, triggerOffset)) {
            val element = psiFile.findElementAt(offset) ?: continue
            generateSequence(element) { current -> current.parent }
                .takeWhile { candidate -> candidate != psiFile }
                .filter(::isSelectablePsiElement)
                .forEach { candidate ->
                    val range = candidate.textRange ?: return@forEach
                    accumulator.add(range, candidate.javaClass.simpleName)
                }
        }

        return accumulator.resultWithFullFile()
    }

    private fun findInstructionLine(
        document: Document,
        triggerOffset: Int,
    ): Int {
        val offset = triggerOffset.coerceIn(0, document.textLength)
        val caretLine = document.getLineNumber((offset - 1).coerceAtLeast(0))
        return sequenceOf(caretLine, caretLine - 1)
            .filter { line -> line in 0 until document.lineCount }
            .firstOrNull { line -> KoogAgentCommentParser.extractInstruction(lineText(document, line)) != null }
            ?: caretLine
    }

    private fun addLineCandidates(
        document: Document,
        instructionLine: Int,
        accumulator: CandidateAccumulator,
    ) {
        val firstCodeLine = firstNonBlankLineAfter(document, instructionLine) ?: return
        accumulator.add(lineRange(document, firstCodeLine, firstCodeLine), "下方当前行")

        val contiguousEndLine = contiguousCodeEndLine(document, firstCodeLine)
        if (contiguousEndLine > firstCodeLine) {
            accumulator.add(lineRange(document, firstCodeLine, contiguousEndLine), "下方连续代码")
        }
    }

    private fun candidateOffsets(
        document: Document,
        instructionLine: Int,
        triggerOffset: Int,
    ): List<Int> {
        val offsets = LinkedHashSet<Int>()
        addOffsetIfValid(document, offsets, triggerOffset)
        firstNonWhitespaceOffsetAfterInstruction(document, instructionLine)?.let { offset ->
            addOffsetIfValid(document, offsets, offset)
        }
        val endLine = (instructionLine + LOOKAHEAD_LINES).coerceAtMost(document.lineCount - 1)
        for (line in (instructionLine + 1)..endLine) {
            firstNonWhitespaceOffsetOnLine(document, line)?.let { offset ->
                addOffsetIfValid(document, offsets, offset)
            }
        }
        return offsets.toList()
    }

    private fun addOffsetIfValid(
        document: Document,
        offsets: MutableSet<Int>,
        offset: Int,
    ) {
        if (offset in 0 until document.textLength) {
            offsets.add(offset)
        }
    }

    private fun firstNonWhitespaceOffsetAfterInstruction(
        document: Document,
        instructionLine: Int,
    ): Int? {
        var offset = document.getLineEndOffset(instructionLine).coerceIn(0, document.textLength)
        while (offset < document.textLength) {
            if (!document.charsSequence[offset].isWhitespace()) {
                return offset
            }
            offset++
        }
        return null
    }

    private fun firstNonBlankLineAfter(
        document: Document,
        instructionLine: Int,
    ): Int? {
        for (line in (instructionLine + 1) until document.lineCount) {
            if (lineText(document, line).isNotBlank()) {
                return line
            }
        }
        return null
    }

    private fun firstNonWhitespaceOffsetOnLine(
        document: Document,
        line: Int,
    ): Int? {
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        for (offset in lineStart until lineEnd) {
            if (!document.charsSequence[offset].isWhitespace()) {
                return offset
            }
        }
        return null
    }

    private fun contiguousCodeEndLine(
        document: Document,
        firstLine: Int,
    ): Int {
        var endLine = firstLine
        var bracketBalance = 0
        for (line in firstLine until document.lineCount) {
            val text = lineText(document, line).trim()
            if (line > firstLine && text.isBlank() && bracketBalance <= 0) {
                break
            }
            if (line > firstLine && bracketBalance <= 0 && text in CLOSING_DELIMITERS) {
                break
            }

            endLine = line
            bracketBalance += bracketDelta(text)
        }
        return endLine
    }

    private fun bracketDelta(text: String): Int {
        return text.count { char -> char == '{' || char == '(' || char == '[' } -
            text.count { char -> char == '}' || char == ')' || char == ']' }
    }

    private fun lineRange(
        document: Document,
        startLine: Int,
        endLine: Int,
    ): TextRange {
        return TextRange(
            document.getLineStartOffset(startLine),
            document.getLineEndOffset(endLine),
        )
    }

    private fun isSelectablePsiElement(element: PsiElement): Boolean {
        val range = element.textRange ?: return false
        if (range.isEmpty) {
            return false
        }
        val kind = element.javaClass.simpleName
        if (EXCLUDED_KIND_MARKERS.any { marker -> kind.contains(marker, ignoreCase = true) }) {
            return false
        }
        return SELECTABLE_KIND_MARKERS.any { marker -> kind.contains(marker, ignoreCase = true) }
    }

    private fun lineText(
        document: Document,
        line: Int,
    ): String {
        return document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
    }

    private class CandidateAccumulator(
        private val document: Document,
        private val instructionLine: Int,
    ) {
        private val candidates = LinkedHashMap<String, KoogAgentSelectionCandidate>()

        fun add(
            range: TextRange,
            kind: String,
        ) {
            if (range.startOffset < 0 || range.endOffset > document.textLength || range.startOffset >= range.endOffset) {
                return
            }

            val startLine = document.getLineNumber(range.startOffset.coerceIn(0, document.textLength))
            val endLine = document.getLineNumber((range.endOffset - 1).coerceIn(0, (document.textLength - 1).coerceAtLeast(0)))
            if (endLine < instructionLine) {
                return
            }

            val expandedRange = lineRange(document, startLine, endLine)
            val rawText = document.getText(expandedRange)
            if (rawText.isBlank()) {
                return
            }

            val key = "${expandedRange.startOffset}:${expandedRange.endOffset}"
            candidates.putIfAbsent(
                key,
                KoogAgentSelectionCandidate(
                    startOffset = expandedRange.startOffset,
                    endOffset = expandedRange.endOffset,
                    startLineNumber = startLine + 1,
                    endLineNumber = endLine + 1,
                    kind = normalizeKind(kind),
                    preview = preview(rawText),
                ),
            )
        }

        fun result(): List<KoogAgentSelectionCandidate> {
            return candidates.values.toList()
        }

        fun resultWithFullFile(): List<KoogAgentSelectionCandidate> {
            val result = candidates.values
                .filterNot { candidate -> candidate.startOffset == 0 && candidate.endOffset == document.textLength }
                .take(MAX_NEARBY_CANDIDATES)
                .toMutableList()
            val fullFileRange = fullFileTextRange() ?: return result
            val rawText = document.getText(fullFileRange)
            result += KoogAgentSelectionCandidate(
                startOffset = fullFileRange.startOffset,
                endOffset = fullFileRange.endOffset,
                startLineNumber = document.getLineNumber(fullFileRange.startOffset) + 1,
                endLineNumber = document.getLineNumber(fullFileRange.endOffset - 1) + 1,
                kind = "整个文件",
                preview = preview(rawText),
            )
            return result
        }

        private fun normalizeKind(kind: String): String {
            return kind
                .removeSuffix("Impl")
                .removePrefix("Kt")
                .removePrefix("Psi")
        }

        private fun preview(text: String): String {
            val compact = text.lineSequence()
                .map { line -> line.trim() }
                .firstOrNull { line -> line.isNotBlank() }
                ?.replace(Regex("\\s+"), " ")
                .orEmpty()
            return compact.take(PREVIEW_MAX_LENGTH)
        }

        private fun fullFileTextRange(): TextRange? {
            val startOffset = document.charsSequence.indexOfFirst { char -> !char.isWhitespace() }
            if (startOffset == -1) {
                return null
            }
            val endOffset = document.charsSequence.indexOfLast { char -> !char.isWhitespace() } + 1
            return TextRange(startOffset, endOffset)
        }
    }

    private val CLOSING_DELIMITERS = setOf("}", ")", "]")
    private val SELECTABLE_KIND_MARKERS = listOf(
        "ArgumentList",
        "ValueArgument",
        "Call",
        "ExpressionList",
        "ExpressionStatement",
        "Statement",
        "Block",
        "Lambda",
        "Property",
        "Function",
        "Method",
        "Constructor",
        "Accessor",
        "Class",
        "Object",
    )
    private val EXCLUDED_KIND_MARKERS = listOf(
        "WhiteSpace",
        "Comment",
        "Identifier",
        "NameReference",
        "ReferenceExpression",
    )
    private const val LOOKAHEAD_LINES = 20
    private const val MAX_NEARBY_CANDIDATES = 11
    private const val PREVIEW_MAX_LENGTH = 90
}

internal data class KoogAgentSelectionCandidate(
    val startOffset: Int,
    val endOffset: Int,
    val startLineNumber: Int,
    val endLineNumber: Int,
    val kind: String,
    val preview: String,
) {
    override fun toString(): String {
        val lines = if (startLineNumber == endLineNumber) {
            "第 $startLineNumber 行"
        } else {
            "第 $startLineNumber-$endLineNumber 行"
        }
        return "$lines · $kind · $preview"
    }
}

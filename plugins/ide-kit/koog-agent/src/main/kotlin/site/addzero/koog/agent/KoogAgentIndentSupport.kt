package site.addzero.koog.agent

internal object KoogAgentIndentSupport {
    fun alignToCommentIndent(
        code: String,
        commentIndent: String,
    ): String {
        return alignToIndent(code, commentIndent)
    }

    fun alignToIndent(
        code: String,
        targetIndent: String,
    ): String {
        val trimmed = code.trim('\n', '\r')
        if (targetIndent.isBlank() || trimmed.isBlank()) {
            return trimmed
        }
        val nonBlankLines = trimmed.lineSequence().filter { it.isNotBlank() }.toList()
        if (nonBlankLines.isEmpty()) {
            return trimmed
        }
        val minimumIndent = nonBlankLines.minOf { line -> line.takeWhile { it == ' ' || it == '\t' }.length }
        if (minimumIndent > 0) {
            return trimmed
        }
        return trimmed.lineSequence()
            .joinToString("\n") { line ->
                if (line.isBlank()) {
                    line
                } else {
                    targetIndent + line
                }
            }
    }

    fun leadingIndentOf(text: String): String {
        return text.lineSequence()
            .firstOrNull { line -> line.isNotBlank() }
            ?.takeWhile { char -> char == ' ' || char == '\t' }
            .orEmpty()
    }
}

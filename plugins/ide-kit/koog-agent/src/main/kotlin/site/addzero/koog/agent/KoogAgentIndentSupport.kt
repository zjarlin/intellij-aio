package site.addzero.koog.agent

internal object KoogAgentIndentSupport {
    fun alignToCommentIndent(
        code: String,
        commentIndent: String,
    ): String {
        val trimmed = code.trim('\n', '\r')
        if (commentIndent.isBlank() || trimmed.isBlank()) {
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
                    commentIndent + line
                }
            }
    }
}

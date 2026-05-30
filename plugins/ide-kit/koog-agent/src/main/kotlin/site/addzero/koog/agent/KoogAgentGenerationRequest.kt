package site.addzero.koog.agent

data class KoogAgentGenerationRequest(
    val filePath: String,
    val language: String,
    val instruction: String,
    val contextScope: KoogAgentContextScope,
    val caretLineNumber: Int,
    val commentLineNumber: Int,
    val insertionLineNumber: Int,
    val fullFileContent: String?,
    val selectedContext: KoogAgentSelectedContext?,
    val focusedContext: KoogAgentFocusedContext?,
    val beforeContext: String,
    val afterContext: String,
    val insertOffset: Int,
    val leadingIndent: String,
    val key: String,
)

enum class KoogAgentContextScope(val displayName: String) {
    NEARBY("nearby context"),
    SELECTION("current selection"),
    FULL_FILE("full file"),
}

data class KoogAgentSelectedContext(
    val startOffset: Int,
    val endOffset: Int,
    val startLineNumber: Int,
    val endLineNumber: Int,
    val rawContent: String,
    val content: String,
)

data class KoogAgentFocusedContext(
    val kind: String,
    val startOffset: Int,
    val endOffset: Int,
    val startLineNumber: Int,
    val endLineNumber: Int,
    val rawContent: String,
    val content: String,
)

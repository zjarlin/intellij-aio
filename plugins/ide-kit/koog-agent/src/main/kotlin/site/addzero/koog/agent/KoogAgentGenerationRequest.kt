package site.addzero.koog.agent

data class KoogAgentGenerationRequest(
    val filePath: String,
    val language: String,
    val instruction: String,
    val caretLineNumber: Int,
    val commentLineNumber: Int,
    val insertionLineNumber: Int,
    val fullFileContent: String,
    val focusedContext: KoogAgentFocusedContext?,
    val beforeContext: String,
    val afterContext: String,
    val insertOffset: Int,
    val leadingIndent: String,
    val key: String,
)

data class KoogAgentFocusedContext(
    val kind: String,
    val startLineNumber: Int,
    val endLineNumber: Int,
    val content: String,
)

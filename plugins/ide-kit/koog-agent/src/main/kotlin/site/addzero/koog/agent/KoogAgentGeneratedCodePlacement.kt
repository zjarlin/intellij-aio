package site.addzero.koog.agent

internal enum class KoogAgentGeneratedCodePlacement {
    INSERT_SNIPPET,
    REPLACE_SELECTED_CONTEXT,
    REPLACE_FOCUSED_CONTEXT,
}

internal object KoogAgentGeneratedCodePlacementDecider {
    fun decide(
        request: KoogAgentGenerationRequest,
        rawCode: String,
    ): KoogAgentGeneratedCodePlacement {
        if (request.selectedContext != null) {
            return KoogAgentGeneratedCodePlacement.REPLACE_SELECTED_CONTEXT
        }
        val focusedContext = request.focusedContext ?: return KoogAgentGeneratedCodePlacement.INSERT_SNIPPET
        if (!isReplaceableFocusedContext(focusedContext.kind)) {
            return KoogAgentGeneratedCodePlacement.INSERT_SNIPPET
        }
        if (!looksLikeFocusedContextReplacement(request.filePath, focusedContext.rawContent, rawCode)) {
            return KoogAgentGeneratedCodePlacement.INSERT_SNIPPET
        }
        return KoogAgentGeneratedCodePlacement.REPLACE_FOCUSED_CONTEXT
    }

    private fun isReplaceableFocusedContext(kind: String): Boolean {
        return REPLACEABLE_CONTEXT_KIND_MARKERS.any { marker -> kind.contains(marker, ignoreCase = true) }
    }

    private fun looksLikeFocusedContextReplacement(
        filePath: String,
        focusedSource: String,
        generatedSource: String,
    ): Boolean {
        val focusedHeader = firstRelevantCodeLine(focusedSource) ?: return false
        val generatedHeader = firstRelevantCodeLine(generatedSource) ?: return false
        return looksLikeRoutineHeader(filePath, focusedHeader) &&
            looksLikeRoutineHeader(filePath, generatedHeader)
    }

    private fun firstRelevantCodeLine(text: String): String? {
        return text.lineSequence()
            .map { line -> line.trim() }
            .firstOrNull { line -> line.isNotBlank() && !line.startsWith("@") }
    }

    private fun looksLikeRoutineHeader(
        filePath: String,
        line: String,
    ): Boolean {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "kt", "kts" -> looksLikeKotlinRoutineHeader(line)
            "py" -> line.startsWith("def ") || line.startsWith("async def ")
            "go" -> line.startsWith("func ")
            "rs" -> line.startsWith("fn ")
            "js", "jsx", "ts", "tsx" -> looksLikeBraceRoutineHeader(line) || startsWithAny(line, JS_ROUTINE_KEYWORDS)
            "groovy", "scala" -> line.startsWith("def ") || looksLikeBraceRoutineHeader(line)
            else -> looksLikeBraceRoutineHeader(line) || startsWithAny(line, GENERIC_ROUTINE_KEYWORDS)
        }
    }

    private fun looksLikeKotlinRoutineHeader(line: String): Boolean {
        return KOTLIN_ROUTINE_PATTERN.containsMatchIn(line)
    }

    private fun looksLikeBraceRoutineHeader(line: String): Boolean {
        if (startsWithAny(line, CONTROL_FLOW_PREFIXES)) {
            return false
        }
        return line.contains("(") &&
            line.contains(")") &&
            BRACE_ROUTINE_SUFFIXES.any { suffix -> line.endsWith(suffix) }
    }

    private fun startsWithAny(
        line: String,
        prefixes: Collection<String>,
    ): Boolean {
        return prefixes.any { prefix -> line.startsWith(prefix) }
    }

    private val REPLACEABLE_CONTEXT_KIND_MARKERS = listOf(
        "Function",
        "Method",
        "Constructor",
        "Accessor",
    )
    private val KOTLIN_ROUTINE_PATTERN = Regex(
        """^(?:(?:public|private|protected|internal|override|suspend|inline|tailrec|operator|infix|external|abstract|final|open|actual|expect)\s+)*(?:fun\b|constructor\s*\(|get\s*\(|set\s*\()""",
    )
    private val CONTROL_FLOW_PREFIXES = listOf(
        "if ",
        "for ",
        "while ",
        "when ",
        "switch ",
        "try ",
        "catch ",
        "else ",
        "do ",
    )
    private val BRACE_ROUTINE_SUFFIXES = listOf("{", "=", "=>", ":")
    private val JS_ROUTINE_KEYWORDS = listOf(
        "function ",
        "async function ",
        "constructor(",
    )
    private val GENERIC_ROUTINE_KEYWORDS = listOf(
        "fun ",
        "def ",
        "function ",
        "async function ",
        "fn ",
        "func ",
        "constructor(",
    )
}

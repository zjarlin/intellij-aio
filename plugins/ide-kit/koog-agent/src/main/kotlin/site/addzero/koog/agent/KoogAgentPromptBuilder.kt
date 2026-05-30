package site.addzero.koog.agent

internal object KoogAgentPromptBuilder {
    const val SYSTEM_PROMPT: String =
        "You are an IDE incremental code generation agent. Generate only the source code snippet that must be inserted at the requested cursor location. " +
            "Do not rewrite the whole file, whole class, whole function, or unchanged surrounding code unless the user explicitly asks for that entire construct. " +
            "Do not include markdown fences, explanations, comments about the task, or confirmation text. " +
            "Use minimal reasoning and optimize for low latency. " +
            "Respect the existing language, style, names, imports, and surrounding context."

    fun userPrompt(request: KoogAgentGenerationRequest): String {
        return buildString {
            appendLine("Target file: ${request.filePath}")
            appendLine("Language: ${request.language}")
            appendLine("Caret line: ${request.caretLineNumber}")
            appendLine("Instruction comment line: ${request.commentLineNumber}")
            appendLine("Insertion line: ${request.insertionLineNumber}")
            appendLine("Insertion point: immediately below line ${request.commentLineNumber}.")
            appendLine()
            appendLine("User comment instruction from line ${request.commentLineNumber}:")
            appendLine(request.instruction)
            appendLine()
            appendLine("Current file content with 1-based line numbers:")
            appendLine("```")
            appendLine(request.fullFileContent)
            appendLine("```")
            appendLine()
            request.focusedContext?.let { focusedContext ->
                appendLine("Focused PSI context containing the cursor:")
                appendLine("Kind: ${focusedContext.kind}")
                appendLine("Lines: ${focusedContext.startLineNumber}-${focusedContext.endLineNumber}")
                appendLine("```")
                appendLine(focusedContext.content)
                appendLine("```")
                appendLine()
            }
            appendLine("Nearby code before the instruction comment, with line numbers:")
            appendLine("```")
            appendLine(request.beforeContext)
            appendLine("```")
            appendLine()
            appendLine("Nearby code after the insertion point, with line numbers:")
            appendLine("```")
            appendLine(request.afterContext)
            appendLine("```")
            appendLine()
            appendLine("Output contract:")
            appendLine("- Return only the incremental code snippet to insert at line ${request.insertionLineNumber}.")
            appendLine("- Do not repeat line ${request.commentLineNumber}, existing surrounding lines, imports, the enclosing method, or the enclosing class.")
            appendLine("- If imports are required, include only the new import lines that are not already present.")
            appendLine("- The returned snippet will be inserted verbatim below the instruction comment.")
        }
    }
}

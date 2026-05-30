package site.addzero.koog.agent

internal object KoogAgentPromptBuilder {
    const val SYSTEM_PROMPT: String =
        "You are an IDE incremental code generation agent. Return only source code. " +
            "Prefer the smallest code fragment that should be inserted at the requested cursor location. " +
            "If the focused method/function/constructor/accessor itself must be regenerated, return only that complete focused context replacement. " +
            "Do not rewrite the whole file, whole class, or unchanged surrounding code unless the user explicitly asks for that entire construct. " +
            "Do not include markdown fences, explanations, comments about the task, or confirmation text. " +
            "Use minimal reasoning and optimize for low latency. " +
            "Respect the existing language, style, names, imports, and surrounding context."

    fun userPrompt(request: KoogAgentGenerationRequest): String {
        return buildString {
            appendLine("Target file: ${request.filePath}")
            appendLine("Language: ${request.language}")
            appendLine("Cursor line number: ${request.caretLineNumber}")
            appendLine("Instruction comment line number: ${request.commentLineNumber}")
            appendLine("Default insertion line number: ${request.insertionLineNumber}")
            appendLine("Insertion point: immediately below line ${request.commentLineNumber}.")
            appendLine("Context scope selected by the user: ${request.contextScope.displayName}.")
            appendLine()
            appendLine("User comment instruction from line ${request.commentLineNumber}:")
            appendLine(request.instruction)
            appendLine()
            request.fullFileContent?.let { fullFileContent ->
                appendLine("Current file content with 1-based line numbers:")
                appendLine("```")
                appendLine(fullFileContent)
                appendLine("```")
                appendLine()
            } ?: run {
                appendLine("Full file content was omitted because the user selected ${request.contextScope.displayName}.")
                appendLine()
            }
            request.selectedContext?.let { selectedContext ->
                appendLine("Selected region chosen in the editor or ide-kit range selector and to be replaced verbatim:")
                appendLine("Lines: ${selectedContext.startLineNumber}-${selectedContext.endLineNumber}")
                appendLine("Character offsets: ${selectedContext.startOffset}-${selectedContext.endOffset}")
                appendLine("Raw selected region source:")
                appendLine("```")
                appendLine(selectedContext.rawContent)
                appendLine("```")
                appendLine("Selected region source with 1-based line numbers:")
                appendLine("```")
                appendLine(selectedContext.content)
                appendLine("```")
                appendLine()
            }
            request.focusedContext?.let { focusedContext ->
                appendLine("Focused PSI context containing the cursor:")
                appendLine("Kind: ${focusedContext.kind}")
                appendLine("Character offsets: ${focusedContext.startOffset}-${focusedContext.endOffset}")
                appendLine("Lines: ${focusedContext.startLineNumber}-${focusedContext.endLineNumber}")
                appendLine("Raw focused context source:")
                appendLine("```")
                appendLine(focusedContext.rawContent)
                appendLine("```")
                appendLine("Focused context source with 1-based line numbers:")
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
            appendLine("- Default: return only the incremental snippet to insert at line ${request.insertionLineNumber}.")
            appendLine("- If a selected region is present above, return the replacement for that selected region only.")
            appendLine("- If a full focused-context rewrite is necessary, return only the complete replacement for the focused method/function/constructor/accessor shown above.")
            appendLine("- Do not return both an insertion snippet and a full replacement.")
            appendLine("- Do not repeat line ${request.commentLineNumber}, existing surrounding lines, the enclosing class, or the whole file.")
            appendLine("- If imports are required, include only the new import lines that are not already present.")
            appendLine("- The returned code is applied automatically: selected regions are replaced directly; declaration-shaped focused-context output replaces that context; otherwise it is inserted below the instruction comment.")
        }
    }
}

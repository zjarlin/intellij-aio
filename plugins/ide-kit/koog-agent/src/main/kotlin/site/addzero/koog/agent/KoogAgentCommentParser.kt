package site.addzero.koog.agent

internal object KoogAgentCommentParser {
    fun extractInstruction(lineText: String): String? {
        val trimmed = lineText.trim()
        val commentBody = when {
            trimmed.startsWith("///") -> trimmed.removePrefix("///")
            trimmed.startsWith("//") -> trimmed.removePrefix("//")
            trimmed.startsWith("#") -> trimmed.removePrefix("#")
            trimmed.startsWith("--") -> trimmed.removePrefix("--")
            trimmed.startsWith("/*") -> trimmed
                .removePrefix("/*")
                .removePrefix("*")
                .removeSuffix("*/")

            trimmed.startsWith("*") -> trimmed.removePrefix("*").removeSuffix("/")
            trimmed.startsWith("<!--") -> trimmed.removePrefix("<!--").removeSuffix("-->")
            else -> return null
        }.trim()

        val instruction = stripOptionalTrigger(commentBody)
        if (instruction.length < 4) {
            return null
        }
        if (instruction.startsWith("koog-agent generated", ignoreCase = true)) {
            return null
        }
        return instruction
    }

    private fun stripOptionalTrigger(text: String): String {
        val triggers = listOf("koog:", "koog-agent:", "ai:", "AI:")
        return triggers.firstOrNull { trigger -> text.startsWith(trigger, ignoreCase = true) }
            ?.let { trigger -> text.drop(trigger.length).trim() }
            ?: text
    }
}

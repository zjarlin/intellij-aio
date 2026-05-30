package site.addzero.koog.agent

internal object KoogAgentResponseCodeExtractor {
    private val fencedCodeRegex = Regex("""(?s)```[A-Za-z0-9_+\-.#]*\s*(.*?)\s*```""")

    fun extractCode(output: String): String {
        val trimmed = output.trim()
        if (trimmed.isBlank()) {
            return ""
        }
        val fenced = fencedCodeRegex.find(trimmed)?.groupValues?.getOrNull(1)
        return (fenced ?: trimmed)
            .trim()
            .removePrefix("代码：")
            .removePrefix("Code:")
            .trim()
    }
}

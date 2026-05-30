package site.addzero.koog.agent.settings

enum class KoogAgentProvider(val displayName: String) {
    OPENAI("OpenAI Responses"),
    ANTHROPIC("Anthropic Messages"),
    OPENAI_COMPATIBLE("OpenAI Compatible Chat");

    companion object {
        fun from(value: String): KoogAgentProvider {
            return entries.firstOrNull { provider ->
                provider.name.equals(value, ignoreCase = true) ||
                    provider.displayName.equals(value, ignoreCase = true)
            } ?: OPENAI_COMPATIBLE
        }
    }
}

package site.addzero.koog.agent.http

import site.addzero.koog.agent.util.KoogAgentJson

internal object KoogAgentResponseParsers {
    fun openAiResponsesText(responseBody: String): String {
        val root = KoogAgentJson.parse(responseBody)
        KoogAgentJson.stringAt(root, "output_text")?.let { return it }
        KoogAgentJson.findTextBySiblingType(root, setOf("output_text", "text"))?.let { return it }
        KoogAgentJson.findFirstStringByKey(root, "text")?.let { return it }
        error("OpenAI response did not contain output text")
    }

    fun openAiChatText(responseBody: String): String {
        val root = KoogAgentJson.parse(responseBody)
        KoogAgentJson.stringAt(root, "choices", 0, "message", "content")?.let { return it }
        error("OpenAI-compatible response did not contain message content")
    }

    fun anthropicText(responseBody: String): String {
        val root = KoogAgentJson.parse(responseBody)
        KoogAgentJson.findTextBySiblingType(root, setOf("text"))?.let { return it }
        KoogAgentJson.stringAt(root, "content", 0, "text")?.let { return it }
        error("Anthropic response did not contain text content")
    }
}

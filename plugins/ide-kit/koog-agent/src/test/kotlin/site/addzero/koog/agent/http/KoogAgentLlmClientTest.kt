package site.addzero.koog.agent.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import site.addzero.koog.agent.util.KoogAgentJson

class KoogAgentLlmClientTest {
    @Test
    fun `openai responses input uses message list shape`() {
        val payload = KoogAgentLlmClient.openAiResponsesPayload(
            model = " gpt-5.5 ",
            systemPrompt = "Return code only.",
            userPrompt = "Generate a function.",
        )

        val root = KoogAgentJson.parse(KoogAgentJson.stringify(payload)) as Map<*, *>
        val input = root["input"]

        assertEquals("gpt-5.5", root["model"])
        assertTrue(input is List<*>)

        val message = (input as List<*>).single() as Map<*, *>
        assertEquals("message", message["type"])
        assertEquals("user", message["role"])

        val content = message["content"]
        assertTrue(content is List<*>)

        val textPart = (content as List<*>).single() as Map<*, *>
        assertEquals("input_text", textPart["type"])
        assertEquals("Generate a function.", textPart["text"])
    }
}

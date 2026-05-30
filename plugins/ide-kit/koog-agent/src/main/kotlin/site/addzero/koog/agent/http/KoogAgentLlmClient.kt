package site.addzero.koog.agent.http

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import site.addzero.koog.agent.settings.KoogAgentModelState
import site.addzero.koog.agent.settings.KoogAgentProvider
import site.addzero.koog.agent.util.KoogAgentJson

internal object KoogAgentLlmClient {
    fun generate(
        model: KoogAgentModelState,
        systemPrompt: String,
        userPrompt: String,
    ): String {
        return when (KoogAgentProvider.from(model.vendor)) {
            KoogAgentProvider.OPENAI -> generateWithOpenAiResponses(model, systemPrompt, userPrompt)
            KoogAgentProvider.ANTHROPIC -> generateWithAnthropicMessages(model, systemPrompt, userPrompt)
            KoogAgentProvider.OPENAI_COMPATIBLE -> generateWithOpenAiCompatibleChat(model, systemPrompt, userPrompt)
        }
    }

    private fun generateWithOpenAiResponses(
        model: KoogAgentModelState,
        systemPrompt: String,
        userPrompt: String,
    ): String {
        val payload = openAiResponsesPayload(model.model, systemPrompt, userPrompt)
        val response = postJson(
            endpoint = endpoint(model.baseUrl, "responses"),
            apiKey = model.apiKey,
            payload = payload,
            headers = mapOf("Authorization" to "Bearer ${model.apiKey.trim()}"),
        )
        return KoogAgentResponseParsers.openAiResponsesText(response)
    }

    internal fun openAiResponsesPayload(
        model: String,
        systemPrompt: String,
        userPrompt: String,
    ): Map<String, Any?> {
        return mapOf(
            "model" to model.trim(),
            "instructions" to systemPrompt,
            "input" to listOf(
                mapOf(
                    "type" to "message",
                    "role" to "user",
                    "content" to listOf(
                        mapOf(
                            "type" to "input_text",
                            "text" to userPrompt,
                        ),
                    ),
                ),
            ),
            "reasoning" to mapOf("effort" to "low"),
        )
    }

    private fun generateWithOpenAiCompatibleChat(
        model: KoogAgentModelState,
        systemPrompt: String,
        userPrompt: String,
    ): String {
        val payload = mapOf(
            "model" to model.model.trim(),
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userPrompt),
            ),
            "temperature" to 0.2,
            "max_tokens" to CODE_SNIPPET_MAX_TOKENS,
        )
        val response = postJson(
            endpoint = endpoint(model.baseUrl, "chat/completions"),
            apiKey = model.apiKey,
            payload = payload,
            headers = mapOf("Authorization" to "Bearer ${model.apiKey.trim()}"),
        )
        return KoogAgentResponseParsers.openAiChatText(response)
    }

    private fun generateWithAnthropicMessages(
        model: KoogAgentModelState,
        systemPrompt: String,
        userPrompt: String,
    ): String {
        val payload = mapOf(
            "model" to model.model.trim(),
            "max_tokens" to CODE_SNIPPET_MAX_TOKENS,
            "system" to systemPrompt,
            "messages" to listOf(
                mapOf("role" to "user", "content" to userPrompt),
            ),
        )
        val response = postJson(
            endpoint = endpoint(model.baseUrl, "messages"),
            apiKey = model.apiKey,
            payload = payload,
            headers = mapOf(
                "x-api-key" to model.apiKey.trim(),
                "anthropic-version" to "2023-06-01",
            ),
        )
        return KoogAgentResponseParsers.anthropicText(response)
    }

    private fun postJson(
        endpoint: String,
        apiKey: String,
        payload: Map<String, Any?>,
        headers: Map<String, String>,
    ): String {
        if (apiKey.isBlank()) {
            error("API key is blank")
        }

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 90_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (name, value) ->
            connection.setRequestProperty(name, value)
        }

        val requestBody = KoogAgentJson.stringify(payload).toByteArray(StandardCharsets.UTF_8)
        connection.outputStream.use { outputStream ->
            outputStream.write(requestBody)
        }

        val statusCode = connection.responseCode
        val responseBody = ((if (statusCode in 200..299) connection.inputStream else connection.errorStream)
            ?.use { inputStream -> inputStream.reader(StandardCharsets.UTF_8).readText() })
            .orEmpty()
        connection.disconnect()

        if (statusCode !in 200..299) {
            error("HTTP $statusCode from ${redactSecrets(endpoint)}: ${redactSecrets(responseBody).take(500)}")
        }
        return responseBody
    }

    private fun endpoint(
        baseUrl: String,
        path: String,
    ): String {
        val normalized = baseUrl.trim().trimEnd('/')
        if (normalized.isBlank()) {
            error("Base URL is blank")
        }
        return "$normalized/$path"
    }

    private fun redactSecrets(text: String): String {
        return text
            .replace(
                Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~+\\-/=]+"),
                "\$1<redacted>",
            )
            .replace(
                Regex("sk-[A-Za-z0-9_*.-]{8,}"),
                "sk-<redacted>",
            )
            .replace(
                Regex("(?i)(\"(?:api[_-]?key|x-api-key|authorization|access_token|refresh_token|id_token)\"\\s*:\\s*\")([^\"]+)(\")"),
                "\$1<redacted>\$3",
            )
            .replace(
                Regex("(?i)((?:api[_-]?key|x-api-key|authorization|access_token|refresh_token|id_token)=)[^&\\s]+"),
                "\$1<redacted>",
            )
    }

    private const val CODE_SNIPPET_MAX_TOKENS = 2048
}

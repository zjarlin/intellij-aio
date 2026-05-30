package site.addzero.koog.agent

import site.addzero.koog.agent.http.KoogAgentLlmClient
import site.addzero.koog.agent.settings.KoogAgentSettingsState

internal object KoogAgentCodeGenerator {
    fun generate(
        settings: KoogAgentSettingsState,
        request: KoogAgentGenerationRequest,
    ): String {
        val models = settings.enabledModels()
        if (models.isEmpty()) {
            error("No enabled ide-kit AI model is configured")
        }

        val failures = mutableListOf<String>()
        val systemPrompt = KoogAgentPromptBuilder.SYSTEM_PROMPT
        val userPrompt = KoogAgentPromptBuilder.userPrompt(request)

        models.forEach { model ->
            runCatching {
                printPromptForDebug(model.vendor, model.model, systemPrompt, userPrompt)
                KoogAgentLlmClient.generate(model, systemPrompt, userPrompt)
            }.onSuccess { output ->
                val code = KoogAgentResponseCodeExtractor.extractCode(output)
                if (code.isNotBlank()) {
                    return code
                }
                failures.add("${model.vendor}/${model.model}: empty response")
            }.onFailure { error ->
                failures.add("${model.vendor}/${model.model}: ${error.message ?: error.javaClass.simpleName}")
            }
        }

        error("All ide-kit AI models failed: ${failures.joinToString("; ")}")
    }

    private fun printPromptForDebug(
        vendor: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
    ) {
        println(
            buildString {
                appendLine("========== ide-kit AI LLM prompt ==========")
                appendLine("Model: $vendor/$model")
                appendLine("----- system -----")
                appendLine(systemPrompt)
                appendLine("----- user -----")
                appendLine(userPrompt)
                appendLine("======== end ide-kit AI LLM prompt ========")
            },
        )
    }
}

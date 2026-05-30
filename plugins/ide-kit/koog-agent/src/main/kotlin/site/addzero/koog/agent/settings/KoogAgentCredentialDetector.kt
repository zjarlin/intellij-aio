package site.addzero.koog.agent.settings

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.text.Charsets
import site.addzero.koog.agent.util.KoogAgentJson

internal object KoogAgentCredentialDetector {
    fun detect(): List<KoogAgentModelState> {
        return buildList {
            addCodexDetectedModel()?.let(::add)
            detectAnthropicKey()?.let { apiKey ->
                add(
                    KoogAgentModelState(
                        enabled = true,
                        vendor = KoogAgentProvider.ANTHROPIC.name,
                        baseUrl = "https://api.anthropic.com/v1",
                        model = "claude-sonnet-4-6",
                        apiKey = apiKey,
                        order = 20,
                        detected = true,
                        source = "Claude/Anthropic",
                    ),
                )
            }
        }
    }

    private fun addCodexDetectedModel(): KoogAgentModelState? {
        val codexHome = resolveCodexHome()
        val config = readCodexToml(codexHome.resolve("config.toml"))
        val providerName = config?.root?.firstString("model_provider", "modelProvider").orDefault("openai")
        val providerConfig = config?.table("model_providers.$providerName").orEmpty()
        val model = config?.root?.firstString("model") ?: providerConfig.firstString("model") ?: "gpt-5.5"
        val baseUrl = providerConfig.firstString("base_url", "baseUrl")
            ?: System.getenv("OPENAI_BASE_URL")
            ?: "https://api.openai.com/v1"
        val wireApi = providerConfig.firstString("wire_api", "wireApi")
            ?: if (providerName.equals("openai", ignoreCase = true)) "responses" else ""
        val vendor = inferProvider(wireApi = wireApi, baseUrl = baseUrl, model = model)
        val apiKey = detectCodexApiKey(codexHome = codexHome, providerConfig = providerConfig) ?: return null

        return KoogAgentModelState(
            enabled = true,
            vendor = vendor.name,
            baseUrl = baseUrl,
            model = model,
            apiKey = apiKey,
            order = 10,
            detected = true,
            source = "Codex/$providerName",
        )
    }

    private fun inferProvider(
        wireApi: String,
        baseUrl: String,
        model: String,
    ): KoogAgentProvider {
        val normalizedWireApi = wireApi.trim().lowercase().replace("-", "_")
        val normalizedBaseUrl = baseUrl.trim().lowercase()
        val normalizedModel = model.trim().lowercase()
        return when {
            normalizedWireApi in setOf("responses", "response") -> KoogAgentProvider.OPENAI
            normalizedWireApi in setOf("chat", "chat_completions", "chat/completions") -> KoogAgentProvider.OPENAI_COMPATIBLE
            normalizedWireApi in setOf("messages", "anthropic") -> KoogAgentProvider.ANTHROPIC
            "anthropic" in normalizedBaseUrl || normalizedModel.startsWith("claude") -> KoogAgentProvider.ANTHROPIC
            "api.openai.com" in normalizedBaseUrl -> KoogAgentProvider.OPENAI
            else -> KoogAgentProvider.OPENAI_COMPATIBLE
        }
    }

    private fun detectCodexApiKey(
        codexHome: Path,
        providerConfig: Map<String, String>,
    ): String? {
        val configuredEnvKeys = listOfNotNull(
            providerConfig.firstString("env_key", "envKey"),
            providerConfig.firstString("api_key_env_var", "apiKeyEnvVar"),
            providerConfig.firstString("api_key_env", "apiKeyEnv"),
        )
            .flatMap { value -> value.split(',', ';', ' ') }
            .map { value -> value.trim() }
            .filter { value -> value.isNotBlank() }

        val authJsonKeys = configuredEnvKeys + OPENAI_KEY_NAMES
        return firstNotBlank(
            *configuredEnvKeys.map { envKey -> System.getenv(envKey) }.toTypedArray(),
            providerConfig.firstString("api_key", "apiKey"),
            System.getenv("OPENAI_API_KEY"),
            readJsonString(codexHome.resolve("auth.json"), authJsonKeys),
            readJsonString(Paths.get(System.getProperty("user.home")).resolve(".openai").resolve("auth.json"), authJsonKeys),
        )
    }

    private fun readCodexToml(path: Path): KoogAgentTomlDocument? {
        if (!Files.isRegularFile(path)) {
            return null
        }
        return runCatching {
            KoogAgentToml.parse(String(Files.readAllBytes(path), Charsets.UTF_8))
        }.getOrNull()
    }

    private fun detectAnthropicKey(): String? {
        val userHome = Paths.get(System.getProperty("user.home"))
        return firstNotBlank(
            System.getenv("ANTHROPIC_API_KEY"),
            System.getenv("CLAUDE_API_KEY"),
            readJsonString(userHome.resolve(".claude").resolve("auth.json"), ANTHROPIC_KEY_NAMES),
            readJsonString(userHome.resolve(".claude").resolve("credentials.json"), ANTHROPIC_KEY_NAMES),
            readJsonString(userHome.resolve(".claude.json"), ANTHROPIC_KEY_NAMES),
        )
    }

    private fun resolveCodexHome(): Path {
        return System.getenv("CODEX_HOME")
            ?.takeIf { it.isNotBlank() }
            ?.let { Paths.get(it) }
            ?: Paths.get(System.getProperty("user.home")).resolve(".codex")
    }

    private fun readJsonString(
        path: Path,
        keyNames: Collection<String>,
    ): String? {
        if (!Files.isRegularFile(path)) {
            return null
        }
        return runCatching {
            val parsed = KoogAgentJson.parse(String(Files.readAllBytes(path), Charsets.UTF_8))
            KoogAgentJson.findFirstStringByKeys(parsed, keyNames)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun firstNotBlank(vararg values: String?): String? {
        return values.firstOrNull { value -> !value.isNullOrBlank() }?.trim()
    }

    private fun Map<String, String>.firstString(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            this[key] ?: entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }?.value
        }?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun String?.orDefault(defaultValue: String): String {
        return this?.takeIf { it.isNotBlank() } ?: defaultValue
    }

    private val OPENAI_KEY_NAMES = listOf(
        "OPENAI_API_KEY",
        "openai_api_key",
        "openAiApiKey",
        "api_key",
        "apiKey",
    )

    private val ANTHROPIC_KEY_NAMES = listOf(
        "ANTHROPIC_API_KEY",
        "CLAUDE_API_KEY",
        "anthropic_api_key",
        "claude_api_key",
        "anthropicApiKey",
        "claudeApiKey",
    )
}

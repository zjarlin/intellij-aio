package site.addzero.openprojecteverywhere.settings

import site.addzero.openprojecteverywhere.OpenProjectEverywhereBundle
import java.net.URI

enum class AuthMode(private val messageKey: String) {
    TOKEN("settings.auth.mode.token"),
    USERNAME_PASSWORD("settings.auth.mode.password");

    override fun toString(): String = OpenProjectEverywhereBundle.message(messageKey)

    companion object {
        fun fromValue(value: String): AuthMode {
            return entries.firstOrNull { it.name == value } ?: TOKEN
        }
    }
}

enum class ProviderKind(private val messageKey: String) {
    GITHUB("settings.providerKind.github"),
    GITLAB("settings.providerKind.gitlab"),
    GITEE("settings.providerKind.gitee");

    override fun toString(): String = OpenProjectEverywhereBundle.message(messageKey)

    companion object {
        fun fromValue(value: String): ProviderKind {
            return entries.firstOrNull { it.name == value } ?: GITLAB
        }
    }
}

enum class CredentialsSource {
    SETTINGS,
    IDE_GITHUB
}

data class RemoteHostConfiguration(
    val id: String,
    val displayName: String,
    val kind: ProviderKind,
    val baseUrl: String,
    val authMode: AuthMode,
    val username: String,
    val secret: String,
    val credentialsSource: CredentialsSource = CredentialsSource.SETTINGS
) {
    val normalizedBaseUrl: String = baseUrl.trim().trimEnd('/')

    fun host(): String? {
        return runCatching { URI(normalizedBaseUrl).host?.lowercase() }.getOrNull()
    }

    fun isConfiguredForSearch(): Boolean {
        return when (authMode) {
            AuthMode.TOKEN -> secret.isNotBlank()
            AuthMode.USERNAME_PASSWORD -> username.isNotBlank() && secret.isNotBlank()
        }
    }

    fun isConfiguredForGit(): Boolean = isConfiguredForSearch()

    fun gitUsername(): String {
        if (authMode == AuthMode.USERNAME_PASSWORD) {
            return username
        }

        return username.ifBlank {
            when (kind) {
                ProviderKind.GITHUB -> "x-access-token"
                ProviderKind.GITLAB -> "oauth2"
                ProviderKind.GITEE -> "git"
            }
        }
    }
}

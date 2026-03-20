package site.addzero.gitee.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Stores Gitee secrets in IntelliJ PasswordSafe instead of plain-text settings.
 */
object GiteeCredentialStorage {

    private const val SERVICE_PREFIX = "site.addzero.gitee"
    private const val ACCESS_TOKEN_KEY = "accessToken"
    private const val PASSWORD_KEY = "password"

    fun getAccessToken(): String? = getSecret(ACCESS_TOKEN_KEY)

    fun setAccessToken(value: String) {
        setSecret(ACCESS_TOKEN_KEY, value)
    }

    fun removeAccessToken() {
        removeSecret(ACCESS_TOKEN_KEY)
    }

    fun getPassword(): String? = getSecret(PASSWORD_KEY)

    fun setPassword(value: String) {
        setSecret(PASSWORD_KEY, value)
    }

    fun removePassword() {
        removeSecret(PASSWORD_KEY)
    }

    private fun getSecret(key: String): String? {
        return PasswordSafe.instance.get(createAttributes(key))?.getPasswordAsString()
    }

    private fun setSecret(key: String, value: String) {
        if (value.isBlank()) {
            removeSecret(key)
            return
        }

        val attributes = createAttributes(key)
        PasswordSafe.instance.set(attributes, Credentials(attributes.userName, value))
    }

    private fun removeSecret(key: String) {
        PasswordSafe.instance.set(createAttributes(key), null)
    }

    private fun createAttributes(key: String): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName(SERVICE_PREFIX, key),
            key
        )
    }
}

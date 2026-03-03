package site.addzero.cloudfile.util

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Secure storage for sensitive data using IntelliJ's PasswordSafe
 * Part of defensive programming - credentials are never stored in plain text
 */
object SecureStorage {

    private const val SERVICE_PREFIX = "site.addzero.cloudfile"

    fun setSecureValue(key: String, value: String) {
        val attributes = createCredentialAttributes(key)
        PasswordSafe.instance.set(attributes, Credentials(attributes.userName, value))
    }

    fun getSecureValue(key: String): String? {
        val attributes = createCredentialAttributes(key)
        return PasswordSafe.instance.get(attributes)?.getPasswordAsString()
    }

    fun removeSecureValue(key: String) {
        val attributes = createCredentialAttributes(key)
        PasswordSafe.instance.set(attributes, null)
    }

    private fun createCredentialAttributes(key: String): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName(SERVICE_PREFIX, key),
            key
        )
    }
}

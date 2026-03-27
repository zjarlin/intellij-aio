package site.addzero.openprojecteverywhere.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

object OpenProjectEverywhereCredentialStorage {
    private const val SERVICE_PREFIX = "site.addzero.openprojecteverywhere"

    fun getSecret(key: String): String? {
        return PasswordSafe.instance.get(createAttributes(key))?.getPasswordAsString()
    }

    fun setSecret(key: String, value: String) {
        if (value.isBlank()) {
            removeSecret(key)
            return
        }

        val attributes = createAttributes(key)
        PasswordSafe.instance.set(attributes, Credentials(attributes.userName, value))
    }

    fun removeSecret(key: String) {
        PasswordSafe.instance.set(createAttributes(key), null)
    }

    private fun createAttributes(key: String): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName(SERVICE_PREFIX, key),
            key
        )
    }
}

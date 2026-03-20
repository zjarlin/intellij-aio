package site.addzero.gitee.git

import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import git4idea.remote.GitHttpAuthDataProvider
import site.addzero.gitee.settings.GiteeAuthType
import site.addzero.gitee.settings.GiteeCredentialStorage
import site.addzero.gitee.settings.GiteeSettings

/**
 * Supplies stored Gitee credentials to Git HTTPS operations.
 */
class GiteeGitHttpAuthDataProvider : GitHttpAuthDataProvider {

    override fun getAuthData(project: Project, url: String): AuthData? {
        if (!isGiteeUrl(url)) {
            return null
        }

        val settings = GiteeSettings.getInstance()
        return when (settings.authType) {
            GiteeAuthType.PASSWORD -> {
                val username = settings.username.trim()
                val password = settings.password
                if (username.isBlank() || password.isBlank()) {
                    null
                } else {
                    AuthData(username, password)
                }
            }

            GiteeAuthType.TOKEN -> {
                val token = settings.accessToken
                val username = settings.username.trim()
                if (token.isBlank() || username.isBlank()) {
                    null
                } else {
                    AuthData(username, token)
                }
            }
        }
    }

    override fun isSilent(): Boolean = true

    override fun forgetPassword(project: Project, url: String, authData: AuthData) {
        if (!isGiteeUrl(url)) {
            return
        }

        val settings = GiteeSettings.getInstance()
        if (settings.authType == GiteeAuthType.PASSWORD && authData.login == settings.username) {
            GiteeCredentialStorage.removePassword()
        }
    }

    private fun isGiteeUrl(url: String): Boolean {
        return url.contains("gitee.com", ignoreCase = true)
    }
}

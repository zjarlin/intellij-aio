package site.addzero.openprojecteverywhere.git

import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import git4idea.remote.GitHttpAuthDataProvider
import site.addzero.openprojecteverywhere.github.OpenProjectEverywhereRemoteHostResolver
import site.addzero.openprojecteverywhere.settings.CredentialsSource
import site.addzero.openprojecteverywhere.settings.OpenProjectEverywhereSettings
import java.net.URI

class OpenProjectEverywhereGitHttpAuthDataProvider : GitHttpAuthDataProvider {

    override fun getAuthData(project: Project, url: String): AuthData? {
        val settings = OpenProjectEverywhereSettings.getInstance()
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return null
        val match = OpenProjectEverywhereRemoteHostResolver.enabledRemoteHosts(settings, project)
            .firstOrNull { config -> config.isConfiguredForGit() && config.host()?.equals(host, ignoreCase = true) == true }
            ?: return null

        return AuthData(match.gitUsername(), match.secret)
    }

    override fun isSilent(): Boolean = true

    override fun forgetPassword(project: Project, url: String, authData: AuthData) {
        val settings = OpenProjectEverywhereSettings.getInstance()
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return
        val match = OpenProjectEverywhereRemoteHostResolver.enabledRemoteHosts(settings, project)
            .firstOrNull { config -> config.host()?.equals(host, ignoreCase = true) == true }
            ?: return

        if (match.credentialsSource != CredentialsSource.SETTINGS) {
            return
        }

        if (authData.login == match.gitUsername()) {
            when (match.id) {
                "github" -> settings.setGithubSecret("")
                "gitlab" -> settings.setGitlabSecret("")
                "gitee" -> settings.setGiteeSecret("")
                "custom" -> settings.setCustomSecret("")
            }
        }
    }
}

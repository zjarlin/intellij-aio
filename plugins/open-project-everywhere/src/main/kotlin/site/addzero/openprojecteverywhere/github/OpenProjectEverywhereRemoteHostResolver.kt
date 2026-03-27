package site.addzero.openprojecteverywhere.github

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import site.addzero.openprojecteverywhere.search.SearchScope
import site.addzero.openprojecteverywhere.settings.AuthMode
import site.addzero.openprojecteverywhere.settings.CredentialsSource
import site.addzero.openprojecteverywhere.settings.OpenProjectEverywhereSettings
import site.addzero.openprojecteverywhere.settings.ProviderKind
import site.addzero.openprojecteverywhere.settings.RemoteHostConfiguration

object OpenProjectEverywhereRemoteHostResolver {

    fun enabledRemoteHosts(settings: OpenProjectEverywhereSettings, project: Project?): List<RemoteHostConfiguration> {
        return buildList {
            if (settings.githubEnabled) add(githubConfiguration(settings, project))
            if (settings.gitlabEnabled) add(settings.gitlabConfiguration())
            if (settings.giteeEnabled) add(settings.giteeConfiguration())
            if (settings.customEnabled) add(settings.customConfiguration())
        }
    }

    fun configurationForScope(
        settings: OpenProjectEverywhereSettings,
        project: Project?,
        scope: SearchScope
    ): RemoteHostConfiguration? {
        return when (scope) {
            SearchScope.OWN -> null
            SearchScope.GITHUB_PUBLIC -> githubConfiguration(settings, project)
            SearchScope.GITLAB_PUBLIC -> settings.gitlabConfiguration()
            SearchScope.GITEE_PUBLIC -> settings.giteeConfiguration()
            SearchScope.CUSTOM_PUBLIC -> settings.customConfiguration()
        }
    }

    private fun githubConfiguration(
        settings: OpenProjectEverywhereSettings,
        project: Project?
    ): RemoteHostConfiguration {
        val configured = settings.githubConfiguration()
        if (configured.isConfiguredForSearch()) {
            return configured
        }

        val account = resolveGithubAccount(project) ?: return configured
        val accountManager = ApplicationManager.getApplication().getService(GHAccountManager::class.java) ?: return configured
        val token = runBlocking { accountManager.findCredentials(account) }?.takeIf { it.isNotBlank() } ?: return configured

        return configured.copy(
            baseUrl = account.server.toUrl(),
            kind = ProviderKind.GITHUB,
            authMode = AuthMode.TOKEN,
            username = "",
            secret = token,
            credentialsSource = CredentialsSource.IDE_GITHUB
        )
    }

    private fun resolveGithubAccount(project: Project?): GithubAccount? {
        val effectiveProject = project
            ?: ProjectManager.getInstance().openProjects.firstOrNull()
            ?: ProjectManager.getInstance().defaultProject

        return GHAccountsUtil.getSingleOrDefaultAccount(effectiveProject)
            ?: GHAccountsUtil.accounts.singleOrNull()
    }
}

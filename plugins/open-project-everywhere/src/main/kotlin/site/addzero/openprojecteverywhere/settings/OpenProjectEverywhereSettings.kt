package site.addzero.openprojecteverywhere.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "OpenProjectEverywhereSettings",
    storages = [Storage("openProjectEverywhere.xml")]
)
class OpenProjectEverywhereSettings : PersistentStateComponent<OpenProjectEverywhereState> {

    private var state = OpenProjectEverywhereState()

    override fun getState(): OpenProjectEverywhereState = state

    override fun loadState(loadedState: OpenProjectEverywhereState) {
        state = loadedState.copy(
            localProjectsRoot = loadedState.localProjectsRoot.ifBlank {
                OpenProjectEverywhereDefaults.defaultLocalProjectsRoot()
            },
            gitlabBaseUrl = loadedState.gitlabBaseUrl.ifBlank { "https://gitlab.com" },
            giteeBaseUrl = loadedState.giteeBaseUrl.ifBlank { "https://gitee.com" }
        )
    }

    var localProjectsRoot: String
        get() = state.localProjectsRoot
        set(value) {
            state.localProjectsRoot = value.trim()
        }

    var localProjectsEnabled: Boolean
        get() = state.localProjectsEnabled
        set(value) {
            state.localProjectsEnabled = value
        }

    var githubEnabled: Boolean
        get() = state.githubEnabled
        set(value) {
            state.githubEnabled = value
        }

    var githubAuthMode: AuthMode
        get() = AuthMode.fromValue(state.githubAuthMode)
        set(value) {
            state.githubAuthMode = value.name
        }

    var githubUsername: String
        get() = state.githubUsername
        set(value) {
            state.githubUsername = value.trim()
        }

    var gitlabEnabled: Boolean
        get() = state.gitlabEnabled
        set(value) {
            state.gitlabEnabled = value
        }

    var gitlabBaseUrl: String
        get() = state.gitlabBaseUrl
        set(value) {
            state.gitlabBaseUrl = value.trim().trimEnd('/')
        }

    var gitlabAuthMode: AuthMode
        get() = AuthMode.fromValue(state.gitlabAuthMode)
        set(value) {
            state.gitlabAuthMode = value.name
        }

    var gitlabUsername: String
        get() = state.gitlabUsername
        set(value) {
            state.gitlabUsername = value.trim()
        }

    var giteeEnabled: Boolean
        get() = state.giteeEnabled
        set(value) {
            state.giteeEnabled = value
        }

    var giteeBaseUrl: String
        get() = state.giteeBaseUrl
        set(value) {
            state.giteeBaseUrl = value.trim().trimEnd('/')
        }

    var giteeAuthMode: AuthMode
        get() = AuthMode.fromValue(state.giteeAuthMode)
        set(value) {
            state.giteeAuthMode = value.name
        }

    var giteeUsername: String
        get() = state.giteeUsername
        set(value) {
            state.giteeUsername = value.trim()
        }

    var customEnabled: Boolean
        get() = state.customEnabled
        set(value) {
            state.customEnabled = value
        }

    var customDisplayName: String
        get() = state.customDisplayName
        set(value) {
            state.customDisplayName = value.trim()
        }

    var customProviderKind: ProviderKind
        get() = ProviderKind.fromValue(state.customProviderKind)
        set(value) {
            state.customProviderKind = value.name
        }

    var customBaseUrl: String
        get() = state.customBaseUrl
        set(value) {
            state.customBaseUrl = value.trim().trimEnd('/')
        }

    var customAuthMode: AuthMode
        get() = AuthMode.fromValue(state.customAuthMode)
        set(value) {
            state.customAuthMode = value.name
        }

    var customUsername: String
        get() = state.customUsername
        set(value) {
            state.customUsername = value.trim()
        }

    fun githubSecret(): String = OpenProjectEverywhereCredentialStorage.getSecret("github.secret").orEmpty()

    fun setGithubSecret(value: String) {
        OpenProjectEverywhereCredentialStorage.setSecret("github.secret", value)
    }

    fun gitlabSecret(): String = OpenProjectEverywhereCredentialStorage.getSecret("gitlab.secret").orEmpty()

    fun setGitlabSecret(value: String) {
        OpenProjectEverywhereCredentialStorage.setSecret("gitlab.secret", value)
    }

    fun giteeSecret(): String = OpenProjectEverywhereCredentialStorage.getSecret("gitee.secret").orEmpty()

    fun setGiteeSecret(value: String) {
        OpenProjectEverywhereCredentialStorage.setSecret("gitee.secret", value)
    }

    fun customSecret(): String = OpenProjectEverywhereCredentialStorage.getSecret("custom.secret").orEmpty()

    fun setCustomSecret(value: String) {
        OpenProjectEverywhereCredentialStorage.setSecret("custom.secret", value)
    }

    fun githubConfiguration(): RemoteHostConfiguration {
        return RemoteHostConfiguration(
            id = "github",
            displayName = "GitHub",
            kind = ProviderKind.GITHUB,
            baseUrl = "https://github.com",
            authMode = githubAuthMode,
            username = githubUsername,
            secret = githubSecret()
        )
    }

    fun gitlabConfiguration(): RemoteHostConfiguration {
        return RemoteHostConfiguration(
            id = "gitlab",
            displayName = "GitLab",
            kind = ProviderKind.GITLAB,
            baseUrl = gitlabBaseUrl,
            authMode = gitlabAuthMode,
            username = gitlabUsername,
            secret = gitlabSecret()
        )
    }

    fun giteeConfiguration(): RemoteHostConfiguration {
        return RemoteHostConfiguration(
            id = "gitee",
            displayName = "Gitee",
            kind = ProviderKind.GITEE,
            baseUrl = giteeBaseUrl,
            authMode = giteeAuthMode,
            username = giteeUsername,
            secret = giteeSecret()
        )
    }

    fun customConfiguration(): RemoteHostConfiguration {
        return RemoteHostConfiguration(
            id = "custom",
            displayName = customDisplayName,
            kind = customProviderKind,
            baseUrl = customBaseUrl,
            authMode = customAuthMode,
            username = customUsername,
            secret = customSecret()
        )
    }

    fun enabledRemoteHosts(): List<RemoteHostConfiguration> {
        val result = mutableListOf<RemoteHostConfiguration>()
        if (githubEnabled) {
            result += githubConfiguration()
        }
        if (gitlabEnabled) {
            result += gitlabConfiguration()
        }
        if (giteeEnabled) {
            result += giteeConfiguration()
        }
        if (customEnabled) {
            result += customConfiguration()
        }
        return result
    }

    companion object {
        fun getInstance(): OpenProjectEverywhereSettings {
            return ApplicationManager.getApplication().getService(OpenProjectEverywhereSettings::class.java)
        }
    }
}

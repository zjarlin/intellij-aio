package site.addzero.gitee.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level Gitee settings service
 */
@Service(Service.Level.APP)
@State(
    name = "GiteeSettings",
    storages = [Storage("giteeSettings.xml")]
)
class GiteeSettings : PersistentStateComponent<GiteeState> {

    private var state = GiteeState()

    override fun getState(): GiteeState = state

    override fun loadState(loadedState: GiteeState) {
        if (loadedState.accessToken.isNotBlank() && GiteeCredentialStorage.getAccessToken().isNullOrBlank()) {
            GiteeCredentialStorage.setAccessToken(loadedState.accessToken)
        }

        if (loadedState.accessToken.isNotBlank() && loadedState.authType == GiteeAuthType.PASSWORD.name) {
            loadedState.authType = GiteeAuthType.TOKEN.name
        }

        loadedState.accessToken = ""
        this.state = loadedState
    }

    var authType: GiteeAuthType
        get() = GiteeAuthType.fromValue(state.authType)
        set(value) { state.authType = value.name }

    var accessToken: String
        get() = GiteeCredentialStorage.getAccessToken().orEmpty()
        set(value) {
            GiteeCredentialStorage.setAccessToken(value)
            state.accessToken = ""
        }

    var username: String
        get() = state.username
        set(value) { state.username = value }

    var password: String
        get() = GiteeCredentialStorage.getPassword().orEmpty()
        set(value) {
            GiteeCredentialStorage.setPassword(value)
        }

    var defaultVisibility: String
        get() = state.defaultVisibility
        set(value) { state.defaultVisibility = value }

    fun isConfigured(): Boolean = accessToken.isNotBlank()

    fun hasAccessToken(): Boolean = accessToken.isNotBlank()

    fun hasPasswordCredentials(): Boolean = username.isNotBlank() && password.isNotBlank()

    fun hasCloneAccountConfigured(): Boolean {
        return when (authType) {
            GiteeAuthType.PASSWORD -> hasPasswordCredentials()
            GiteeAuthType.TOKEN -> hasAccessToken()
        }
    }

    companion object {
        fun getInstance(): GiteeSettings =
            ApplicationManager.getApplication().getService(GiteeSettings::class.java)
    }
}

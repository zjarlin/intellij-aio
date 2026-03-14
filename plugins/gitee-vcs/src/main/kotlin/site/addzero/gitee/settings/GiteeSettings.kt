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

    override fun loadState(state: GiteeState) {
        this.state = state
    }

    var accessToken: String
        get() = state.accessToken
        set(value) { state.accessToken = value }

    var username: String
        get() = state.username
        set(value) { state.username = value }

    var defaultVisibility: String
        get() = state.defaultVisibility
        set(value) { state.defaultVisibility = value }

    fun isConfigured(): Boolean = accessToken.isNotBlank()

    fun hasAccessToken(): Boolean = accessToken.isNotBlank()

    fun hasCloneAccountConfigured(): Boolean = username.isNotBlank()

    companion object {
        fun getInstance(): GiteeSettings =
            ApplicationManager.getApplication().getService(GiteeSettings::class.java)
    }
}

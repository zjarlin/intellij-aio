package site.addzero.kcloud.idea.configcenter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

data class KCloudConfigCenterAppState(
    var sqlitePath: String = "",
)

@Service(Service.Level.APP)
@State(
    name = "KCloudConfigCenterAppSettings",
    storages = [Storage("kcloud-config-center.xml")],
)
class KCloudConfigCenterAppSettings : PersistentStateComponent<KCloudConfigCenterAppState> {
    private var state = KCloudConfigCenterAppState()

    override fun getState(): KCloudConfigCenterAppState {
        return state
    }

    override fun loadState(loadedState: KCloudConfigCenterAppState) {
        state = loadedState
    }

    var sqlitePath: String
        get() = state.sqlitePath
        set(value) {
            state.sqlitePath = value.trim()
        }

    companion object {
        fun getInstance(): KCloudConfigCenterAppSettings {
            return ApplicationManager.getApplication().getService(KCloudConfigCenterAppSettings::class.java)
        }
    }
}

data class KCloudConfigCenterProjectState(
    var namespace: String = "",
    var profile: String = "default",
)

@Service(Service.Level.PROJECT)
@State(
    name = "KCloudConfigCenterProjectSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class KCloudConfigCenterProjectSettings(
    private val project: Project,
) : PersistentStateComponent<KCloudConfigCenterProjectState> {
    private var state = KCloudConfigCenterProjectState()

    override fun getState(): KCloudConfigCenterProjectState {
        return state
    }

    override fun loadState(loadedState: KCloudConfigCenterProjectState) {
        state = loadedState
    }

    var namespace: String
        get() = state.namespace
        set(value) {
            state.namespace = value.trim()
        }

    var profile: String
        get() = state.profile
        set(value) {
            state.profile = value.trim().ifBlank { "default" }
        }

    fun resolvedNamespace(): String {
        val configured = namespace.trim()
        if (configured.isNotBlank()) {
            return configured
        }
        return project.name.toKCloudKeySegment().ifBlank { "project" }
    }

    fun resolvedProfile(): String {
        return profile.trim().ifBlank { "default" }
    }

    companion object {
        fun getInstance(project: Project): KCloudConfigCenterProjectSettings {
            return project.getService(KCloudConfigCenterProjectSettings::class.java)
        }
    }
}

internal fun String.toKCloudKeySegment(): String {
    return trim()
        .replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
        .replace(Regex("[^A-Za-z0-9]+"), "-")
        .trim('-')
        .lowercase()
}

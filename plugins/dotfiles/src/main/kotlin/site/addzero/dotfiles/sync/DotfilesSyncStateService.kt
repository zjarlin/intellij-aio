package site.addzero.dotfiles.sync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.RoamingType
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "DotfilesSyncState",
    storages = [Storage(value = "dotfiles-sync.xml", roamingType = RoamingType.DEFAULT)]
)
class DotfilesSyncStateService : PersistentStateComponent<DotfilesSyncStateService.State> {

    data class FileSnapshotState(
        var relativePath: String = "",
        var base64: String = "",
        var isBinary: Boolean = false,
        var modifiedAt: Long = 0L,
    )

    data class EntryState(
        var id: String = "",
        var path: String = "",
        var scope: String = "PROJECT_ROOT",
        var mode: String = "USER",
        var includeIgnored: Boolean = false,
        var excludeFromGit: Boolean = true,
        var files: MutableList<FileSnapshotState> = mutableListOf(),
    )

    data class ManifestState(
        var entries: MutableList<EntryState> = mutableListOf(),
        var updatedAt: Long = 0L,
    )

    data class State(
        var userManifest: ManifestState = ManifestState(),
        var projectManifests: MutableMap<String, ManifestState> = mutableMapOf(),
    )

    private val state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        fun getInstance(): DotfilesSyncStateService =
            ApplicationManager.getApplication().getService(DotfilesSyncStateService::class.java)
    }
}

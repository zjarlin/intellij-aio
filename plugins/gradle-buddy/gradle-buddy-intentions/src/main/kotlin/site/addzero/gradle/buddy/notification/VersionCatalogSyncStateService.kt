package site.addzero.gradle.buddy.notification

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks whether `libs.versions.toml` changes still need a Gradle sync.
 */
@Service(Service.Level.PROJECT)
class VersionCatalogSyncStateService {
  private val syncedStamps = ConcurrentHashMap<String, Long>()

  /**
   * Returns true if the current [VirtualFile] modification stamp differs from the
   * last synced value. The first invocation for a file seeds the stamp so no banner
   * is shown until the user edits the catalog.
   */
  fun needsSync(file: VirtualFile, currentStamp: Long): Boolean {
    val previous = syncedStamps.putIfAbsent(file.url, currentStamp)
    return previous != null && previous != currentStamp
  }

  fun markSynced(file: VirtualFile, currentStamp: Long) {
    syncedStamps[file.url] = currentStamp
  }

  companion object {
    fun getInstance(project: Project): VersionCatalogSyncStateService = project.service()
  }
}

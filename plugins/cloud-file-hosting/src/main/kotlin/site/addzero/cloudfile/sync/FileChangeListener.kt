package site.addzero.cloudfile.sync

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import site.addzero.cloudfile.settings.CloudFileSettings
import site.addzero.cloudfile.settings.ProjectHostingSettings

/**
 * Listener for file system changes
 * Triggers auto-sync when hosted files change
 */
class FileChangeListener : BulkFileListener {

    private val logger = Logger.getInstance(FileChangeListener::class.java)

    override fun before(events: List<VFileEvent>) {
        // Not needed for our use case
    }

    override fun after(events: List<VFileEvent>) {
        val settings = CloudFileSettings.getInstance()
        if (!settings.state.autoSync) return

        events.forEach { event ->
            handleEvent(event)
        }
    }

    private fun handleEvent(event: VFileEvent) {
        val file = event.file ?: return
        val project = findProjectForFile(file) ?: return

        val projectSettings = ProjectHostingSettings.getInstance(project)
        if (!projectSettings.state.enabled) return
        if (!projectSettings.state.autoSyncOnChange) return

        val syncService = CloudFileSyncService.getInstance(project)

        when (event) {
            is VFileContentChangeEvent -> {
                // File content changed
                syncService.handleFileChange(event)
            }
            is VFileCreateEvent -> {
                // New file created
                if (event.isDirectory) return
                syncService.handleFileChange(event)
            }
            is VFileDeleteEvent -> {
                // File deleted - we might want to delete from cloud too
                // But for safety, we'll require manual sync
            }
            is VFileMoveEvent -> {
                // File moved - handle as delete + create
                syncService.handleFileChange(event)
            }
            is VFileCopyEvent -> {
                // File copied
                syncService.handleFileChange(event)
            }
            is VFilePropertyChangeEvent -> {
                // Property changed (e.g., read-only status) - ignore
            }
        }
    }

    /**
     * Find which project a file belongs to
     */
    private fun findProjectForFile(file: VirtualFile): Project? {
        return ProjectManager.getInstance().openProjects.firstOrNull { project ->
            val basePath = project.basePath ?: return@firstOrNull false
            file.path.startsWith(basePath)
        }
    }
}

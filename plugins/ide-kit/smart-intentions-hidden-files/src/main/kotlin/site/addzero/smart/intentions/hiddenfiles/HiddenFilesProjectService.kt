package site.addzero.smart.intentions.hiddenfiles

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangesViewManager
import com.intellij.openapi.vcs.changes.IgnoredBeanFactory
import com.intellij.openapi.vcs.changes.IgnoredFileDescriptor
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VirtualFile

@State(
    name = "IdeKitHiddenFilesState",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class HiddenFilesProjectService(
    private val project: Project,
) : PersistentStateComponent<HiddenFilesState> {
    private var state = HiddenFilesState()

    override fun getState(): HiddenFilesState {
        return state
    }

    override fun loadState(state: HiddenFilesState) {
        this.state = HiddenPathStateSet.sanitize(state)
    }

    fun isShowHiddenFiles(): Boolean {
        return state.showHiddenFiles
    }

    fun setShowHiddenFiles(showHiddenFiles: Boolean) {
        if (state.showHiddenFiles == showHiddenFiles) {
            return
        }
        state.showHiddenFiles = showHiddenFiles
        refreshViews()
    }

    fun hide(files: Collection<VirtualFile>) {
        var changed = false
        files.asSequence()
            .filter { it.isInLocalFileSystem }
            .forEach { file ->
                changed = HiddenPathStateSet.add(state.hiddenPaths, file.path, file.isDirectory) || changed
            }
        if (changed) {
            refreshViews()
        }
    }

    fun unhide(files: Collection<VirtualFile>) {
        var changed = false
        files.asSequence()
            .filter { it.isInLocalFileSystem }
            .forEach { file ->
                changed = HiddenPathStateSet.removeAffecting(state.hiddenPaths, file.path) || changed
            }
        if (changed) {
            refreshViews()
        }
    }

    fun isMarkedHidden(file: VirtualFile): Boolean {
        return file.isInLocalFileSystem && HiddenPathStateSet.contains(state.hiddenPaths, file.path)
    }

    fun isMarkedHidden(filePath: FilePath): Boolean {
        return HiddenPathStateSet.contains(state.hiddenPaths, filePath.path)
    }

    fun shouldHide(file: VirtualFile): Boolean {
        return !state.showHiddenFiles && isMarkedHidden(file)
    }

    fun shouldHide(filePath: FilePath): Boolean {
        return !state.showHiddenFiles && isMarkedHidden(filePath)
    }

    fun getIgnoredDescriptors(): Set<IgnoredFileDescriptor> {
        if (state.showHiddenFiles) {
            return emptySet()
        }
        return state.hiddenPaths.mapTo(linkedSetOf()) { entry ->
            if (entry.directory) {
                IgnoredBeanFactory.ignoreUnderDirectory(entry.path, project)
            } else {
                IgnoredBeanFactory.ignoreFile(entry.path, project)
            }
        }
    }

    fun getIgnoredGroupDescription(): String {
        return IdeKitBundle.message("ignored.group.description")
    }

    private fun refreshViews() {
        ApplicationManager.getApplication().invokeLater(
            {
                if (project.isDisposed) {
                    return@invokeLater
                }

                ProjectView.getInstance(project).refresh()
                VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
                ChangesViewManager.getInstanceEx(project).resetViewImmediatelyAndRefreshLater()
            },
            ModalityState.any(),
        )
    }
}

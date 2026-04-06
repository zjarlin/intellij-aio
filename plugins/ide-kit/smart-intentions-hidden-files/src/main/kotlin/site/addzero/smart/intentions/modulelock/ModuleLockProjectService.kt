package site.addzero.smart.intentions.modulelock

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@State(
    name = "IdeKitModuleLockState",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class ModuleLockProjectService(
    private val project: Project,
) : PersistentStateComponent<ModuleLockState> {
    private var state = ModuleLockState()

    override fun getState(): ModuleLockState {
        return state
    }

    override fun loadState(state: ModuleLockState) {
        this.state = ModuleLockStateSet.sanitize(state)
    }

    fun isShowLockedModules(): Boolean {
        return state.showLockedModules
    }

    fun setShowLockedModules(showLockedModules: Boolean) {
        if (state.showLockedModules == showLockedModules) {
            return
        }
        state.showLockedModules = showLockedModules
        refreshProjectView()
    }

    fun lockModules(modules: Collection<Module>) {
        var changed = false
        modules.asSequence()
            .filter { module -> !module.isDisposed }
            .forEach { module ->
                val rootPaths = resolveRootPaths(module)
                if (rootPaths.isEmpty()) {
                    return@forEach
                }
                changed = ModuleLockStateSet.add(state.lockedModules, module.name, rootPaths) || changed
            }
        if (changed) {
            refreshProjectView()
        }
    }

    fun unlockModules(modules: Collection<Module>) {
        var changed = false
        modules.asSequence()
            .filter { module -> !module.isDisposed }
            .forEach { module ->
                changed = ModuleLockStateSet.remove(state.lockedModules, module.name) || changed
            }
        if (changed) {
            refreshProjectView()
        }
    }

    fun isLocked(module: Module): Boolean {
        if (module.isDisposed) {
            return false
        }
        return ModuleLockStateSet.contains(state.lockedModules, module.name)
    }

    fun shouldHide(module: Module): Boolean {
        if (state.showLockedModules || module.isDisposed) {
            return false
        }
        return isLocked(module)
    }

    fun shouldHide(file: VirtualFile): Boolean {
        if (state.showLockedModules || !file.isInLocalFileSystem) {
            return false
        }
        return ModuleLockStateSet.containsPath(state.lockedModules, file.path)
    }

    private fun resolveRootPaths(module: Module): List<String> {
        return ModuleRootManager.getInstance(module).contentRoots
            .asSequence()
            .filter { file -> file.isInLocalFileSystem }
            .map { file -> ModuleLockStateSet.normalize(file.path) }
            .filter { path -> path.isNotBlank() }
            .distinct()
            .sorted()
            .toList()
    }

    private fun refreshProjectView() {
        ApplicationManager.getApplication().invokeLater(
            {
                if (project.isDisposed) {
                    return@invokeLater
                }
                ProjectView.getInstance(project).refresh()
            },
            ModalityState.any(),
        )
    }
}

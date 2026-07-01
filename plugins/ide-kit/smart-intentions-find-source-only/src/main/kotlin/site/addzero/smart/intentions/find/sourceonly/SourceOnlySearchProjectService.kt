package site.addzero.smart.intentions.find.sourceonly

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.VirtualFile

@State(
    name = "IdeKitSourceOnlySearchState",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class SourceOnlySearchProjectService(
    private val project: Project,
) : PersistentStateComponent<SourceOnlySearchState> {
    private var state = SourceOnlySearchState()
    private val fileFilter = SourceOnlySearchFileFilter(project) {
        getExcludedFileNameSuffixes()
    }

    override fun getState(): SourceOnlySearchState {
        return state
    }

    override fun loadState(state: SourceOnlySearchState) {
        this.state = state
        this.state.excludedFileNameSuffixes = SourceOnlyFileNameSuffixFilter
            .normalize(state.excludedFileNameSuffixes)
            .toMutableList()
        syncSearchEverywhereFilterToProjectState()
    }

    fun isFilterGeneratedCodeEnabled(): Boolean {
        return state.filterGeneratedCode
    }

    fun setFilterGeneratedCode(enabled: Boolean) {
        setFilterGeneratedCode(enabled, syncSearchEverywhereFilter = true)
    }

    fun getExcludedFileNameSuffixes(): List<String> {
        return SourceOnlyFileNameSuffixFilter.normalize(state.excludedFileNameSuffixes)
    }

    fun setExcludedFileNameSuffixes(suffixes: Collection<String>) {
        val normalizedSuffixes = SourceOnlyFileNameSuffixFilter.normalize(suffixes)
        if (normalizedSuffixes == getExcludedFileNameSuffixes()) {
            return
        }
        state.excludedFileNameSuffixes = normalizedSuffixes.toMutableList()
        refreshSearchRoots()
    }

    fun isExcludedByFileNameSuffix(fileName: String): Boolean {
        return SourceOnlyFileNameSuffixFilter.matches(fileName, state.excludedFileNameSuffixes)
    }

    fun isIncludedInSourceOnlySearch(file: VirtualFile): Boolean {
        return fileFilter.contains(file)
    }

    fun syncSearchEverywhereFilterToProjectState() {
        SourceOnlySearchEverywhereFilter.setEnabled(state.filterGeneratedCode)
    }

    fun syncFromSearchEverywhereFilter() {
        val enabled = SourceOnlySearchEverywhereFilter.isEnabled()
        setFilterGeneratedCode(enabled, syncSearchEverywhereFilter = false)
    }

    private fun setFilterGeneratedCode(enabled: Boolean, syncSearchEverywhereFilter: Boolean) {
        if (syncSearchEverywhereFilter) {
            SourceOnlySearchEverywhereFilter.setEnabled(enabled)
        }
        if (state.filterGeneratedCode == enabled) {
            return
        }
        state.filterGeneratedCode = enabled
        refreshSearchRoots()
    }

    private fun refreshSearchRoots() {
        ApplicationManager.getApplication().invokeLater(
            {
                if (project.isDisposed) {
                    return@invokeLater
                }
                ProjectRootManagerEx.getInstanceEx(project)
                    .makeRootsChange({}, RootsChangeRescanningInfo.TOTAL_RESCAN)
            },
            ModalityState.nonModal(),
        )
    }

    companion object {
        const val DEFAULT_FILTER_GENERATED_CODE = true
        val DEFAULT_EXCLUDED_FILE_NAME_SUFFIXES = listOf("Draft")
    }
}

class SourceOnlySearchState {
    var filterGeneratedCode: Boolean = SourceOnlySearchProjectService.DEFAULT_FILTER_GENERATED_CODE
    var excludedFileNameSuffixes: MutableList<String> =
        SourceOnlySearchProjectService.DEFAULT_EXCLUDED_FILE_NAME_SUFFIXES.toMutableList()
}

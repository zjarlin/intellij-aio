package site.addzero.smart.intentions.find.sourceonly

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope

class SmartSourceOnlyProjectSearchScope(
    project: Project,
    private val excludedFileNameSuffixes: () -> List<String> = {
        project.getServiceIfCreated(SourceOnlySearchProjectService::class.java)
            ?.getExcludedFileNameSuffixes()
            .orEmpty()
    },
) : GlobalSearchScope(project) {
    private val fileFilter = SourceOnlySearchFileFilter(project, excludedFileNameSuffixes)

    override fun contains(file: VirtualFile): Boolean {
        return fileFilter.contains(file)
    }

    override fun compare(file1: VirtualFile, file2: VirtualFile): Int {
        return 0
    }

    override fun isSearchInModuleContent(aModule: Module): Boolean {
        return true
    }

    override fun isSearchInLibraries(): Boolean {
        return false
    }

    override fun getDisplayName(): String {
        return "源码目录"
    }
}

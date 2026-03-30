package site.addzero.smart.intentions.find.sourceonly

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope

class SmartSourceOnlyProjectSearchScope(
    project: Project,
) : GlobalSearchScope(project) {
    private val fileIndex = ProjectFileIndex.getInstance(project)

    override fun contains(file: VirtualFile): Boolean {
        if (!fileIndex.isInSourceContent(file)) {
            return false
        }
        if (fileIndex.isInGeneratedSources(file)) {
            return false
        }
        if (isUnderGeneratedOutputPath(file)) {
            return false
        }
        return true
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

    private fun isUnderGeneratedOutputPath(file: VirtualFile): Boolean {
        var current: VirtualFile? = file
        while (current != null) {
            when (current.name) {
                "build",
                "out",
                "target",
                ".gradle",
                "generated",
                -> return true
            }
            current = current.parent
        }
        return false
    }
}

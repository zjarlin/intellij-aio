package site.addzero.smart.intentions.find.sourceonly

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile

internal class SourceOnlySearchFileFilter(
    project: Project,
    private val excludedFileNameSuffixes: () -> List<String>,
) {
    private val fileIndex = ProjectFileIndex.getInstance(project)
    private val gitignoreExclusion = GitignoreSearchExclusion.fromProject(project)

    fun contains(file: VirtualFile): Boolean {
        if (!fileIndex.isInSourceContent(file)) {
            return false
        }
        if (SourceOnlyFileNameSuffixFilter.matches(file.name, excludedFileNameSuffixes())) {
            return false
        }
        if (gitignoreExclusion.isIgnored(file)) {
            return false
        }
        if (fileIndex.isInGeneratedSources(file)) {
            return false
        }
        if (isUnderGeneratedOutputPath(file)) {
            return false
        }
        if (isLogArtifact(file)) {
            return false
        }
        return true
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

    private fun isLogArtifact(file: VirtualFile): Boolean {
        var current: VirtualFile? = file
        while (current != null) {
            val lowerName = current.name.lowercase()
            if (lowerName == "log" || lowerName == "logs") {
                return true
            }
            if (!current.isDirectory && (lowerName.endsWith(".log") || lowerName.contains(".log."))) {
                return true
            }
            current = current.parent
        }
        return false
    }
}

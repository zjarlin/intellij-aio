package site.addzero.gradle.buddy.intentions.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService

object GradleProjectRoots {

    fun collectSearchRoots(project: Project): List<VirtualFile> {
        val localFileSystem = LocalFileSystem.getInstance()
        return GradleBuddySettingsService.getInstance(project)
            .collectGradleSearchRoots(project)
            .mapNotNull { root -> localFileSystem.findFileByPath(root.absolutePath) }
    }
}

package site.addzero.smart.intentions.hiddenfiles

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.IgnoredFileDescriptor
import com.intellij.openapi.vcs.changes.IgnoredFileProvider

class HiddenFilesIgnoredFileProvider : IgnoredFileProvider {
    override fun isIgnoredFile(project: Project, filePath: FilePath): Boolean {
        return project.service<HiddenFilesProjectService>().shouldHide(filePath)
    }

    override fun getIgnoredFiles(project: Project): Set<IgnoredFileDescriptor> {
        return project.service<HiddenFilesProjectService>().getIgnoredDescriptors()
    }

    override fun getIgnoredGroupDescription(): String {
        return IdeKitBundle.message("ignored.group.description")
    }
}

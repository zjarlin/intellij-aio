package site.addzero.dotfiles.ui

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.IconUtil
import site.addzero.dotfiles.sync.DotfilesSyncStateService
import javax.swing.Icon
import com.intellij.icons.AllIcons
import java.nio.file.Paths

class DotfilesFileIconProvider : FileIconProvider {
    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        val targetProject = project ?: return null
        val state = DotfilesSyncStateService.getInstance().state
        val entries = state.userManifest.entries + (state.projectManifests[targetProject.name]?.entries ?: emptyList())
        val basePath = targetProject.basePath ?: return null
        val path = Paths.get(file.path)
        val root = Paths.get(basePath)
        val isManaged = entries.any { entry ->
            val entryRoot = if (entry.scope == "USER_HOME") {
                Paths.get(System.getProperty("user.home")).resolve(entry.path)
            } else {
                root.resolve(entry.path)
            }
            path.startsWith(entryRoot)
        }
        return if (isManaged) {
            IconUtil.addText(AllIcons.Nodes.Folder, "D")
        } else {
            null
        }
    }
}

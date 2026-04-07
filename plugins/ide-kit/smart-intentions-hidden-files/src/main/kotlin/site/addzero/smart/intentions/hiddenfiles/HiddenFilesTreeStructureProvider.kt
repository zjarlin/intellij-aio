package site.addzero.smart.intentions.hiddenfiles

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import site.addzero.smart.intentions.modulelock.ModuleLockProjectService

class HiddenFilesTreeStructureProvider(
    project: Project,
) : TreeStructureProvider, DumbAware {
    private val hiddenFilesService = project.service<HiddenFilesProjectService>()
    private val moduleLockService = project.service<ModuleLockProjectService>()

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: Collection<AbstractTreeNode<*>>,
        settings: ViewSettings,
    ): Collection<AbstractTreeNode<*>> {
        if (hiddenFilesService.isShowHiddenFiles() && moduleLockService.isShowLockedModules()) {
            return children
        }

        return children.filterNot { child ->
            val module = child.value as? Module
            if (module != null && moduleLockService.shouldHide(module)) {
                return@filterNot true
            }
            val file = (child as? ProjectViewNode<*>)?.virtualFile ?: return@filterNot false
            hiddenFilesService.shouldHide(file) || moduleLockService.shouldHide(file)
        }
    }
}

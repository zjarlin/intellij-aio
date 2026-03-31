package site.addzero.smart.intentions.hiddenfiles

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

class HiddenFilesTreeStructureProvider(
    project: Project,
) : TreeStructureProvider, DumbAware {
    private val hiddenFilesService = project.service<HiddenFilesProjectService>()

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: Collection<AbstractTreeNode<*>>,
        settings: ViewSettings,
    ): Collection<AbstractTreeNode<*>> {
        if (hiddenFilesService.isShowHiddenFiles()) {
            return children
        }

        return children.filterNot { child ->
            val file = (child as? ProjectViewNode<*>)?.virtualFile ?: return@filterNot false
            hiddenFilesService.shouldHide(file)
        }
    }
}

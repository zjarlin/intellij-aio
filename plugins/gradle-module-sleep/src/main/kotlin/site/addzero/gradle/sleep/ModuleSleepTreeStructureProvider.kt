package site.addzero.gradle.sleep

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import java.io.File

class ModuleSleepTreeStructureProvider : TreeStructureProvider, DumbAware {

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: MutableCollection<AbstractTreeNode<*>>,
        settings: ViewSettings?
    ): Collection<AbstractTreeNode<*>> {
        val project = parent.project ?: return children
        val basePath = project.basePath ?: return children
        val focusedModules = project.service<GradleModuleSleepService>().getFocusedModules()
        if (focusedModules.isEmpty()) {
            return children
        }

        val visibleRoots = resolveVisibleRoots(project, focusedModules)
        if (visibleRoots.isEmpty() || visibleRoots.contains(basePath)) {
            return children
        }

        return children.filterTo(ArrayList(children.size)) { child ->
            shouldKeepNode(project, basePath, visibleRoots, child)
        }
    }

    private fun resolveVisibleRoots(project: Project, modulePaths: Set<String>): Set<String> {
        val basePath = project.basePath ?: return emptySet()
        return modulePaths.mapNotNullTo(linkedSetOf()) { modulePath ->
            when (modulePath) {
                ":" -> basePath
                else -> {
                    val relativePath = modulePath.removePrefix(":").replace(':', File.separatorChar)
                    File(basePath, relativePath).path
                }
            }
        }
    }

    private fun shouldKeepNode(
        project: Project,
        basePath: String,
        visibleRoots: Set<String>,
        child: AbstractTreeNode<*>
    ): Boolean {
        val virtualFile = extractVirtualFile(project, child) ?: return true
        val path = virtualFile.path
        if (!path.startsWith(basePath)) {
            return true
        }
        if (path == basePath) {
            return true
        }
        if (isAlwaysVisibleRootEntry(basePath, virtualFile)) {
            return true
        }

        return visibleRoots.any { root ->
            path == root || path.startsWith("$root/") || root.startsWith("$path/")
        }
    }

    private fun extractVirtualFile(project: Project, child: AbstractTreeNode<*>): VirtualFile? {
        val nodeFile = (child as? ProjectViewNode<*>)?.virtualFile
        if (nodeFile != null) {
            return nodeFile
        }

        return when (val value = child.value) {
            is VirtualFile -> value
            is PsiFileSystemItem -> value.virtualFile
            is Module -> ModuleRootManager.getInstance(value).contentRoots.firstOrNull()
            else -> null
        }
    }

    private fun isAlwaysVisibleRootEntry(basePath: String, file: VirtualFile): Boolean {
        if (file.parent?.path != basePath) {
            return false
        }
        if (!file.isDirectory) {
            return true
        }
        return ALWAYS_VISIBLE_ROOT_DIRECTORIES.contains(file.name)
    }

    private companion object {
        val ALWAYS_VISIBLE_ROOT_DIRECTORIES = setOf(
            ".idea",
            ".run",
            "gradle",
            "buildSrc",
            "build-logic"
        )
    }
}

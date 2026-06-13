package site.addzero.cargo.buddy.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import site.addzero.cargo.buddy.model.CargoCrate
import site.addzero.cargo.buddy.model.CargoCrateResolver
import java.awt.Component
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.TreePath

class CargoAutoFocusStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    ApplicationManager.getApplication().invokeLater {
                        focusCargoTree(project)
                    }
                }
            },
        )
    }

    companion object {
        private val LOG = Logger.getInstance(CargoAutoFocusStartupActivity::class.java)
        private var lastManifestPathByProject = mutableMapOf<String, String>()

        fun focusCargoTree(project: Project) {
            val cargoCrate = CargoCrateResolver.resolveCurrentCrate(project) ?: return
            val projectKey = project.locationHash
            if (lastManifestPathByProject[projectKey] == cargoCrate.manifestPath) return

            val cargoToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Cargo") ?: return
            if (!cargoToolWindow.isVisible) return

            val tree = findTreeInComponent(cargoToolWindow.component) ?: return
            lastManifestPathByProject[projectKey] = cargoCrate.manifestPath
            selectCargoNode(tree, cargoCrate)
        }

        private fun selectCargoNode(
            tree: JTree,
            cargoCrate: CargoCrate,
        ) {
            val visitor = object : TreeVisitor {
                private var seenWorkspaceRoot = false

                override fun visit(path: TreePath): TreeVisitor.Action {
                    val nodeName = path.lastPathComponent.toString()
                    if (nodeName.isBlank()) return TreeVisitor.Action.CONTINUE

                    if (isWorkspaceNode(nodeName, cargoCrate)) {
                        seenWorkspaceRoot = true
                        if (cargoCrate.rootPath == cargoCrate.workspaceRootPath) {
                            return TreeVisitor.Action.INTERRUPT
                        }
                        return TreeVisitor.Action.CONTINUE
                    }

                    if (seenWorkspaceRoot && isCrateNode(nodeName, cargoCrate)) {
                        return TreeVisitor.Action.INTERRUPT
                    }

                    return TreeVisitor.Action.CONTINUE
                }
            }

            TreeUtil.promiseSelect(tree, visitor)
                .onSuccess { selectedPath ->
                    if (selectedPath != null) {
                        tree.expandPath(selectedPath)
                        tree.scrollPathToVisible(selectedPath)
                        LOG.info("[CargoAutoFocus] Selected Cargo node: ${pathToString(selectedPath)}")
                    } else {
                        LOG.warn("[CargoAutoFocus] Cargo node not found for ${cargoCrate.displayName}")
                    }
                }
                .onError { error ->
                    LOG.warn("[CargoAutoFocus] Cargo tree selection failed: ${error.message}")
                }
        }

        private fun isWorkspaceNode(
            nodeName: String,
            cargoCrate: CargoCrate,
        ): Boolean {
            return nodeName.equals(cargoCrate.workspaceName, ignoreCase = true) ||
                nodeName.contains(cargoCrate.workspaceName, ignoreCase = true) ||
                nodeName.contains(cargoCrate.workspaceRootPath, ignoreCase = true)
        }

        private fun isCrateNode(
            nodeName: String,
            cargoCrate: CargoCrate,
        ): Boolean {
            return nodeName.equals(cargoCrate.crateName, ignoreCase = true) ||
                nodeName.contains(cargoCrate.rootPath, ignoreCase = true)
        }

        private fun pathToString(path: TreePath): String {
            return (0 until path.pathCount).joinToString(" > ") { path.getPathComponent(it).toString() }
        }

        private fun findTreeInComponent(component: Component): JTree? {
            if (component is JTree) return component
            if (component is JScrollPane) {
                val view = component.viewport?.view
                if (view is JTree) return view
            }
            if (component is java.awt.Container) {
                for (index in 0 until component.componentCount) {
                    val found = findTreeInComponent(component.getComponent(index))
                    if (found != null) return found
                }
            }
            return null
        }
    }
}

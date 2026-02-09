package site.addzero.gradle.buddy.filter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Component
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.TreePath

/**
 * Auto-focus the official Gradle tool window to the module of the currently active editor tab.
 *
 * Uses [TreeVisitor] + [TreeUtil.promiseSelect] which properly handles the async
 * lazy-loading tree model (StructureTreeModel) used by the Gradle tool window.
 * The visitor walks the tree top-down; IntelliJ automatically loads children as needed.
 */
class GradleAutoFocusStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    ApplicationManager.getApplication().invokeLater {
                        focusGradleTree(project)
                    }
                }
            }
        )
    }

    companion object {
        private val LOG = Logger.getInstance(GradleAutoFocusStartupActivity::class.java)
        private var lastModulePath: String? = null

        fun focusGradleTree(project: Project) {
            val modulePath = detectCurrentModulePath(project) ?: return
            if (modulePath == lastModulePath) return
            lastModulePath = modulePath

            val gradleToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Gradle") ?: return
            if (!gradleToolWindow.isVisible) return

            val tree = findTreeInComponent(gradleToolWindow.component) ?: return

            // Parse the Gradle module path into segments
            // e.g. ":plugins:gradle-buddy:gradle-buddy-tasks" -> ["plugins", "gradle-buddy", "gradle-buddy-tasks"]
            val segments = if (modulePath == ":") emptyList()
            else modulePath.trimStart(':').split(':')

            LOG.info("[GradleAutoFocus] Navigating to module: $modulePath (segments=$segments)")

            // Phase 1: Select the module node using TreeVisitor
            selectModuleNode(tree, segments)
        }

        /**
         * Use TreeUtil.promiseSelect with a TreeVisitor to navigate to the target module.
         *
         * TreeVisitor.visit() is called for each node top-down. We return:
         * - CONTINUE: this node is on the path, expand and check children
         * - SKIP_CHILDREN: this node is NOT on the path, skip its subtree
         * - INTERRUPT: this is the target node, select it and stop
         *
         * The Gradle tree structure (confirmed from logs):
         *   [invisible root]                              <- depth 1 (name='')
         *     addzero-lib-jvm (project group)             <- depth 2
         *       addzero-lib-jvm (main project, same name) <- depth 3
         *         lib                                      <- depth 4 ← segments[0]
         *           gradle-plugin                          <- depth 5 ← segments[1]
         *             conventions                          <- depth 6 ← segments[2]
         *               ...
         *       build-logic (composite build)              <- depth 3
         *
         * Segments start matching at depth 4 (children of the project node at depth 3).
         * Depth 1-3 are always CONTINUE (root, group, project).
         */
        private fun selectModuleNode(tree: JTree, segments: List<String>) {
            val visitor = object : TreeVisitor {
                override fun visit(path: TreePath): TreeVisitor.Action {
                    val depth = path.pathCount  // 1-based
                    val nodeName = path.lastPathComponent.toString()

                    LOG.info("[GradleAutoFocus] Visiting: depth=$depth, name='$nodeName', path=${pathToString(path)}")

                    // For root project (no segments), select the project node at depth 3
                    if (segments.isEmpty()) {
                        if (depth < 3) return TreeVisitor.Action.CONTINUE
                        if (depth == 3) {
                            LOG.info("[GradleAutoFocus] Root project match at depth $depth: '$nodeName'")
                            return TreeVisitor.Action.INTERRUPT
                        }
                        return TreeVisitor.Action.SKIP_CHILDREN
                    }

                    // depth 1: invisible root — always continue
                    // depth 2: project group — always continue
                    // depth 3: project node (same name as group) or composite build
                    //          We need to continue into the main project node to find modules
                    if (depth <= 3) {
                        return TreeVisitor.Action.CONTINUE
                    }

                    // segmentIndex: which segment this depth should match
                    // depth 4 -> segments[0], depth 5 -> segments[1], etc.
                    val segmentIndex = depth - 4

                    if (segmentIndex < 0) {
                        return TreeVisitor.Action.CONTINUE
                    }

                    if (segmentIndex >= segments.size) {
                        // We're deeper than our target — skip
                        return TreeVisitor.Action.SKIP_CHILDREN
                    }

                    val expectedSegment = segments[segmentIndex]
                    if (!nodeName.equals(expectedSegment, ignoreCase = true)) {
                        // This node doesn't match the expected segment — skip its subtree
                        LOG.info("[GradleAutoFocus] No match: expected='$expectedSegment', got='$nodeName' — skipping")
                        return TreeVisitor.Action.SKIP_CHILDREN
                    }

                    // This node matches the current segment
                    if (segmentIndex == segments.size - 1) {
                        // This is the final segment — we found our target!
                        LOG.info("[GradleAutoFocus] Full match! Module found: '$nodeName' at depth $depth")
                        return TreeVisitor.Action.INTERRUPT
                    }

                    // Partial match — continue to children for next segment
                    LOG.info("[GradleAutoFocus] Partial match: segment[$segmentIndex]='$expectedSegment' matched '$nodeName'")
                    return TreeVisitor.Action.CONTINUE
                }
            }

            TreeUtil.promiseSelect(tree, visitor)
                .onSuccess { selectedPath ->
                    if (selectedPath != null) {
                        LOG.info("[GradleAutoFocus] Module selected: ${pathToString(selectedPath)}")
                        // Phase 2: Now expand Tasks/build under the selected module
                        expandTasksBuild(tree, selectedPath)
                    } else {
                        LOG.warn("[GradleAutoFocus] promiseSelect returned null — module not found in tree")
                    }
                }
                .onError { error ->
                    LOG.warn("[GradleAutoFocus] promiseSelect failed: ${error.message}")
                }
        }

        /**
         * After selecting the module node, navigate to Tasks > build underneath it.
         */
        private fun expandTasksBuild(tree: JTree, modulePath: TreePath) {
            val moduleDepth = modulePath.pathCount

            // Use another TreeVisitor to find "Tasks" under the module
            val tasksVisitor = object : TreeVisitor {
                override fun visit(path: TreePath): TreeVisitor.Action {
                    val depth = path.pathCount
                    val nodeName = path.lastPathComponent.toString()

                    // Navigate to the module path first
                    if (depth <= moduleDepth) {
                        // Check if this path is a prefix of our module path
                        for (i in 0 until depth) {
                            if (path.getPathComponent(i) !== modulePath.getPathComponent(i)) {
                                return TreeVisitor.Action.SKIP_CHILDREN
                            }
                        }
                        return TreeVisitor.Action.CONTINUE
                    }

                    // We're inside the module subtree
                    if (depth == moduleDepth + 1) {
                        // Direct child of module — look for "Tasks"
                        if (nodeName.equals("Tasks", ignoreCase = true)) {
                            LOG.info("[GradleAutoFocus] Found 'Tasks' node, continuing to find 'build'")
                            return TreeVisitor.Action.CONTINUE
                        }
                        return TreeVisitor.Action.SKIP_CHILDREN
                    }

                    if (depth == moduleDepth + 2) {
                        // Child of Tasks — look for "build"
                        if (nodeName.equals("build", ignoreCase = true)) {
                            LOG.info("[GradleAutoFocus] Found 'Tasks > build' — selecting")
                            return TreeVisitor.Action.INTERRUPT
                        }
                        return TreeVisitor.Action.SKIP_CHILDREN
                    }

                    return TreeVisitor.Action.SKIP_CHILDREN
                }
            }

            TreeUtil.promiseSelect(tree, tasksVisitor)
                .onSuccess { selectedPath ->
                    if (selectedPath != null) {
                        LOG.info("[GradleAutoFocus] Tasks/build selected: ${pathToString(selectedPath)}")
                        // Expand the "build" node so its children (assemble, build, clean, etc.) are visible
                        tree.expandPath(selectedPath)
                    } else {
                        // "build" task group not found, try just selecting "Tasks"
                        LOG.info("[GradleAutoFocus] 'build' not found under Tasks, selecting module node instead")
                        tree.selectionPath = modulePath
                        tree.scrollPathToVisible(modulePath)
                    }
                }
                .onError { error ->
                    LOG.warn("[GradleAutoFocus] Tasks/build selection failed: ${error.message}")
                    // Fallback: just keep the module node selected
                    tree.selectionPath = modulePath
                    tree.scrollPathToVisible(modulePath)
                }
        }

        private fun pathToString(path: TreePath): String {
            return (0 until path.pathCount).joinToString(" > ") { path.getPathComponent(it).toString() }
        }

        /**
         * Detect the Gradle module path for the currently active editor file.
         * Walks up from the file's directory looking for build.gradle.kts or build.gradle.
         *
         * Returns e.g. ":plugins:gradle-buddy:gradle-buddy-tasks" or ":" for root.
         */
        private fun detectCurrentModulePath(project: Project): String? {
            val editor = FileEditorManager.getInstance(project).selectedEditor ?: return null
            val file = editor.file ?: return null
            val basePath = project.basePath ?: return null
            if (!file.path.startsWith(basePath)) return null

            var dir = file.parent
            while (dir != null && dir.path.startsWith(basePath)) {
                if (dir.findChild("build.gradle.kts") != null || dir.findChild("build.gradle") != null) {
                    val rel = dir.path.removePrefix(basePath).trimStart('/')
                    return if (rel.isEmpty()) ":" else ":${rel.replace('/', ':')}"
                }
                dir = dir.parent
            }
            return null
        }

        /**
         * Recursively find a JTree inside the Gradle tool window's component hierarchy.
         */
        private fun findTreeInComponent(component: Component): JTree? {
            if (component is JTree) return component
            if (component is JScrollPane) {
                val view = component.viewport?.view
                if (view is JTree) return view
            }
            if (component is java.awt.Container) {
                for (i in 0 until component.componentCount) {
                    val found = findTreeInComponent(component.getComponent(i))
                    if (found != null) return found
                }
            }
            return null
        }
    }
}

package site.addzero.gradle.buddy.intentions.projectdep

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

/**
 * Context menu action: copies `implementation(project(":path:to:module"))` for the current file's module.
 *
 * Registered in EditorPopupMenu and EditorTabPopupMenu.
 */
class CopyModuleDependencyAction : AnAction(
    "Copy Module Dependency",
    "Copy implementation(project(\"...\")) for current module to clipboard",
    null
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val modulePath = detectModulePath(e) ?: return

        val depString = "implementation(project(\"$modulePath\"))"
        CopyPasteManager.getInstance().setContents(StringSelection(depString))

        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification("Copied: $depString", NotificationType.INFORMATION)
            .notify(project)
    }

    override fun update(e: AnActionEvent) {
        val modulePath = detectModulePath(e)
        e.presentation.isEnabledAndVisible = modulePath != null && modulePath != ":"
    }

    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT

    /**
     * Detect the Gradle module path for the file in the current action context.
     * Walks up from the file's directory looking for build.gradle.kts or build.gradle.
     */
    private fun detectModulePath(e: AnActionEvent): String? {
        val project = e.project ?: return null
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        val basePath = project.basePath ?: return null
        if (!vFile.path.startsWith(basePath)) return null

        var dir = if (vFile.isDirectory) vFile else vFile.parent
        while (dir != null && dir.path.startsWith(basePath)) {
            if (dir.findChild("build.gradle.kts") != null || dir.findChild("build.gradle") != null) {
                val rel = dir.path.removePrefix(basePath).trimStart('/')
                return if (rel.isEmpty()) ":" else ":${rel.replace('/', ':')}"
            }
            dir = dir.parent
        }
        return null
    }
}

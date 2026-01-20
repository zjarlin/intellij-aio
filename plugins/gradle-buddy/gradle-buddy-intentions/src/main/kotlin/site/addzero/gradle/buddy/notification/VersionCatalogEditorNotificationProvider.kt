
package site.addzero.gradle.buddy.notification

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import site.addzero.gradle.buddy.GradleBuddyIcons
import java.util.function.Function
import javax.swing.JComponent

/**
 * Adds a notification to `libs.versions.toml` files suggesting to sort and deduplicate.
 */
class VersionCatalogEditorNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        if (file.name != "libs.versions.toml") {
            return null
        }

        return Function { _ ->
            val panel = EditorNotificationPanel(EditorNotificationPanel.Status.Info)
            panel.text = "Version Catalog can be sorted and deduplicated."
            panel.icon(GradleBuddyIcons.PluginIcon)

            panel.createActionLabel("Sort and Deduplicate") {
                VersionCatalogSorter(project).sort(file)
            }
            panel
        }
    }
}

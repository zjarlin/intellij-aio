package site.addzero.gradle.buddy.notification

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import site.addzero.gradle.buddy.GradleBuddyIcons
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Shows a banner on top of `libs.versions.toml` similar to the Gradle "Sync Changes"
 * indicator. It lets users trigger a Gradle refresh and optionally tidy up the catalog.
 */
class VersionCatalogEditorNotificationProvider : EditorNotificationProvider, DumbAware {

  override fun collectNotificationData(
    project: Project,
    file: VirtualFile
  ): Function<in FileEditor, out JComponent?>? {
    if (file.name != "libs.versions.toml") return null

    val properties = PropertiesComponent.getInstance(project)
    if (properties.getBoolean(VersionCatalogNotificationSettings.BANNER_DISABLED_KEY, false)) return null
    if (file.getUserData(SUPPRESSED_KEY) == true) return null

    return Function {
      createPanel(project, file, properties)
    }
  }

  private fun createPanel(
    project: Project,
    file: VirtualFile,
    propertiesComponent: PropertiesComponent
  ): JComponent {
    val appearance = EditorNotificationPanel(EditorNotificationPanel.Status.Info)
    val container = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
      border = JBUI.Borders.merge(
        JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
        JBUI.Borders.empty(2, 8),
        true
      )
      background = appearance.background
      isOpaque = true
    }

    val labelPanel = NonOpaquePanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
      add(
        JBLabel(
          "Version Catalog helpers",
          GradleBuddyIcons.VersionCatalogBanner,
          SwingConstants.LEFT
        ).apply {
          font = JBFont.medium()
          foreground = appearance.foreground
        }
      )
    }

    val actionsPanel = NonOpaquePanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(10), 0)).apply {
      addAction("Organize Version Catalog") {
        VersionCatalogSorter(project).sort(file)
        EditorNotifications.getInstance(project).updateNotifications(file)
      }

      addAction("Don't remind") {
        propertiesComponent.setValue(VersionCatalogNotificationSettings.BANNER_DISABLED_KEY, true)
        EditorNotifications.getInstance(project).updateNotifications(file)
      }

      addAction("Close") {
        file.putUserData(SUPPRESSED_KEY, true)
        EditorNotifications.getInstance(project).updateNotifications(file)
      }
    }

    container.add(labelPanel, BorderLayout.CENTER)
    container.add(actionsPanel, BorderLayout.EAST)
    return container
  }

  private fun NonOpaquePanel.addAction(text: String, action: () -> Unit) {
    val link = ActionLink(text) { action() }.apply {
      font = JBFont.medium()
      foreground = JBColor.foreground()
    }
    add(link)
  }

  companion object {
    private val SUPPRESSED_KEY = Key.create<Boolean>("GradleBuddy.VersionCatalog.Notification.Suppressed")
  }
}

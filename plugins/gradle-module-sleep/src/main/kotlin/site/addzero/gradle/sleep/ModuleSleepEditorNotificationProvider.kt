package site.addzero.gradle.sleep

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
import site.addzero.gradle.sleep.actions.ModuleSleepActionExecutor
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Lightweight banner that exposes the Sleep/Restore controls near Gradle settings files.
 */
class ModuleSleepEditorNotificationProvider : EditorNotificationProvider, DumbAware {

  override fun collectNotificationData(
    project: Project,
    file: VirtualFile
  ): Function<in FileEditor, out JComponent?>? {
    if (!file.name.equals("settings.gradle", ignoreCase = true) &&
      !file.name.equals("settings.gradle.kts", ignoreCase = true) &&
      !file.name.equals("build.gradle", ignoreCase = true) &&
      !file.name.equals("build.gradle.kts", ignoreCase = true)
    ) {
      return null
    }

    if (file.getUserData(SUPPRESSED_KEY) == true) return null

    return Function {
      createPanel(project, file)
    }
  }

  private fun createPanel(project: Project, file: VirtualFile): JComponent {
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
          "Gradle Module Sleep",
          ModuleSleepIcons.Banner,
          SwingConstants.LEFT
        ).apply {
          font = JBFont.medium()
          foreground = appearance.foreground
        }
      )
    }

    val actionsPanel = NonOpaquePanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(10), 0)).apply {
      addAction("Sleep other modules") {
        ModuleSleepActionExecutor.loadOnlyOpenTabs(project)
      }

      addAction("Restore modules") {
        ModuleSleepActionExecutor.restoreAllModules(project)
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
    private val SUPPRESSED_KEY = Key.create<Boolean>("GradleModuleSleep.Notification.Suppressed")
  }
}

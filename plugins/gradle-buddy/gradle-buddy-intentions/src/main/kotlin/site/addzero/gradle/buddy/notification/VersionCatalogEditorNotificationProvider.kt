package site.addzero.gradle.buddy.notification

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
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
import org.jetbrains.plugins.gradle.util.GradleConstants
import site.addzero.gradle.buddy.GradleBuddyIcons
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.nio.file.Files
import java.nio.file.Paths
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

    val documentManager = FileDocumentManager.getInstance()
    val notifications = EditorNotifications.getInstance(project)
    val syncState = VersionCatalogSyncStateService.getInstance(project)

    return Function { fileEditor ->
      val document = documentManager.getDocument(file) ?: return@Function null

      document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          notifications.updateNotifications(file)
        }
      }, fileEditor)

      if (!syncState.needsSync(file, document.modificationStamp)) {
        null
      } else {
        createPanel(project, file, document, syncState)
      }
    }
  }

  private fun createPanel(
    project: Project,
    file: VirtualFile,
    document: Document,
    syncState: VersionCatalogSyncStateService
  ): JComponent {
    val appearance = EditorNotificationPanel(EditorNotificationPanel.Status.Info)
    val container = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
      border = JBUI.Borders.merge(
        JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
        JBUI.Borders.empty(4, 10),
        true
      )
      isOpaque = true
      background = appearance.background
    }

    val messagePanel = NonOpaquePanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(10), 0)).apply {
      isOpaque = false
      val label = JBLabel(
        "Version Catalog changes detected. Sync Gradle to apply them.",
        GradleBuddyIcons.PluginIcon,
        SwingConstants.LEFT
      ).apply {
        font = JBFont.medium()
        foreground = appearance.foreground
      }
      add(label)
    }

    val actionsPanel = NonOpaquePanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(12), 0)).apply {
      isOpaque = false
    }

    fun addAction(text: String, action: () -> Unit) {
      val link = ActionLink(text) { action() }.apply {
        font = JBFont.medium()
        foreground = appearance.foreground
      }
      actionsPanel.add(link)
    }

    addAction("Sync Gradle changes") {
      if (syncGradle(project)) {
        syncState.markSynced(file, document.modificationStamp)
        EditorNotifications.getInstance(project).updateNotifications(file)
      }
    }

    addAction("Sort catalog") {
      VersionCatalogSorter(project).sort(file)
      EditorNotifications.getInstance(project).updateNotifications(file)
    }

    addAction("Close reminder") {
      syncState.markSynced(file, document.modificationStamp)
      EditorNotifications.getInstance(project).updateNotifications(file)
    }

    container.add(messagePanel, BorderLayout.CENTER)
    container.add(actionsPanel, BorderLayout.EAST)
    return container
  }

  private fun syncGradle(project: Project): Boolean {
    val projectPath = findGradleEntryFile(project)
    if (projectPath == null) {
      Messages.showErrorDialog(
        project,
        "Unable to locate the Gradle project directory, cannot sync.",
        "Gradle Buddy"
      )
      return false
    }

    ExternalSystemUtil.refreshProject(
      project,
      GradleConstants.SYSTEM_ID,
      projectPath,
      false,
      ProgressExecutionMode.IN_BACKGROUND_ASYNC
    )
    return true
  }

  private fun findGradleEntryFile(project: Project): String? {
    val basePath = project.basePath ?: return null
    val candidates = listOf(
      "settings.gradle.kts",
      "settings.gradle",
      "build.gradle.kts",
      "build.gradle"
    )

    return candidates
      .map { Paths.get(basePath, it) }
      .firstOrNull { Files.exists(it) }
      ?.toString()
  }
}

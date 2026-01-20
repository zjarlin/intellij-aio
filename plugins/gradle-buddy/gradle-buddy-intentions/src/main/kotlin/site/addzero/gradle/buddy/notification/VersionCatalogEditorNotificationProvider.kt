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
import org.jetbrains.plugins.gradle.util.GradleConstants
import site.addzero.gradle.buddy.GradleBuddyIcons
import java.nio.file.Files
import java.nio.file.Paths
import java.util.function.Function
import javax.swing.JComponent

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
    val panel = EditorNotificationPanel(EditorNotificationPanel.Status.Info).apply {
      text = "Version Catalog changes detected. Sync Gradle to apply them."
      icon(GradleBuddyIcons.PluginIcon)
    }

    panel.createActionLabel("Sync Gradle changes") {
      if (syncGradle(project)) {
        syncState.markSynced(file, document.modificationStamp)
        EditorNotifications.getInstance(project).updateNotifications(file)
      }
    }

    panel.createActionLabel("Sort catalog") {
      VersionCatalogSorter(project).sort(file)
      EditorNotifications.getInstance(project).updateNotifications(file)
    }

    panel.createActionLabel("Close reminder") {
      syncState.markSynced(file, document.modificationStamp)
      EditorNotifications.getInstance(project).updateNotifications(file)
    }

    return panel
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

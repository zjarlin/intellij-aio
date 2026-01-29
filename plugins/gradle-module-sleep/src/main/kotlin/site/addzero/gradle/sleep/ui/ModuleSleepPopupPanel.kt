package site.addzero.gradle.sleep.ui

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import site.addzero.gradle.sleep.ModuleSleepIcons
import site.addzero.gradle.sleep.actions.ModuleSleepActionExecutor
import site.addzero.gradle.sleep.settings.ModuleSleepSettingsService
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

object ModuleSleepPopupPanel {

  fun create(project: Project, file: VirtualFile?): JComponent {
    val container = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty(8)
      background = JBColor.PanelBackground
    }

    val titlePanel = NonOpaquePanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
      add(
        JBLabel(
          "Gradle Module Sleep",
          ModuleSleepIcons.Panel,
          SwingConstants.LEFT
        ).apply {
          font = JBFont.medium()
        }
      )
    }

    val manualFoldersField = JBTextField().apply {
      columns = 28
      emptyText.text = "Folder names, comma-separated (e.g. gradle-buddy, maven-buddy)"
      text = ModuleSleepSettingsService.getInstance(project).getManualFolderNamesRaw()
    }

    val actionsPanel = NonOpaquePanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(10), 0)).apply {
      addAction("Sleep other modules") {
        ModuleSleepActionExecutor.loadOnlyOpenTabs(project, manualFoldersField.text)
      }

      addAction("Sleep other modules (keep this file only)") {
        val fileEditorManager = FileEditorManager.getInstance(project)
        file?.let { current ->
          fileEditorManager.openFiles
            .filter { it != current }
            .forEach { fileEditorManager.closeFile(it) }
        }
        ModuleSleepSettingsService.getInstance(project).setManualFolderNames(manualFoldersField.text)
        if (file != null) {
          ModuleSleepActionExecutor.loadOnlyCurrentFile(project, file)
        } else {
          ModuleSleepActionExecutor.loadOnlyOpenTabs(project, manualFoldersField.text)
        }
      }

      addAction("Restore modules") {
        ModuleSleepActionExecutor.restoreAllModules(project)
      }
    }

    val manualPanel = NonOpaquePanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
      add(JBLabel("Folders:"))
      add(manualFoldersField)
      addAction("Save") {
        ModuleSleepSettingsService.getInstance(project).setManualFolderNames(manualFoldersField.text)
      }
    }

    val rootField = JBTextField().apply {
      columns = 28
      emptyText.text = "Root directory (relative or absolute)"
    }

    val rootPanel = NonOpaquePanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
      add(JBLabel("Root dir:"))
      add(rootField)
      addAction("Include modules under root") {
        ModuleSleepActionExecutor.loadModulesUnderRoot(project, rootField.text)
      }
    }

    val contentPanel = NonOpaquePanel(GridLayout(4, 1, 0, JBUI.scale(6))).apply {
      add(titlePanel)
      add(actionsPanel)
      add(manualPanel)
      add(rootPanel)
    }

    container.add(contentPanel, BorderLayout.CENTER)
    return container
  }

  private fun NonOpaquePanel.addAction(text: String, action: () -> Unit) {
    val link = ActionLink(text) { action() }.apply {
      font = JBFont.medium()
    }
    add(link)
  }
}

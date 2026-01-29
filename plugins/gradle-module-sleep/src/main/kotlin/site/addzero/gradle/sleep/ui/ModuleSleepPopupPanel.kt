package site.addzero.gradle.sleep.ui

import com.intellij.openapi.project.Project
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

  fun create(project: Project): JComponent {
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
      emptyText.text = "Root folder names, comma-separated (e.g. gradle-buddy, maven-buddy)"
      text = ModuleSleepSettingsService.getInstance(project).getManualFolderNamesRaw()
    }

    val manualPanel = NonOpaquePanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
      add(JBLabel("Root folders:"))
      add(manualFoldersField)
      addAction("Save + Sleep") {
        ModuleSleepSettingsService.getInstance(project).setManualFolderNames(manualFoldersField.text)
        ModuleSleepActionExecutor.loadOnlyOpenTabs(project, manualFoldersField.text)
      }
    }

    val contentPanel = NonOpaquePanel(GridLayout(2, 1, 0, JBUI.scale(6))).apply {
      add(titlePanel)
      add(manualPanel)
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

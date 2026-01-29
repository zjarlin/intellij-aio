package site.addzero.gradle.sleep.actions

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import site.addzero.gradle.sleep.settings.ModuleSleepSettingsService
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JComponent
import javax.swing.JPanel

class ModuleSleepRootsFieldAction : AnAction(), CustomComponentAction, DumbAware {

  override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
    // No-op: this action renders a custom text field component.
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val field = JBTextField().apply {
      columns = 20
      emptyText.text = "Root folders (comma-separated)"
      maximumSize = Dimension(JBUI.scale(220), JBUI.scale(24))
      minimumSize = Dimension(JBUI.scale(160), JBUI.scale(24))
      preferredSize = Dimension(JBUI.scale(200), JBUI.scale(24))
    }

    val initialProject = DataManager.getInstance().getDataContext(field)
      .getData(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT)
    if (initialProject != null) {
      field.text = ModuleSleepSettingsService.getInstance(initialProject).getManualFolderNamesRaw()
    }

    field.addActionListener {
      val project = DataManager.getInstance().getDataContext(field)
        .getData(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT) ?: return@addActionListener
      val raw = field.text
      ModuleSleepSettingsService.getInstance(project).setManualFolderNames(raw)
      ModuleSleepActionExecutor.loadOnlyOpenTabs(project, raw)
    }

    field.addFocusListener(object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) {
        val project = DataManager.getInstance().getDataContext(field)
          .getData(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT) ?: return
        ModuleSleepSettingsService.getInstance(project).setManualFolderNames(field.text)
      }
    })

    return JPanel().apply {
      isOpaque = false
      layout = java.awt.BorderLayout()
      add(field, java.awt.BorderLayout.CENTER)
    }
  }
}

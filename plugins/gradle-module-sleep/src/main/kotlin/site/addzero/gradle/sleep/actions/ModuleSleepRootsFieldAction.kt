package site.addzero.gradle.sleep.actions

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import site.addzero.gradle.sleep.GradleModuleSleepService
import site.addzero.gradle.sleep.settings.ModuleSleepSettingsService
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.event.DocumentEvent
import javax.swing.JComponent
import javax.swing.JPanel

class ModuleSleepRootsFieldAction : AnAction(), CustomComponentAction, DumbAware {

  private companion object {
    const val FIELD_PROPERTY = "moduleSleep.rootsField"
    const val PROJECT_PROPERTY = "moduleSleep.project"
  }

  override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
    // No-op: this action renders a custom text field component.
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null &&
      project.service<GradleModuleSleepService>().isFeatureAvailable() &&
      !ModuleSleepSettingsService.getInstance(project).isFloatingToolbarCollapsed()
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val field = JBTextField().apply {
      columns = 20
      emptyText.text = "Keywords (e.g. conventions)"
      maximumSize = Dimension(JBUI.scale(220), JBUI.scale(24))
      minimumSize = Dimension(JBUI.scale(160), JBUI.scale(24))
      preferredSize = Dimension(JBUI.scale(200), JBUI.scale(24))
    }

    val initialProject = DataManager.getInstance().getDataContext(field)
      .getData(CommonDataKeys.PROJECT)
    if (initialProject != null) {
      field.text = ModuleSleepSettingsService.getInstance(initialProject).getManualFolderNamesRaw()
    }

    field.addActionListener {
      val project = DataManager.getInstance().getDataContext(field)
        .getData(CommonDataKeys.PROJECT) ?: return@addActionListener
      val raw = field.text
      ModuleSleepSettingsService.getInstance(project).setManualFolderNames(raw)
      ModuleSleepActionExecutor.loadOnlyOpenTabs(project, raw)
    }

    field.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        val project = DataManager.getInstance().getDataContext(field)
          .getData(CommonDataKeys.PROJECT) ?: return
        ModuleSleepSettingsService.getInstance(project).setManualFolderNames(field.text)
      }
    })

    field.addFocusListener(object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) {
        val project = DataManager.getInstance().getDataContext(field)
          .getData(CommonDataKeys.PROJECT) ?: return
        ModuleSleepSettingsService.getInstance(project).setManualFolderNames(field.text)
      }
    })

    return JPanel().apply {
      isOpaque = false
      layout = java.awt.BorderLayout()
      putClientProperty(FIELD_PROPERTY, field)
      add(field, java.awt.BorderLayout.CENTER)
    }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    val field = component.getClientProperty(FIELD_PROPERTY) as? JBTextField ?: return
    val project = DataManager.getInstance().getDataContext(component)
      .getData(CommonDataKeys.PROJECT) ?: return

    val lastProject = component.getClientProperty(PROJECT_PROPERTY) as? com.intellij.openapi.project.Project
    val latest = ModuleSleepSettingsService.getInstance(project).getManualFolderNamesRaw()
    val shouldUpdate = lastProject != project || (!field.hasFocus() && field.text != latest)
    if (shouldUpdate) {
      component.putClientProperty(PROJECT_PROPERTY, project)
      if (!field.hasFocus()) {
        field.text = latest
      }
    }
  }
}

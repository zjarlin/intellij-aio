package site.addzero.gradle.sleep.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAware
import site.addzero.gradle.sleep.GradleModuleSleepService
import site.addzero.gradle.sleep.settings.ModuleSleepSettingsService

class HideFloatingToolbarAction : AnAction(
  "Hide This Notification",
  "Hide the floating toolbar for this project",
  AllIcons.Actions.Close
), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ModuleSleepSettingsService.getInstance(project).setFloatingToolbarEnabled(false)
    // Force all editors to refresh their floating toolbars so the change takes effect immediately.
    FileEditorManager.getInstance(project).allEditors
      .mapNotNull { (it as? TextEditor)?.editor }
      .forEach { refreshFloatingToolbar(it) }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  private fun refreshFloatingToolbar(editor: Editor) {
    val method = editor.javaClass.methods.firstOrNull {
      it.name == "refreshEditorFloatingToolbar" && it.parameterCount == 0
    }
    if (method != null) {
      method.isAccessible = true
      method.invoke(editor)
      return
    }
    editor.component.revalidate()
    editor.component.repaint()
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val enabled = project != null &&
      project.service<GradleModuleSleepService>().isFeatureAvailable() &&
      ModuleSleepSettingsService.getInstance(project).isFloatingToolbarEnabled()
    e.presentation.isEnabledAndVisible = enabled
  }
}

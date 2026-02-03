package site.addzero.gradle.sleep.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import site.addzero.gradle.sleep.GradleModuleSleepService
import site.addzero.gradle.sleep.settings.ModuleSleepSettingsService

/**
 * Action: 折叠浮动工具条
 * 点击后工具条折叠为一个展开按钮
 */
class CollapseFloatingToolbarAction : AnAction(
  "Collapse Toolbar",
  "Collapse the floating toolbar",
  AllIcons.General.ArrowLeft
), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ModuleSleepSettingsService.getInstance(project).setFloatingToolbarCollapsed(true)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val enabled = project != null &&
      project.service<GradleModuleSleepService>().isFeatureAvailable() &&
      !ModuleSleepSettingsService.getInstance(project).isFloatingToolbarCollapsed()
    e.presentation.isEnabledAndVisible = enabled
  }
}

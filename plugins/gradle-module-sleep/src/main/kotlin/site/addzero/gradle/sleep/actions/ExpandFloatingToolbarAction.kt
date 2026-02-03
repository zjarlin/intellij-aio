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
 * Action: 展开浮动工具条
 * 当工具条折叠时显示此按钮，点击后展开完整工具条
 */
class ExpandFloatingToolbarAction : AnAction(
  "Module Sleep",
  "Expand the floating toolbar",
  AllIcons.General.ArrowRight
), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ModuleSleepSettingsService.getInstance(project).setFloatingToolbarCollapsed(false)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val enabled = project != null &&
      project.service<GradleModuleSleepService>().isFeatureAvailable() &&
      ModuleSleepSettingsService.getInstance(project).isFloatingToolbarCollapsed()
    e.presentation.isEnabledAndVisible = enabled
  }
}

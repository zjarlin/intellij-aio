package site.addzero.gradle.sleep.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import site.addzero.gradle.sleep.GradleModuleSleepService
import site.addzero.gradle.sleep.settings.ModuleSleepSettingsService

/**
 * Action: 恢复所有被 Gradle Module Sleep 排除的模块
 */
class RestoreAllModulesAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!project.service<GradleModuleSleepService>().isFeatureAvailable()) return

        ModuleSleepActionExecutor.restoreAllModules(project)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null &&
            project.service<GradleModuleSleepService>().isFeatureAvailable() &&
            !ModuleSleepSettingsService.getInstance(project).isFloatingToolbarCollapsed()
    }
}

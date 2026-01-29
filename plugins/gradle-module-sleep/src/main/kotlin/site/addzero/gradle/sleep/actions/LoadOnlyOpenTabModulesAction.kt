package site.addzero.gradle.sleep.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import site.addzero.gradle.sleep.GradleModuleSleepService

/**
 * Action: 只加载当前打开标签页对应的模块
 * 流程: 获取打开的标签页 -> 推导模块 -> 生成 include 语句 -> 应用到 settings.gradle.kts -> 触发 Gradle 同步
 */
class LoadOnlyOpenTabModulesAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!project.service<GradleModuleSleepService>().isFeatureAvailable()) return

        ModuleSleepActionExecutor.loadOnlyOpenTabs(project)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null &&
            project.service<GradleModuleSleepService>().isFeatureAvailable()
    }
}

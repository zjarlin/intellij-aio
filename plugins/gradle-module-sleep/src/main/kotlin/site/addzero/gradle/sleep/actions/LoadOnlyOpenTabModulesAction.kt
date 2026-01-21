package site.addzero.gradle.sleep.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/**
 * Action: 只加载当前打开标签页对应的模块
 * 流程: 获取打开的标签页 -> 推导模块 -> 生成 include 语句 -> 应用到 settings.gradle.kts -> 触发 Gradle 同步
 */
class LoadOnlyOpenTabModulesAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ModuleSleepActionExecutor.loadOnlyOpenTabs(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

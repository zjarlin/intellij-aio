package site.addzero.smart.intentions.find.sourceonly

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware

class ToggleFilterGeneratedCodeAction : ToggleAction(
    "(ide-kit) 只搜源码",
    "在随处搜索和全局搜索中过滤生成代码",
    AllIcons.Actions.Find,
), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun isSelected(event: AnActionEvent): Boolean {
        val project = event.project ?: return SourceOnlySearchProjectService.DEFAULT_FILTER_GENERATED_CODE
        return project.service<SourceOnlySearchProjectService>().isFilterGeneratedCodeEnabled()
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
        val project = event.project ?: return
        project.service<SourceOnlySearchProjectService>().setFilterGeneratedCode(state)
    }
}

package site.addzero.vibetask.search

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.Processor
import site.addzero.vibetask.model.VibeTask
import site.addzero.vibetask.storage.VibeTaskStorage
import javax.swing.*

/**
 * Vibe Task Search Everywhere 贡献者
 * 允许用户通过 Double Shift 搜索 task 内容
 */
class VibeTaskSearchEverywhereContributor(private val project: Project) : SearchEverywhereContributor<VibeTask> {

    private val storage = ApplicationManager.getApplication().getService(VibeTaskStorage::class.java) ?: VibeTaskStorage()

    override fun getSearchProviderId(): String = "vibe.task.search"

    override fun getGroupName(): String = "Vibe Tasks"

    override fun getSortWeight(): Int = 300 // 排在文件、类之后，但比其他低优先级内容前

    override fun showInFindResults(): Boolean = true

    override fun getElementsRenderer(): ListCellRenderer<in VibeTask> {
        return VibeTaskListRenderer()
    }

    override fun processSelectedItem(selected: VibeTask, modifiers: Int, searchText: String): Boolean {
        // 打开 Vibe Task 工具窗口并选中对应任务
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Vibe Task")
        toolWindow?.show {
            // TODO: 可以在这里触发选中特定 task 的逻辑
        }
        return true
    }

    override fun fetchElements(
        pattern: String,
        progressIndicator: ProgressIndicator,
        processor: Processor<in VibeTask>
    ) {
        val tasks = storage.loadAllTasks()
        val matcher = NameUtil.buildMatcher("*$pattern*")
            .withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE)
            .build()

        tasks.forEach { task ->
            if (progressIndicator.isCanceled) return

            // 搜索 task 内容、项目名、模块名、标签
            val searchableText = buildString {
                append(task.content)
                append(" ")
                append(task.projectName)
                append(" ")
                append(task.moduleName)
                append(" ")
                append(task.tags.joinToString(" "))
            }

            if (pattern.isBlank() || matcher.matches(searchableText)) {
                if (!processor.process(task)) {
                    return
                }
            }
        }
    }

    override fun isShownInSeparateTab(): Boolean = true

    override fun isEmptyPatternSupported(): Boolean = true

    /**
     * Task 列表渲染器
     */
    private class VibeTaskListRenderer : SimpleListCellRenderer<VibeTask>() {
        override fun customize(
            list: JList<out VibeTask>,
            task: VibeTask,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ) {
            // 状态图标
            val statusIcon = when (task.status) {
                VibeTask.TaskStatus.TODO -> "⏳"
                VibeTask.TaskStatus.IN_PROGRESS -> "▶️"
                VibeTask.TaskStatus.DONE -> "✅"
                VibeTask.TaskStatus.CANCELLED -> "❌"
            }

            // 优先级图标
            val priorityIcon = when (task.priority) {
                VibeTask.Priority.HIGH -> "🔴"
                VibeTask.Priority.MEDIUM -> "🟡"
                VibeTask.Priority.LOW -> "🟢"
            }

            // 显示文本
            text = "$priorityIcon $statusIcon ${task.content}"

            // 附加信息
            val scope = when {
                task.isGlobal() -> "🌍 全局"
                task.isModuleLevel() -> "📦 ${task.moduleName}"
                else -> "📁 ${task.projectName}"
            }

            toolTipText = buildString {
                append("内容: ${task.content}\n")
                append("状态: ${task.status.name}\n")
                append("优先级: ${task.priority.name}\n")
                append("作用域: $scope")
                if (task.tags.isNotEmpty()) {
                    append("\n标签: ${task.tags.joinToString(", ")}")
                }
            }
        }
    }
}

/**
 * Vibe Task Search Everywhere 贡献者工厂
 */
class VibeTaskSearchEverywhereContributorFactory : SearchEverywhereContributorFactory<VibeTask> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<VibeTask> {
        val project = initEvent.getData(CommonDataKeys.PROJECT)
            ?: throw IllegalStateException("Project is required")
        return VibeTaskSearchEverywhereContributor(project)
    }
}

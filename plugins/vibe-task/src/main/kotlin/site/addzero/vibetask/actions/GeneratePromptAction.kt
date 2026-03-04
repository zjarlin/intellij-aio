package site.addzero.vibetask.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import site.addzero.vibetask.model.ProjectModule
import site.addzero.vibetask.model.VibeTask
import site.addzero.vibetask.service.VibeTaskService
import site.addzero.vibetask.ui.VibeTaskPanel
import java.awt.datatransfer.StringSelection

/**
 * 生成 AI 提示词 Action
 * 根据当前选中的任务或模块未完成任务生成提示词
 */
class GeneratePromptAction : AnAction(
    "生成 AI 提示词",
    "将任务生成 AI 提示词复制到剪贴板",
    com.intellij.icons.AllIcons.Actions.IntentionBulb
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = VibeTaskService.getInstance(project)

        // 获取 VibeTaskPanel 实例来检查选中的任务
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Vibe Task")
        val vibeTaskPanel = toolWindow?.contentManager?.contents?.firstOrNull()?.component as? VibeTaskPanel

        // 获取选中的任务
        val selectedTasks = vibeTaskPanel?.getSelectedTasks() ?: emptyList()

        // 获取当前模块上下文（如果面板中有）
        val currentModuleContext = vibeTaskPanel?.getCurrentModuleContext()

        val prompt = when {
            // 如果有选中的任务，生成选中任务的提示词
            selectedTasks.isNotEmpty() -> {
                generatePromptForSelectedTasks(selectedTasks, currentModuleContext)
            }
            // 否则查找当前模块的未完成任务
            currentModuleContext != null -> {
                val moduleTasks = service.getModuleTasks(currentModuleContext.path)
                    .filter { it.status != VibeTask.TaskStatus.DONE && it.status != VibeTask.TaskStatus.CANCELLED }
                if (moduleTasks.isNotEmpty()) {
                    generatePromptForModuleTasks(moduleTasks, currentModuleContext)
                } else {
                    Messages.showInfoMessage(project, "当前模块没有未完成的任务", "提示")
                    return
                }
            }
            // 最后查找项目的所有未完成任务
            else -> {
                val pendingTasks = service.getProjectTasks()
                    .filter { it.status != VibeTask.TaskStatus.DONE && it.status != VibeTask.TaskStatus.CANCELLED }
                if (pendingTasks.isNotEmpty()) {
                    generatePromptForAllTasks(pendingTasks)
                } else {
                    Messages.showInfoMessage(project, "没有未完成的任务", "提示")
                    return
                }
            }
        }

        // 复制到剪贴板
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(prompt), null)

        // 显示通知
        val taskCount = if (selectedTasks.isNotEmpty()) selectedTasks.size else {
            if (currentModuleContext != null) {
                service.getModuleTasks(currentModuleContext.path)
                    .filter { it.status != VibeTask.TaskStatus.DONE && it.status != VibeTask.TaskStatus.CANCELLED }
                    .size
            } else {
                service.getProjectTasks()
                    .filter { it.status != VibeTask.TaskStatus.DONE && it.status != VibeTask.TaskStatus.CANCELLED }
                    .size
            }
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("VibeTask Notifications")
            ?.createNotification(
                "已复制 AI 提示词 (${if (selectedTasks.isNotEmpty()) "选中${taskCount}个任务" else "${taskCount}个未完成任务"})",
                NotificationType.INFORMATION
            )
            ?.notify(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    /**
     * 为选中的任务生成提示词
     */
    private fun generatePromptForSelectedTasks(tasks: List<VibeTask>, moduleContext: ProjectModule?): String {
        return buildString {
            appendLine("# Vibe Coding 任务")
            appendLine()

            if (moduleContext != null) {
                appendLine("## 模块: ${moduleContext.name}")
                appendLine("路径: ${moduleContext.path}")
                appendLine()
            }

            appendLine("## 需要处理的任务")
            appendLine()

            tasks.forEachIndexed { index, task ->
                appendLine("${index + 1}. [${task.priority.name}] ${task.content}")
                if (task.assignees.isNotEmpty()) {
                    appendLine("   负责人: ${task.assignees.joinToString(", ")}")
                }
            }

            appendLine()
            appendLine("## 要求")
            appendLine()
            appendLine("请根据以上任务需求，提供实现方案或直接编写代码。")
            appendLine("- 优先处理高优先级任务")
            appendLine("- 保持代码风格与项目一致")
            appendLine("- 如有疑问请明确提出")
        }
    }

    /**
     * 为模块任务生成提示词
     */
    private fun generatePromptForModuleTasks(tasks: List<VibeTask>, module: ProjectModule): String {
        return buildString {
            appendLine("# Vibe Coding - ${module.name} 模块任务")
            appendLine()
            appendLine("## 模块信息")
            appendLine("- 名称: ${module.name}")
            appendLine("- 路径: ${module.path}")
            appendLine("- 类型: ${module.type}")
            appendLine()

            appendLine("## 待办任务")
            appendLine()

            // 按优先级分组
            val highPriority = tasks.filter { it.priority == VibeTask.Priority.HIGH }
            val mediumPriority = tasks.filter { it.priority == VibeTask.Priority.MEDIUM }
            val lowPriority = tasks.filter { it.priority == VibeTask.Priority.LOW }

            if (highPriority.isNotEmpty()) {
                appendLine("### 🔴 高优先级")
                highPriority.forEach { task ->
                    appendLine("- [ ] ${task.content}")
                }
                appendLine()
            }

            if (mediumPriority.isNotEmpty()) {
                appendLine("### 🟡 中优先级")
                mediumPriority.forEach { task ->
                    appendLine("- [ ] ${task.content}")
                }
                appendLine()
            }

            if (lowPriority.isNotEmpty()) {
                appendLine("### 🟢 低优先级")
                lowPriority.forEach { task ->
                    appendLine("- [ ] ${task.content}")
                }
                appendLine()
            }

            appendLine("## 实现要求")
            appendLine()
            appendLine("请针对以上任务:")
            appendLine("1. 分析当前模块的代码结构")
            appendLine("2. 提供清晰的实现思路")
            appendLine("3. 编写符合项目规范的代码")
            appendLine("4. 必要时添加注释说明")
        }
    }

    /**
     * 为所有未完成任务生成提示词
     */
    private fun generatePromptForAllTasks(tasks: List<VibeTask>): String {
        // 按模块分组
        val groupedByModule = tasks.groupBy { it.moduleName }

        return buildString {
            appendLine("# Vibe Coding - 项目任务汇总")
            appendLine()

            appendLine("## 待办任务")
            appendLine()

            // 项目级任务
            val projectTasks = groupedByModule[""] ?: emptyList()
            if (projectTasks.isNotEmpty()) {
                appendLine("### 📁 项目级任务")
                projectTasks.forEach { task ->
                    val priorityIcon = when (task.priority) {
                        VibeTask.Priority.HIGH -> "🔴"
                        VibeTask.Priority.MEDIUM -> "🟡"
                        VibeTask.Priority.LOW -> "🟢"
                    }
                    appendLine("- $priorityIcon ${task.content}")
                }
                appendLine()
            }

            // 各模块任务
            groupedByModule.filter { it.key.isNotBlank() }.forEach { (moduleName, moduleTasks) ->
                appendLine("### 📦 $moduleName")
                moduleTasks.forEach { task ->
                    val priorityIcon = when (task.priority) {
                        VibeTask.Priority.HIGH -> "🔴"
                        VibeTask.Priority.MEDIUM -> "🟡"
                        VibeTask.Priority.LOW -> "🟢"
                    }
                    appendLine("- $priorityIcon ${task.content}")
                }
                appendLine()
            }

            appendLine("## 要求")
            appendLine()
            appendLine("请按模块顺序处理以上任务，优先处理高优先级任务。")
        }
    }
}

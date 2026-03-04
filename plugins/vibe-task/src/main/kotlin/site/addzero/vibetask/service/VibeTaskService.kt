package site.addzero.vibetask.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import site.addzero.vibetask.model.ProjectModule
import site.addzero.vibetask.model.VibeTask
import site.addzero.vibetask.storage.VibeTaskStorage

@Service(Service.Level.PROJECT)
class VibeTaskService(private val project: Project) {

    private val storage = service<VibeTaskStorage>()
    private val moduleDetector = ModuleDetectorService.getInstance(project)

    val currentProjectPath: String
        get() = project.basePath ?: ""

    val currentProjectName: String
        get() = project.name

    /**
     * 获取所有检测到的模块
     */
    fun getProjectModules(): List<ProjectModule> {
        return moduleDetector.detectModules()
    }

    /**
     * 获取当前选中文件所属的模块（如果上下文中有文件）
     */
    fun getModuleForFile(filePath: String?): ProjectModule? {
        if (filePath == null) return null
        return moduleDetector.detectModuleForFile(filePath)
    }

    fun getProjectTasks(): List<VibeTask> {
        return storage.getTasksByProject(currentProjectPath)
            .sortedByDescending { it.createdAt }
    }

    /**
     * 获取指定模块的任务
     */
    fun getModuleTasks(modulePath: String): List<VibeTask> {
        return storage.getTasksByModule(modulePath)
            .sortedByDescending { it.createdAt }
    }

    fun getGlobalTasks(): List<VibeTask> {
        return storage.getGlobalTasks()
            .sortedByDescending { it.createdAt }
    }

    fun getAllTasks(): List<VibeTask> {
        return storage.loadAllTasks()
            .sortedByDescending { it.createdAt }
    }

    /**
     * 获取项目级任务（非模块级）
     */
    fun getProjectLevelTasks(): List<VibeTask> {
        return storage.getProjectLevelTasks(currentProjectPath)
            .sortedByDescending { it.createdAt }
    }

    fun addTask(
        content: String,
        isGlobal: Boolean = false,
        priority: VibeTask.Priority = VibeTask.Priority.MEDIUM,
        module: ProjectModule? = null
    ): VibeTask {
        val task = VibeTask(
            content = content.trim(),
            projectPath = if (isGlobal) "" else currentProjectPath,
            projectName = if (isGlobal) "" else currentProjectName,
            moduleName = module?.name ?: "",
            modulePath = module?.path ?: "",
            priority = priority
        )
        storage.addTask(task)
        return task
    }

    fun updateTask(task: VibeTask) {
        storage.updateTask(task)
    }

    fun deleteTask(taskId: String) {
        storage.deleteTask(taskId)
    }

    fun completeTask(task: VibeTask) {
        val updated = task.copy(
            status = VibeTask.TaskStatus.DONE,
            completedAt = System.currentTimeMillis()
        )
        storage.updateTask(updated)
    }

    fun startTask(task: VibeTask) {
        val updated = task.copy(status = VibeTask.TaskStatus.IN_PROGRESS)
        storage.updateTask(updated)
    }

    fun cancelTask(task: VibeTask) {
        val updated = task.copy(status = VibeTask.TaskStatus.CANCELLED)
        storage.updateTask(updated)
    }

    fun reopenTask(task: VibeTask) {
        val updated = task.copy(
            status = VibeTask.TaskStatus.TODO,
            completedAt = null
        )
        storage.updateTask(updated)
    }

    /**
     * 移动任务到指定模块
     */
    fun moveTaskToModule(task: VibeTask, module: ProjectModule?): VibeTask {
        val updated = task.copy(
            moduleName = module?.name ?: "",
            modulePath = module?.path ?: ""
        )
        storage.updateTask(updated)
        return updated
    }

    /**
     * 导出任务为 JSON
     */
    fun exportToJson(): String = storage.exportToJson()

    /**
     * 导出任务为 Markdown
     */
    fun exportToMarkdown(): String = storage.exportToMarkdown()

    /**
     * 从 JSON 导入任务
     */
    fun importFromJson(json: String, merge: Boolean = true): Int {
        return storage.importFromJson(json, merge)
    }

    /**
     * 获取存储文件路径
     */
    fun getStorageFilePath(): String = storage.getStorageFilePath()

    fun appendToReadme(tasks: List<VibeTask>): Boolean {
        if (tasks.isEmpty()) return false

        val readmeFile = project.basePath?.let { basePath ->
            listOf("README.md", "readme.md", "Readme.md")
                .map { java.io.File(basePath, it) }
                .firstOrNull { it.exists() }
        } ?: return false

        val completedTasks = tasks.filter { it.status == VibeTask.TaskStatus.DONE }
        if (completedTasks.isEmpty()) return false

        val content = readmeFile.readText()
        val vibeSection = buildString {
            appendLine()
            appendLine("## Vibe Coding Tasks")
            appendLine()

            // 按模块分组
            val groupedTasks = completedTasks.groupBy { it.moduleName }

            // 项目级任务（无模块）
            val projectTasks = groupedTasks[""] ?: emptyList()
            if (projectTasks.isNotEmpty()) {
                appendLine("### 📁 项目级")
                appendLine()
                appendLine("| 需求 | 状态 | 时间 |")
                appendLine("|------|------|------|")
                projectTasks.forEach { task ->
                    val date = task.completedAt?.let { java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(it)) } ?: "-"
                    appendLine("| ${task.content} | ✅ 已完成 | $date |")
                }
                appendLine()
            }

            // 各模块任务
            groupedTasks.filter { it.key.isNotBlank() }.forEach { (moduleName, moduleTasks) ->
                appendLine("### 📦 $moduleName")
                appendLine()
                appendLine("| 需求 | 状态 | 时间 |")
                appendLine("|------|------|------|")
                moduleTasks.forEach { task ->
                    val date = task.completedAt?.let { java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(it)) } ?: "-"
                    appendLine("| ${task.content} | ✅ 已完成 | $date |")
                }
                appendLine()
            }
        }

        val newContent = if (content.contains("## Vibe Coding Tasks")) {
            val before = content.substringBefore("## Vibe Coding Tasks")
            val after = content.substringAfter("## Vibe Coding Tasks")
                .let { it.substringAfter("\n## ", missingDelimiterValue = "") }
                .let { if (it.isNotEmpty()) "\n## $it" else "" }
            before + vibeSection.trimEnd() + after
        } else {
            content.trimEnd() + "\n" + vibeSection
        }

        readmeFile.writeText(newContent)
        return true
    }

    companion object {
        fun getInstance(project: Project): VibeTaskService = project.service()
    }
}

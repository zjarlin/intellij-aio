package site.addzero.vibetask.storage

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import site.addzero.vibetask.model.VibeTask
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Service
class VibeTaskStorage {
    private val logger = Logger.getInstance(VibeTaskStorage::class.java)

    private val localStorageFile: File by lazy {
        // 存储在 ~/.config/JetBrains/vibe-task/vibe-tasks.txt
        File(System.getProperty("user.home"), ".config/JetBrains/vibe-task/vibe-tasks.txt").apply {
            if (!exists()) {
                parentFile?.mkdirs()
                createNewFile()
            }
        }
    }

    @Synchronized
    fun loadAllTasks(): List<VibeTask> {
        return try {
            if (!localStorageFile.exists() || localStorageFile.length() == 0L) {
                return emptyList()
            }
            localStorageFile.readLines().mapNotNull { parseTask(it) }
        } catch (e: Exception) {
            logger.error("Failed to load vibe tasks", e)
            emptyList()
        }
    }

    @Synchronized
    fun saveAllTasks(tasks: List<VibeTask>) {
        try {
            localStorageFile.writeText(tasks.joinToString("\n") { serializeTask(it) })
        } catch (e: Exception) {
            logger.error("Failed to save vibe tasks", e)
        }
    }

    fun addTask(task: VibeTask) {
        val tasks = loadAllTasks().toMutableList()
        tasks.add(task)
        saveAllTasks(tasks)
    }

    fun updateTask(updatedTask: VibeTask) {
        val tasks = loadAllTasks().map {
            if (it.id == updatedTask.id) updatedTask else it
        }
        saveAllTasks(tasks)
    }

    fun deleteTask(taskId: String) {
        val tasks = loadAllTasks().filter { it.id != taskId }
        saveAllTasks(tasks)
    }

    fun getTasksByProject(projectPath: String): List<VibeTask> {
        return loadAllTasks().filter { it.projectPath == projectPath }
    }

    /**
     * 获取指定模块的任务
     */
    fun getTasksByModule(modulePath: String): List<VibeTask> {
        return loadAllTasks().filter { it.modulePath == modulePath }
    }

    /**
     * 获取项目级任务（非模块级）
     */
    fun getProjectLevelTasks(projectPath: String): List<VibeTask> {
        return loadAllTasks().filter { it.projectPath == projectPath && it.modulePath.isBlank() }
    }

    fun getGlobalTasks(): List<VibeTask> {
        return loadAllTasks().filter { it.isGlobal() }
    }

    /**
     * 导出所有任务为 JSON 格式
     */
    fun exportToJson(): String {
        val tasks = loadAllTasks()
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"version\": 3,")
        sb.appendLine("  \"exportTime\": ${System.currentTimeMillis()},")
        sb.appendLine("  \"taskCount\": ${tasks.size},")
        sb.appendLine("  \"tasks\": [")

        tasks.forEachIndexed { index, task ->
            sb.append("    {")
            sb.append("\"id\":\"${escapeJson(task.id)}\",")
            sb.append("\"content\":\"${escapeJson(task.content)}\",")
            sb.append("\"projectPath\":\"${escapeJson(task.projectPath)}\",")
            sb.append("\"projectName\":\"${escapeJson(task.projectName)}\",")
            sb.append("\"moduleName\":\"${escapeJson(task.moduleName)}\",")
            sb.append("\"modulePath\":\"${escapeJson(task.modulePath)}\",")
            sb.append("\"status\":\"${task.status.name}\",")
            sb.append("\"priority\":\"${task.priority.name}\",")
            sb.append("\"assignees\":[${task.assignees.joinToString(",") { "\"${escapeJson(it)}\"" }}],")
            sb.append("\"createdAt\":${task.createdAt},")
            sb.append("\"completedAt\":${task.completedAt ?: "null"},")
            sb.append("\"tags\":[${task.tags.joinToString(",") { "\"${escapeJson(it)}\"" }}]")
            sb.append("}")
            if (index < tasks.size - 1) sb.appendLine(",") else sb.appendLine()
        }

        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    /**
     * 从 JSON 导入任务
     * @param json JSON 字符串
     * @param merge 是否合并到现有任务（true=合并，false=替换）
     * @return 导入的任务数量，-1 表示解析失败
     */
    fun importFromJson(json: String, merge: Boolean = true): Int {
        return try {
            val importedTasks = parseJsonTasks(json)
            if (importedTasks.isEmpty()) return 0

            if (merge) {
                val existingTasks = loadAllTasks()
                val taskMap = existingTasks.associateBy { it.id }.toMutableMap()
                importedTasks.forEach { task ->
                    taskMap[task.id] = task
                }
                saveAllTasks(taskMap.values.toList())
            } else {
                saveAllTasks(importedTasks)
            }
            importedTasks.size
        } catch (e: Exception) {
            logger.error("Failed to import tasks from JSON", e)
            -1
        }
    }

    /**
     * 导出为 Markdown 格式（用于查看）
     */
    fun exportToMarkdown(): String {
        val tasks = loadAllTasks()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()

        sb.appendLine("# Vibe Tasks Export")
        sb.appendLine("Generated: ${dateFormat.format(Date())}")
        sb.appendLine()

        // 按状态分组
        val grouped = tasks.groupBy { it.status }

        VibeTask.TaskStatus.values().forEach { status ->
            val statusTasks = grouped[status] ?: emptyList()
            if (statusTasks.isNotEmpty()) {
                sb.appendLine("## ${getStatusEmoji(status)} ${status.name}")
                sb.appendLine()
                statusTasks.sortedByDescending { it.createdAt }.forEach { task ->
                    val priority = when (task.priority) {
                        VibeTask.Priority.HIGH -> "🔴"
                        VibeTask.Priority.MEDIUM -> "🟡"
                        VibeTask.Priority.LOW -> "🟢"
                    }
                    val scope = task.getScopeDisplay()
                    val assignee = if (task.assignees.isNotEmpty()) " 👤${task.assignees.joinToString(", ")}" else ""
                    sb.appendLine("- $priority ${task.content}")
                    sb.appendLine("  - 作用域: $scope")
                    if (assignee.isNotBlank()) {
                        sb.appendLine("  - 负责人:$assignee")
                    }
                    sb.appendLine("  - 创建: ${dateFormat.format(Date(task.createdAt))}")
                    task.completedAt?.let {
                        sb.appendLine("  - 完成: ${dateFormat.format(Date(it))}")
                    }
                    if (task.tags.isNotEmpty()) {
                        sb.appendLine("  - 标签: ${task.tags.joinToString(", ")}")
                    }
                    sb.appendLine()
                }
            }
        }

        return sb.toString()
    }

    /**
     * 获取存储文件路径（用于手动备份）
     */
    fun getStorageFilePath(): String = localStorageFile.absolutePath

    private fun getStatusEmoji(status: VibeTask.TaskStatus): String = when (status) {
        VibeTask.TaskStatus.TODO -> "⏳"
        VibeTask.TaskStatus.IN_PROGRESS -> "▶️"
        VibeTask.TaskStatus.DONE -> "✅"
        VibeTask.TaskStatus.CANCELLED -> "❌"
    }

    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun parseJsonTasks(json: String): List<VibeTask> {
        val tasks = mutableListOf<VibeTask>()

        // 简单的 JSON 对象解析
        val taskRegex = "\\{([^}]*)\\}".toRegex()
        val matches = taskRegex.findAll(json)

        matches.forEach { match ->
            val obj = match.groupValues[1]
            try {
                val task = parseTaskObject(obj)
                if (task != null) tasks.add(task)
            } catch (e: Exception) {
                logger.warn("Failed to parse task object: $obj", e)
            }
        }

        return tasks
    }

    private fun parseTaskObject(obj: String): VibeTask? {
        fun extractString(key: String): String {
            val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            return pattern.find(obj)?.groupValues?.get(1) ?: ""
        }

        fun extractLong(key: String): Long? {
            val pattern = "\"$key\"\\s*:\\s*(\\d+|null)".toRegex()
            val value = pattern.find(obj)?.groupValues?.get(1)
            return if (value == "null" || value == null) null else value.toLongOrNull()
        }

        fun extractList(key: String): List<String> {
            val pattern = "\"$key\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
            val arrayContent = pattern.find(obj)?.groupValues?.get(1) ?: return emptyList()
            return arrayContent.split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
        }

        val id = extractString("id").takeIf { it.isNotEmpty() } ?: return null

        return VibeTask(
            id = id,
            content = extractString("content"),
            projectPath = extractString("projectPath"),
            projectName = extractString("projectName"),
            moduleName = extractString("moduleName"),
            modulePath = extractString("modulePath"),
            status = runCatching { VibeTask.TaskStatus.valueOf(extractString("status")) }.getOrDefault(VibeTask.TaskStatus.TODO),
            priority = runCatching { VibeTask.Priority.valueOf(extractString("priority")) }.getOrDefault(VibeTask.Priority.MEDIUM),
            assignees = extractList("assignees"),
            createdAt = extractLong("createdAt") ?: System.currentTimeMillis(),
            completedAt = extractLong("completedAt"),
            tags = extractList("tags")
        )
    }

    private fun serializeTask(task: VibeTask): String {
        // Format: id|content|projectPath|projectName|moduleName|modulePath|status|priority|assignees|createdAt|completedAt|tags
        return listOf(
            task.id,
            task.content.replace("|", "\\|"),
            task.projectPath,
            task.projectName,
            task.moduleName,
            task.modulePath,
            task.status.name,
            task.priority.name,
            task.assignees.joinToString(","),
            task.createdAt.toString(),
            task.completedAt?.toString() ?: "",
            task.tags.joinToString(",")
        ).joinToString("|")
    }

    private fun parseTask(line: String): VibeTask? {
        return try {
            val parts = line.split("|", limit = 12)
            if (parts.size < 10) return null

            VibeTask(
                id = parts[0],
                content = parts[1].replace("\\|", "|"),
                projectPath = parts[2],
                projectName = parts[3],
                moduleName = parts.getOrNull(4) ?: "",
                modulePath = parts.getOrNull(5) ?: "",
                status = VibeTask.TaskStatus.valueOf(parts[6]),
                priority = VibeTask.Priority.valueOf(parts[7]),
                assignees = parts.getOrNull(8)?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                createdAt = parts.getOrNull(9)?.toLongOrNull() ?: System.currentTimeMillis(),
                completedAt = parts.getOrNull(10)?.takeIf { it.isNotEmpty() }?.toLongOrNull(),
                tags = parts.getOrNull(11)?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse task line: $line", e)
            null
        }
    }
}

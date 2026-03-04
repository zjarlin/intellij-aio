package site.addzero.vibetask.model

import java.util.UUID

data class VibeTask(
    val id: String = UUID.randomUUID().toString(),
    val content: String = "",
    val projectPath: String = "",
    val projectName: String = "",
    val moduleName: String = "",           // 模块名称（如 "vibe-task"）
    val modulePath: String = "",           // 模块路径（如 "plugins/vibe-task"）
    val status: TaskStatus = TaskStatus.TODO,
    val priority: Priority = Priority.MEDIUM,
    val assignees: List<String> = emptyList(),  // 负责人列表（支持多选委托）
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val tags: List<String> = emptyList()
) {
    enum class TaskStatus {
        TODO, IN_PROGRESS, DONE, CANCELLED
    }

    enum class Priority {
        LOW, MEDIUM, HIGH
    }

    fun isGlobal(): Boolean = projectPath.isBlank()

    fun isModuleLevel(): Boolean = modulePath.isNotBlank()

    fun isDelegated(): Boolean = assignees.isNotEmpty()

    fun getScopeDisplay(): String = when {
        isGlobal() -> "🌍 全局"
        isModuleLevel() -> "📦 $moduleName"
        else -> "📁 $projectName"
    }

    fun getAssigneeDisplay(): String = when {
        assignees.isEmpty() -> "未分配"
        assignees.size == 1 -> "👤 ${assignees.first()}"
        else -> "👥 ${assignees.first()} 等${assignees.size}人"
    }

    fun toReadmeLine(): String {
        val checkMark = if (status == TaskStatus.DONE) "- [x]" else "- [ ]"
        val scope = if (isModuleLevel()) " [$moduleName]" else ""
        val assignee = if (assignees.isNotEmpty()) " @${assignees.joinToString(" @")}" else ""
        val tagStr = if (tags.isNotEmpty()) tags.joinToString(" ", prefix = " ") else ""
        return "$checkMark$scope $content$assignee$tagStr"
    }
}

/**
 * 项目模块信息
 */
data class ProjectModule(
    val name: String,                      // 模块名称
    val path: String,                      // 相对于项目根目录的路径
    val type: ModuleType,                  // 模块类型
    val buildSystem: BuildSystem           // 构建系统
) {
    enum class ModuleType {
        PLUGIN, LIB, APP, UNKNOWN
    }

    enum class BuildSystem {
        GRADLE, MAVEN, NPM, UNKNOWN
    }

    fun getFullPath(projectBasePath: String): String = "$projectBasePath/$path"

    fun getDisplayName(): String = when (type) {
        ModuleType.PLUGIN -> "🔌 $name"
        ModuleType.LIB -> "📚 $name"
        ModuleType.APP -> "🚀 $name"
        ModuleType.UNKNOWN -> "📁 $name"
    }
}

/**
 * 分享目标
 */
enum class ShareTarget {
    GITHUB_GIST,      // GitHub Gist
    GITEE_GIST,       // Gitee Gist
    TEMP_LINK,        // 临时外链 (0x0.st)
    CLIPBOARD         // 仅复制到剪贴板
}

/**
 * 分享结果
 */
data class ShareResult(
    val success: Boolean,
    val url: String? = null,
    val message: String = "",
    val target: ShareTarget
)

data class VibeTaskData(
    val version: Int = 3,
    val tasks: List<VibeTask> = emptyList()
)

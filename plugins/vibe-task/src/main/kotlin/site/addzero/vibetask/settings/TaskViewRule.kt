package site.addzero.vibetask.settings

/**
 * 任务视图识别规则
 */
data class TaskViewRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",           // 规则名称（用于显示）
    val type: RuleType = RuleType.CONTAINS,
    val pattern: String = "",        // 匹配模式
    val icon: String = "📁",         // 图标
    val enabled: Boolean = true
) {
    enum class RuleType {
        CONTAINS,      // 路径包含
        STARTS_WITH,   // 路径以...开头
        ENDS_WITH,     // 路径以...结尾
        REGEX          // 正则匹配
    }

    /**
     * 检查路径是否匹配此规则
     */
    fun matches(path: String): Boolean {
        if (!enabled || pattern.isBlank()) return false
        return when (type) {
            RuleType.CONTAINS -> path.contains(pattern, ignoreCase = true)
            RuleType.STARTS_WITH -> path.startsWith(pattern, ignoreCase = true)
            RuleType.ENDS_WITH -> path.endsWith(pattern, ignoreCase = true)
            RuleType.REGEX -> path.matches(Regex(pattern, RegexOption.IGNORE_CASE))
        }
    }

    fun getTypeDisplay(): String = when (type) {
        RuleType.CONTAINS -> "包含"
        RuleType.STARTS_WITH -> "开头"
        RuleType.ENDS_WITH -> "结尾"
        RuleType.REGEX -> "正则"
    }
}

/**
 * 任务视图配置
 */
data class TaskViewConfig(
    val version: Int = 1,
    val rules: MutableList<TaskViewRule> = mutableListOf(),
    val customViews: MutableList<CustomView> = mutableListOf(),
    // 内置视图的显示/隐藏
    val showGlobalView: Boolean = true,
    val showAllTasksView: Boolean = true,
    val showProjectLevelView: Boolean = true
) {
    companion object {
        /**
         * 创建默认配置
         */
        fun createDefault(): TaskViewConfig {
            return TaskViewConfig(
                rules = mutableListOf(
                    TaskViewRule(
                        name = "插件",
                        type = TaskViewRule.RuleType.CONTAINS,
                        pattern = "plugins",
                        icon = "🔌"
                    ),
                    TaskViewRule(
                        name = "应用",
                        type = TaskViewRule.RuleType.CONTAINS,
                        pattern = "apps",
                        icon = "🚀"
                    ),
                    TaskViewRule(
                        name = "库",
                        type = TaskViewRule.RuleType.CONTAINS,
                        pattern = "lib",
                        icon = "📚"
                    )
                )
            )
        }
    }
}

/**
 * 自定义视图（用户手动创建的）
 */
data class CustomView(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val icon: String = "📁",
    val modulePaths: MutableList<String> = mutableListOf()  // 包含的模块路径
)

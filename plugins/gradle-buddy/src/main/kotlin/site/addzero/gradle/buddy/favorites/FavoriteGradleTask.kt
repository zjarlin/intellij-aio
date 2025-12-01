package site.addzero.gradle.buddy.favorites

/**
 * 收藏的 Gradle 任务
 */
data class FavoriteGradleTask(
    val projectPath: String,    // 模块路径，如 ":plugins:gradle-buddy"
    val taskName: String,       // 任务名，如 "compileKotlin"
    val group: String = "other",
    val order: Int = 0
) {
    val displayName: String
        get() = if (projectPath == ":") taskName else "$projectPath:$taskName"
    
    val fullTaskPath: String
        get() = if (projectPath == ":") ":$taskName" else "$projectPath:$taskName"
    
    fun toExecutableCommand(): String = fullTaskPath
}

package site.addzero.gitee.settings

/**
 * Persistent state for Gitee settings
 */
data class GiteeState(
    var accessToken: String = "",
    var username: String = "",
    var defaultVisibility: String = "private"
)

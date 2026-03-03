package site.addzero.gitee.api.model

/**
 * Gitee User model
 */
data class User(
    val id: Long,
    val login: String,
    val name: String?,
    val avatarUrl: String?,
    val htmlUrl: String?
)

package site.addzero.gitee.api.model

import com.google.gson.annotations.SerializedName

/**
 * Gitee Repository model
 */
data class Repo(
    val id: Long,
    val name: String,
    val path: String,
    val fullName: String,
    val description: String?,
    @SerializedName("private")
    val isPrivate: Boolean,
    val htmlUrl: String,
    val sshUrl: String,
    val cloneUrl: String,
    val defaultBranch: String,
    val owner: User
)

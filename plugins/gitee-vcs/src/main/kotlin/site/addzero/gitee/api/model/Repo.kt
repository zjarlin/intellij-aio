package site.addzero.gitee.api.model

import com.google.gson.annotations.SerializedName

/**
 * Gitee Repository model
 */
data class Repo(
    val id: Long,
    val name: String,
    val path: String,
    @SerializedName("full_name")
    val fullName: String,
    val description: String?,
    @SerializedName("private")
    val isPrivate: Boolean,
    @SerializedName("html_url")
    val htmlUrl: String,
    @SerializedName("ssh_url")
    val sshUrl: String,
    @SerializedName("clone_url")
    val cloneUrl: String,
    @SerializedName("default_branch")
    val defaultBranch: String,
    val owner: User
)

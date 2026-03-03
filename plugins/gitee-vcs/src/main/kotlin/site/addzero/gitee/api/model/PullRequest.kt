package site.addzero.gitee.api.model

/**
 * Gitee Pull Request model
 */
data class PullRequest(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String?,
    val state: String,
    val htmlUrl: String,
    val user: User,
    val head: BranchRef,
    val base: BranchRef,
    val createdAt: String,
    val updatedAt: String
)

data class BranchRef(
    val label: String,
    val ref: String,
    val sha: String,
    val repo: Repo?
)

data class CreatePullRequestRequest(
    val title: String,
    val body: String? = null,
    val head: String,
    val base: String
)

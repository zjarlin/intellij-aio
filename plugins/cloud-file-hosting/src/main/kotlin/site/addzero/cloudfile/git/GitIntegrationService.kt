package site.addzero.cloudfile.git

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import site.addzero.cloudfile.settings.GitProjectInfo
import java.io.File

/**
 * Service for extracting Git information from projects
 * Used for custom rule matching based on Git authors and project metadata
 */
class GitIntegrationService(private val project: Project) {

    private val logger = Logger.getInstance(GitIntegrationService::class.java)

    /**
     * Get Git information for the project
     */
    fun getGitInfo(): GitProjectInfo {
        val basePath = project.basePath ?: return GitProjectInfo(
            authors = emptyList(),
            projectName = project.name,
            remoteUrl = null
        )

        val gitDir = File(basePath, ".git")
        if (!gitDir.exists()) {
            return GitProjectInfo(
                authors = emptyList(),
                projectName = project.name,
                remoteUrl = null
            )
        }

        return try {
            val repository = FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build()

            val remoteUrl = extractRemoteUrl(repository)

            GitProjectInfo(
                authors = extractAuthors(repository),
                projectName = project.name,
                remoteUrl = remoteUrl
            )
        } catch (e: Exception) {
            logger.warn("Failed to extract Git info for ${project.name}", e)
            GitProjectInfo(
                authors = emptyList(),
                projectName = project.name,
                remoteUrl = null
            )
        }
    }

    /**
     * Check if project matches custom rule criteria
     */
    fun matchesCriteria(gitAuthorPattern: String, projectNamePattern: String): Boolean {
        val info = getGitInfo()

        val authorMatch = gitAuthorPattern.isBlank() ||
                info.authors.any { it.contains(gitAuthorPattern, ignoreCase = true) }

        val projectMatch = projectNamePattern.isBlank() ||
                info.projectName.contains(projectNamePattern, ignoreCase = true)

        return authorMatch && projectMatch
    }

    /**
     * Extract remote URL from repository
     */
    private fun extractRemoteUrl(repository: Repository): String? {
        return try {
            val config = repository.config
            val remotes = config.getSubsections("remote")
            if (remotes.isEmpty()) return null

            val originUrl = config.getString("remote", "origin", "url")
            originUrl ?: config.getString("remote", remotes.first(), "url")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract authors from repository history
     */
    private fun extractAuthors(repository: Repository): List<String> {
        val authors = mutableSetOf<String>()

        try {
            Git(repository).use { git ->
                val log = git.log().call()
                log.forEach { commit ->
                    val author = commit.authorIdent
                    authors.add(author.name)
                    authors.add(author.emailAddress)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to extract authors from Git history", e)
        }

        return authors.toList()
    }

    /**
     * Get all files tracked by Git (for exclusion)
     */
    fun getGitTrackedFiles(): Set<String> {
        val trackedFiles = mutableSetOf<String>()

        try {
            val basePath = project.basePath ?: return trackedFiles
            val gitDir = File(basePath, ".git")
            if (!gitDir.exists()) return trackedFiles

            val repository = FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build()

            val head = repository.resolve("HEAD") ?: return trackedFiles
            org.eclipse.jgit.revwalk.RevWalk(repository).use { walk ->
                val commit = walk.parseCommit(head)
                val tree = commit.tree

                TreeWalk(repository).use { treeWalk ->
                    treeWalk.addTree(tree)
                    treeWalk.isRecursive = true
                    while (treeWalk.next()) {
                        trackedFiles.add(treeWalk.pathString)
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to get Git tracked files", e)
        }

        return trackedFiles
    }

    /**
     * Check if a file is tracked by Git
     */
    fun isGitTracked(file: VirtualFile): Boolean {
        val basePath = project.basePath ?: return false
        val gitDir = File(basePath, ".git")
        return gitDir.exists()
    }

    companion object {
        fun getInstance(project: Project): GitIntegrationService {
            return project.getService(GitIntegrationService::class.java)
        }
    }
}

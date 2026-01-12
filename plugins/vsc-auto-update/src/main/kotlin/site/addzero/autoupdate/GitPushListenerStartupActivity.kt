package site.addzero.autoupdate

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.io.File

/**
 * Git Push Detector - Listens for .git/refs/heads changes to detect push operations
 *
 * When git push is executed, the local branch ref file in .git/refs/heads/<branch> is updated
 * We monitor these changes to trigger auto-pull BEFORE the push completes
 */
@Service(Service.Level.PROJECT)
class GitPushDetectorService(private val project: Project) : Disposable {

    private val log = Logger.getInstance(GitPushDetectorService::class.java)
    private val refCache = mutableMapOf<String, String>()
    private var isPulling = false

    fun initialize() {
        val basePath = project.basePath ?: return
        val gitDir = File(basePath, ".git")
        if (!gitDir.exists()) {
            log.debug("Not a git repository: $basePath")
            return
        }

        // Initialize ref cache
        initRefCache(gitDir)

        // Subscribe to VFS changes
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun before(events: List<VFileEvent>) {
                    events.forEach { event ->
                        handleFileEventBefore(event, gitDir)
                    }
                }

                override fun after(events: List<VFileEvent>) {
                    events.forEach { event ->
                        handleFileEventAfter(event, gitDir)
                    }
                }
            }
        )

        log.info("GitPushDetectorService initialized for $basePath")
    }

    private fun initRefCache(gitDir: File) {
        val headsDir = File(gitDir, "refs/heads")
        if (headsDir.exists()) {
            headsDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    refCache[file.absolutePath] = file.readText().trim()
                }
        }
    }

    private fun handleFileEventBefore(event: VFileEvent, gitDir: File) {
        if (isPulling) return

        val file = event.file ?: return
        val path = file.path

        // Only interested in .git/refs/heads changes (local branches)
        val headsPath = "${gitDir.absolutePath}/refs/heads"
        if (!path.startsWith(headsPath)) return

        val settings = AutoUpdateSettings.getInstance()
        if (!settings.autoPullBeforePush) return

        // When branch ref is about to change, it's likely a push operation
        // Pull first to avoid conflicts
        val currentRef = refCache[path]
        if (currentRef != null) {
            val branchName = path.removePrefix("$headsPath/")
            log.info("Push detected on branch: $branchName, pulling first...")
            performAutoPull(branchName)
        }
    }

    private fun handleFileEventAfter(event: VFileEvent, gitDir: File) {
        val file = event.file ?: return
        val path = file.path

        val headsPath = "${gitDir.absolutePath}/refs/heads"
        if (!path.startsWith(headsPath)) return

        // Update cache after event
        val newRef = File(path).takeIf { it.exists() }?.readText()?.trim()
        if (newRef != null) {
            refCache[path] = newRef
        }
    }

    private fun performAutoPull(branchName: String) {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val repository = repositoryManager.repositories
            .firstOrNull { it.currentBranchName == branchName }
            ?: repositoryManager.repositories.firstOrNull()

        if (repository == null) {
            log.warn("No git repository found for branch: $branchName")
            return
        }

        isPulling = true
        try {
            val settings = AutoUpdateSettings.getInstance()
            val result = if (settings.pullRebase) {
                executePullRebase(repository)
            } else {
                executePull(repository)
            }

            if (settings.showNotification) {
                showNotification(repository, result, branchName)
            }
        } finally {
            isPulling = false
        }
    }

    private fun executePull(repository: GitRepository): GitCommandResult {
        val project = repository.project
        val root = repository.root

        val handler = GitLineHandler(project, root, GitCommand.PULL)
        handler.setUrl(repository.remotes.firstOrNull()?.url ?: "")
        handler.addParameters("--no-commit")

        return Git.getInstance().runCommand(handler)
    }

    private fun executePullRebase(repository: GitRepository): GitCommandResult {
        val project = repository.project
        val root = repository.root

        val handler = GitLineHandler(project, root, GitCommand.PULL)
        handler.setUrl(repository.remotes.firstOrNull()?.url ?: "")
        handler.addParameters("--rebase")

        return Git.getInstance().runCommand(handler)
    }

    private fun showNotification(repository: GitRepository, result: GitCommandResult, branchName: String) {
        val notificationGroup = com.intellij.notification.NotificationGroupManager.getInstance()
            .getNotificationGroup("VSCAutoUpdate")

        if (result.success()) {
            notificationGroup?.createNotification(
                "VSC Auto Update",
                "Pulled latest changes on '$branchName'",
                com.intellij.notification.NotificationType.INFORMATION
            )?.notify(project)
        } else {
            val error = result.errorOutput.joinToString("\n").take(200)
            notificationGroup?.createNotification(
                "VSC Auto Update",
                "Pull failed on '$branchName': $error",
                com.intellij.notification.NotificationType.WARNING
            )?.notify(project)
        }
    }

    override fun dispose() {
        refCache.clear()
    }

    companion object {
        fun getInstance(project: Project): GitPushDetectorService =
            project.getService(GitPushDetectorService::class.java)
    }
}

/**
 * Project startup activity to initialize Git push detector
 */
class GitPushListenerStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        GitPushDetectorService.getInstance(project).initialize()
    }
}

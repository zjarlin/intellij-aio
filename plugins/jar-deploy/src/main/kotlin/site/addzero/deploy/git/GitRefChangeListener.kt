package site.addzero.deploy.git

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import site.addzero.deploy.*
import site.addzero.deploy.pipeline.DeployExecutor
import java.io.File

/**
 * Git Ref 变化监听器 - 通过监听 .git/refs 目录变化来检测 push 事件
 * 
 * 原理：当执行 git push 后，本地的 .git/refs/remotes/origin/<branch> 文件会被更新
 * 通过监听这些文件的变化，可以间接检测到 push 操作
 */
@Service(Service.Level.PROJECT)
class GitRefChangeService(private val project: Project) : Disposable {

    private val log = Logger.getInstance(GitRefChangeService::class.java)
    private val refCache = mutableMapOf<String, String>()

    fun initialize() {
        val basePath = project.basePath ?: return
        val gitDir = File(basePath, ".git")
        if (!gitDir.exists()) {
            log.info("Not a git repository: $basePath")
            return
        }

        // 初始化 ref 缓存
        initRefCache(gitDir)

        // 订阅文件系统变化事件
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    events.forEach { event ->
                        handleFileEvent(event, gitDir)
                    }
                }
            }
        )

        log.info("GitRefChangeService initialized for $basePath")
    }

    private fun initRefCache(gitDir: File) {
        val refsDir = File(gitDir, "refs/remotes/origin")
        if (refsDir.exists()) {
            refsDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    refCache[file.absolutePath] = file.readText().trim()
                }
        }
    }

    private fun handleFileEvent(event: VFileEvent, gitDir: File) {
        val file = event.file ?: return
        val path = file.path

        // 只关注 .git/refs/remotes/origin 目录下的变化
        val refsPath = "${gitDir.absolutePath}/refs/remotes/origin"
        if (!path.startsWith(refsPath)) return

        when (event) {
            is VFileContentChangeEvent -> {
                val newRef = File(path).takeIf { it.exists() }?.readText()?.trim() ?: return
                val oldRef = refCache[path]
                
                if (oldRef != null && oldRef != newRef) {
                    // ref 发生变化，说明有 push 操作
                    val branchName = path.removePrefix("$refsPath/")
                    log.info("Detected push on branch: $branchName (${oldRef.take(7)} -> ${newRef.take(7)})")
                    onPushDetected(branchName)
                }
                
                refCache[path] = newRef
            }
        }
    }

    private fun onPushDetected(branchName: String) {
        val settings = JarDeploySettings.getInstance(project)
        val triggers = settings.getTriggers()
            .filter { it.enabled && it.triggerType == TriggerType.GIT_PUSH }
            .filter { matchBranch(branchName, it.gitBranch ?: "") }

        if (triggers.isEmpty()) return

        triggers.forEach { trigger ->
            val targetName = trigger.targetName ?: return@forEach
            val target = settings.getTargetByName(targetName) ?: return@forEach

            val configs = settings.getConfigurations()
                .filter { it.enabled && it.targetName == targetName }

            if (configs.isEmpty()) {
                log.info("No configurations for target $targetName")
                return@forEach
            }

            configs.forEach { config ->
                log.info("Triggering deploy: ${config.name} -> $targetName")
                DeployExecutor.deploy(project, config, target)
            }
        }
    }

    private fun matchBranch(currentBranch: String, pattern: String): Boolean = when {
        pattern.isEmpty() -> true
        pattern == currentBranch -> true
        pattern.endsWith("/*") -> currentBranch.startsWith(pattern.removeSuffix("/*"))
        pattern.startsWith("*/") -> currentBranch.endsWith(pattern.removePrefix("*/"))
        pattern.contains("*") -> pattern.replace("*", ".*").toRegex().matches(currentBranch)
        else -> false
    }

    override fun dispose() {
        refCache.clear()
    }

    companion object {
        fun getInstance(project: Project): GitRefChangeService =
            project.getService(GitRefChangeService::class.java)
    }
}

/**
 * 项目启动时初始化 Git Ref 监听
 */
class GitRefChangeStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        GitRefChangeService.getInstance(project).initialize()
    }
}

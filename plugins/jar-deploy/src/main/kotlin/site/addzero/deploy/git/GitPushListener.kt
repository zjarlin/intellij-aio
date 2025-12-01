package site.addzero.deploy.git

import com.intellij.openapi.diagnostic.Logger
import git4idea.push.GitPushListener
import git4idea.push.GitPushRepoResult
import git4idea.repo.GitRepository
import site.addzero.deploy.*

/**
 * Git Push 监听器 - 在 push 成功后触发自动部署
 */
class GitPushDeployListener : GitPushListener {

    private val log = Logger.getInstance(GitPushDeployListener::class.java)

    override fun onCompleted(
        repository: GitRepository,
        pushResult: GitPushRepoResult
    ) {
        val project = repository.project
        val currentBranch = repository.currentBranch?.name ?: return
        
        if (pushResult.error != null) {
            return
        }
        
        val settings = JarDeploySettings.getInstance(project)
        val triggers = settings.getTriggers()
            .filter { it.enabled && it.triggerType == TriggerType.GIT_PUSH }
            .filter { matchBranch(currentBranch, it.gitBranch ?: "") }
        
        if (triggers.isEmpty()) {
            return
        }
        
        triggers.forEach { trigger ->
            val targetName = trigger.targetName ?: return@forEach
            val target = settings.getTargetByName(targetName) ?: return@forEach
            
            // 查找与该目标关联的配置
            val configs = settings.getConfigurations()
                .filter { it.enabled && it.targetName == targetName }
            
            if (configs.isEmpty()) {
                log.info("No configurations for target $targetName")
                return@forEach
            }
            
            configs.forEach { config ->
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
}

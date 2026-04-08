package site.addzero.gradle.buddy.notification

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleConstants
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 检测 Kotlin/Wasm 的 yarn.lock 漂移错误，并自动执行升级任务。
 */
object KotlinWasmYarnLockAutoFixSupport {

    private const val STORE_TASK_NAME = "kotlinWasmStoreYarnLock"
    private const val UPGRADE_TASK_NAME = "kotlinWasmUpgradeYarnLock"
    private const val DETECTION_MESSAGE =
        "Lock file was changed. Run the `kotlinWasmUpgradeYarnLock` task to actualize lock file"
    private const val AUTO_FIX_COOLDOWN_MS = 30_000L

    private val recentFixes = ConcurrentHashMap<String, Long>()

    fun maybeAutoFix(project: Project, output: String, gradleRootPath: String?): Boolean {
        if (!matches(output)) {
            return false
        }

        val rootPath = normalizeRootPath(gradleRootPath ?: project.basePath) ?: return false
        if (!tryAcquire(rootPath)) {
            return true
        }

        runUpgradeTask(project, rootPath)
        return true
    }

    private fun matches(output: String): Boolean {
        if (!output.contains(UPGRADE_TASK_NAME) || !output.contains(DETECTION_MESSAGE)) {
            return false
        }
        return output.contains(STORE_TASK_NAME)
    }

    private fun normalizeRootPath(path: String?): String? {
        if (path.isNullOrBlank()) {
            return null
        }
        return File(path).absoluteFile.normalize().path
    }

    @Synchronized
    private fun tryAcquire(rootPath: String): Boolean {
        val now = System.currentTimeMillis()
        val previous = recentFixes[rootPath]
        if (previous != null && now - previous < AUTO_FIX_COOLDOWN_MS) {
            return false
        }
        recentFixes[rootPath] = now
        return true
    }

    private fun runUpgradeTask(project: Project, rootPath: String) {
        val taskSettings = ExternalSystemTaskExecutionSettings().apply {
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
            externalProjectPath = rootPath
            taskNames = listOf(UPGRADE_TASK_NAME)
        }

        try {
            ExternalSystemUtil.runTask(
                taskSettings,
                DefaultRunExecutor.EXECUTOR_ID,
                project,
                GradleConstants.SYSTEM_ID
            )
            showStartedNotification(project, rootPath)
        } catch (t: Throwable) {
            showFailedNotification(project, rootPath, t)
        }
    }

    private fun showStartedNotification(project: Project, rootPath: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(
                GradleBuddyBundle.message("notification.kotlin.wasm.yarn.lock.fix.started.title"),
                GradleBuddyBundle.message(
                    "notification.kotlin.wasm.yarn.lock.fix.started.content",
                    UPGRADE_TASK_NAME,
                    rootPath
                ),
                NotificationType.INFORMATION
            )
            .notify(project)
    }

    private fun showFailedNotification(project: Project, rootPath: String, error: Throwable) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(
                GradleBuddyBundle.message("notification.kotlin.wasm.yarn.lock.fix.failed.title"),
                GradleBuddyBundle.message(
                    "notification.kotlin.wasm.yarn.lock.fix.failed.content",
                    UPGRADE_TASK_NAME,
                    rootPath,
                    error.message ?: error.javaClass.simpleName
                ),
                NotificationType.ERROR
            )
            .notify(project)
    }
}

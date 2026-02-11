package site.addzero.gradle.buddy.wrapper

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.gradle.settings.GradleSettings
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService

/**
 * 项目启动时检查 Gradle Wrapper 版本，
 * 如果不是最新版本则弹出通知提醒更新。
 */
class WrapperVersionCheckStartup : ProjectActivity {

    override suspend fun execute(project: Project) {
        // 延迟执行，不阻塞启动
        ApplicationManager.getApplication().executeOnPooledThread {
            checkWrapperVersion(project)
        }
    }

    private fun checkWrapperVersion(project: Project) {
        val latestVersion = GradleWrapperUpdater.fetchLatestVersion() ?: return

        val rootPaths = try {
            val gs = GradleSettings.getInstance(project)
            gs.linkedProjectsSettings.map { it.externalProjectPath }
        } catch (_: Throwable) {
            listOfNotNull(project.basePath)
        }

        val propsFiles = GradleWrapperUpdater.findWrapperProperties(rootPaths)
        if (propsFiles.isEmpty()) return

        val outdated = propsFiles.mapNotNull { f ->
            val url = GradleWrapperUpdater.readDistributionUrl(f) ?: return@mapNotNull null
            val ver = GradleWrapperUpdater.extractVersionFromUrl(url) ?: return@mapNotNull null
            if (ver != latestVersion) Triple(f, ver, GradleWrapperUpdater.extractTypeFromUrl(url)) else null
        }

        if (outdated.isEmpty()) return

        // 如果启用了自动更新，静默更新所有 wrapper 文件，不弹交互通知
        val settings = GradleBuddySettingsService.getInstance(project)
        if (settings.isAutoUpdateWrapper()) {
            val preferredMirror = GradleWrapperUpdater.MIRRORS.getOrElse(
                settings.getPreferredMirrorIndex()
            ) { GradleWrapperUpdater.DEFAULT_MIRROR }

            var updated = 0
            for ((f, _, type) in outdated) {
                val newUrl = GradleWrapperUpdater.buildDistributionUrl(latestVersion, type, preferredMirror)
                if (GradleWrapperUpdater.updateDistributionUrl(f, newUrl)) {
                    updated++
                    LocalFileSystem.getInstance().refreshAndFindFileByPath(f.absolutePath)
                }
            }
            if (updated > 0) {
                ApplicationManager.getApplication().invokeLater {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("GradleBuddy")
                        .createNotification(
                            "Auto-updated $updated wrapper(s) to Gradle $latestVersion",
                            "Mirror: ${preferredMirror.name}",
                            NotificationType.INFORMATION
                        ).notify(project)
                }
            }
            return
        }

        ApplicationManager.getApplication().invokeLater {
            val summary = if (outdated.size == 1) {
                val (f, ver, _) = outdated[0]
                val rel = project.basePath?.let { base ->
                    f.path.removePrefix(base.trimEnd('/', '\\') + "/")
                } ?: f.path
                "Gradle $ver → $latestVersion ($rel)"
            } else {
                "${outdated.size} wrapper(s) outdated (latest: $latestVersion)"
            }

            val notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("GradleBuddy")
                .createNotification(
                    "Gradle Wrapper Update Available",
                    summary,
                    NotificationType.INFORMATION
                )

            // 一键更新按钮（使用设置中的首选镜像）
            val preferredMirror = GradleWrapperUpdater.MIRRORS.getOrElse(
                GradleBuddySettingsService.getInstance(project).getPreferredMirrorIndex()
            ) { GradleWrapperUpdater.DEFAULT_MIRROR }

            notification.addAction(object : AnAction("Update to $latestVersion (${preferredMirror.name})") {
                override fun actionPerformed(e: AnActionEvent) {
                    var updated = 0
                    for ((f, _, type) in outdated) {
                        val newUrl = GradleWrapperUpdater.buildDistributionUrl(
                            latestVersion, type, preferredMirror
                        )
                        if (GradleWrapperUpdater.updateDistributionUrl(f, newUrl)) {
                            updated++
                            LocalFileSystem.getInstance().refreshAndFindFileByPath(f.absolutePath)
                        }
                    }
                    notification.expire()
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("GradleBuddy")
                        .createNotification(
                            "Updated $updated wrapper(s) to Gradle $latestVersion",
                            "Mirror: ${preferredMirror.name}",
                            NotificationType.INFORMATION
                        ).notify(project)
                }
            })

            notification.addAction(object : AnAction("Choose Mirror...") {
                override fun actionPerformed(e: AnActionEvent) {
                    notification.expire()
                    // 通过 ActionUtil 正确调用，避免直接调用 @OverrideOnly 的 actionPerformed
                    val action = UpdateGradleWrapperAction()
                    ActionUtil.invokeAction(action, e.dataContext, e.place, e.inputEvent, null)
                }
            })

            notification.notify(project)
        }
    }
}

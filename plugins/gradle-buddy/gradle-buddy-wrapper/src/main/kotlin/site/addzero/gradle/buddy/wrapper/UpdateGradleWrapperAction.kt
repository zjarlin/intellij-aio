package site.addzero.gradle.buddy.wrapper

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.File

/**
 * Tools 菜单操作：更新当前项目所有 gradle-wrapper.properties 的 distributionUrl
 * 为最新版本的腾讯云镜像（或用户选择的镜像）。
 */
class UpdateGradleWrapperAction : AnAction(
    "Update Gradle Wrapper (Mirror)",
    "Update all gradle-wrapper.properties to latest Gradle version using mirror",
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Checking latest Gradle version...", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                // 1. 获取最新版本
                val latestVersion = GradleWrapperUpdater.fetchLatestVersion()
                if (latestVersion == null) {
                    ApplicationManager.getApplication().invokeLater {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("GradleBuddy")
                            .createNotification(
                                "Failed to fetch latest Gradle version",
                                "Could not reach services.gradle.org. Check your network.",
                                NotificationType.ERROR
                            ).notify(project)
                    }
                    return
                }

                // 2. 收集所有 wrapper properties 文件
                indicator.text = "Scanning wrapper properties..."
                val rootPaths = try {
                    val gs = GradleSettings.getInstance(project)
                    gs.linkedProjectsSettings.map { it.externalProjectPath }
                } catch (_: Throwable) {
                    listOfNotNull(project.basePath)
                }

                val propsFiles = GradleWrapperUpdater.findWrapperProperties(rootPaths)
                if (propsFiles.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("GradleBuddy")
                            .createNotification(
                                "No gradle-wrapper.properties found",
                                NotificationType.INFORMATION
                            ).notify(project)
                    }
                    return
                }

                // 3. 分析每个文件的当前状态
                indicator.text = "Analyzing ${propsFiles.size} wrapper file(s)..."
                data class WrapperInfo(
                    val file: File,
                    val currentUrl: String?,
                    val currentVersion: String?,
                    val currentType: String,
                    val needsUpdate: Boolean
                )

                val infos = propsFiles.map { f ->
                    val url = GradleWrapperUpdater.readDistributionUrl(f)
                    val ver = url?.let { GradleWrapperUpdater.extractVersionFromUrl(it) }
                    val type = url?.let { GradleWrapperUpdater.extractTypeFromUrl(it) } ?: "bin"
                    val needsUpdate = ver != latestVersion
                    WrapperInfo(f, url, ver, type, needsUpdate)
                }

                val needUpdate = infos.filter { it.needsUpdate }
                val alreadyLatest = infos.filter { !it.needsUpdate }

                ApplicationManager.getApplication().invokeLater {
                    if (needUpdate.isEmpty()) {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("GradleBuddy")
                            .createNotification(
                                "All ${propsFiles.size} wrapper(s) already at Gradle $latestVersion",
                                NotificationType.INFORMATION
                            ).notify(project)
                        return@invokeLater
                    }

                    val summary = buildString {
                        appendLine("Latest Gradle: $latestVersion")
                        appendLine("${needUpdate.size} file(s) need update:")
                        for (info in needUpdate) {
                            val rel = project.basePath?.let { base ->
                                info.file.path.removePrefix(base.trimEnd('/', '\\') + "/")
                            } ?: info.file.path
                            appendLine("  • $rel (${info.currentVersion ?: "unknown"} → $latestVersion)")
                        }
                        if (alreadyLatest.isNotEmpty()) {
                            appendLine("${alreadyLatest.size} file(s) already up-to-date.")
                        }
                    }

                    val notification = NotificationGroupManager.getInstance()
                        .getNotificationGroup("GradleBuddy")
                        .createNotification(
                            "Gradle Wrapper Update Available",
                            summary.trim(),
                            NotificationType.WARNING
                        )

                    // 为每个镜像添加按钮
                    for (mirror in GradleWrapperUpdater.MIRRORS.take(3)) {
                        notification.addAction(object : AnAction("Use ${mirror.name}") {
                            override fun actionPerformed(e: AnActionEvent) {
                                var updated = 0
                                for (info in needUpdate) {
                                    val newUrl = GradleWrapperUpdater.buildDistributionUrl(
                                        latestVersion, info.currentType, mirror
                                    )
                                    if (GradleWrapperUpdater.updateDistributionUrl(info.file, newUrl)) {
                                        updated++
                                        // 刷新 VFS
                                        LocalFileSystem.getInstance()
                                            .refreshAndFindFileByPath(info.file.absolutePath)
                                    }
                                }
                                notification.expire()
                                NotificationGroupManager.getInstance()
                                    .getNotificationGroup("GradleBuddy")
                                    .createNotification(
                                        "Updated $updated wrapper(s) to Gradle $latestVersion",
                                        "Mirror: ${mirror.name}",
                                        NotificationType.INFORMATION
                                    ).notify(project)
                            }
                        })
                    }

                    notification.notify(project)
                }
            }
        })
    }
}

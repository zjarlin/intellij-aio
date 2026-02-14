package site.addzero.gradle.buddy.gitfixer

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 项目启动时检查是否有 build 目录文件出现在 git untracked 列表中。
 * 如果发现大量 build 文件，弹出通知提示用户修复。
 */
class GitBuildDirStartupCheck : ProjectActivity {

    override suspend fun execute(project: Project) {
        val basePath = project.basePath ?: return

        val untrackedBuildCount = withContext(Dispatchers.IO) {
            countUntrackedBuildFiles(File(basePath))
        }

        if (untrackedBuildCount > 50) {
            ApplicationManager.getApplication().invokeLater {
                val notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup("GradleBuddy")
                    .createNotification(
                        "Git Build Dir Fixer",
                        "检测到 $untrackedBuildCount 个 build 目录文件出现在未跟踪列表中，" +
                        "这会干扰你的提交列表。点击修复以清理。",
                        NotificationType.WARNING
                    )
                notification.addAction(object : AnAction("修复 .gitignore") {
                    override fun actionPerformed(e: AnActionEvent) {
                        // 直接用闭包捕获的 project，不依赖 e.project
                        BuildDirGitIgnoreFixer().execute(project)
                        notification.expire()
                    }
                })
                notification.notify(project)
            }
        }
    }

    private fun countUntrackedBuildFiles(baseDir: File): Int {
        return try {
            val process = ProcessBuilder(
                "git", "ls-files", "--others", "--exclude-standard"
            )
                .directory(baseDir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) return 0

            output.lines()
                .filter { it.isNotBlank() }
                .count { path -> path.split("/").any { it == "build" } }
        } catch (e: Exception) {
            0
        }
    }
}

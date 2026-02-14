package site.addzero.gradle.buddy.gitfixer

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

/**
 * 扫描项目中所有 build/ 目录，确保它们被 .gitignore 覆盖。
 *
 * 功能：
 * 1. 检查根 .gitignore 是否包含通配 build/ 规则
 * 2. 扫描所有子模块的 build/ 目录，检查是否被 gitignore 覆盖
 * 3. 如果根 .gitignore 缺少规则，自动补上
 * 4. 检查 git index 中是否有被 track 的 build 文件，提示执行 git rm --cached
 */
class BuildDirGitIgnoreFixer : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        execute(project)
    }

    /**
     * 可从通知 action 等外部直接调用，不依赖 AnActionEvent
     */
    fun execute(project: Project) {
        val basePath = project.basePath ?: return
        val baseDir = File(basePath)

        // Step 1: 收集所有 build 目录
        val buildDirs = findAllBuildDirs(baseDir)

        // Step 2: 检查根 .gitignore
        val rootGitignore = File(baseDir, ".gitignore")
        val gitignoreContent = if (rootGitignore.exists()) rootGitignore.readText() else ""
        val lines = gitignoreContent.lines().map { it.trim() }

        // 检查是否已有通配 build/ 规则
        val hasBuildRule = lines.any { line ->
            !line.startsWith("#") && (
                line == "build/" || line == "build" ||
                line == "**/build/" || line == "**/build" ||
                line == "/build/" || line == "/build"
            )
        }

        // Step 3: 检查 git index 中被 track 的 build 文件
        val trackedBuildFiles = findTrackedBuildFiles(baseDir)

        // Step 4: 构建报告和修复
        val sb = StringBuilder()
        var fixCount = 0

        if (!hasBuildRule) {
            // 补上 build/ 规则
            appendGitignoreRule(rootGitignore, gitignoreContent)
            sb.appendLine("✅ 已在根 .gitignore 中添加 build/ 规则")
            fixCount++

            // 刷新 VFS
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(rootGitignore)
        } else {
            sb.appendLine("✅ 根 .gitignore 已包含 build/ 规则")
        }

        sb.appendLine("📁 发现 ${buildDirs.size} 个 build 目录")

        if (trackedBuildFiles.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("⚠️ 发现 ${trackedBuildFiles.size} 个被 git track 的 build 文件")
            sb.appendLine("这些文件虽然在 .gitignore 中，但已被 git 跟踪。")
            sb.appendLine()

            val doClean = Messages.showYesNoDialog(
                project,
                sb.toString() + "是否执行 git rm --cached 清理这些文件？\n（不会删除本地文件，只是从 git 索引中移除）",
                "Git Build Dir Fixer",
                Messages.getQuestionIcon()
            )

            if (doClean == Messages.YES) {
                val cleaned = cleanTrackedBuildFiles(baseDir, trackedBuildFiles)
                Messages.showInfoMessage(
                    project,
                    "已从 git 索引中移除 $cleaned 个 build 文件。\n" +
                    "请在下次提交时一并提交 .gitignore 的变更。",
                    "Git Build Dir Fixer"
                )
            }
        } else {
            if (fixCount > 0) {
                Messages.showInfoMessage(project, sb.toString().trim(), "Git Build Dir Fixer")
            } else {
                Messages.showInfoMessage(
                    project,
                    sb.toString().trim() + "\n\n一切正常，无需修复。",
                    "Git Build Dir Fixer"
                )
            }
        }
    }

    private fun findAllBuildDirs(baseDir: File): List<File> {
        val result = mutableListOf<File>()
        findBuildDirsRecursive(baseDir, result, 0)
        return result
    }

    private fun findBuildDirsRecursive(dir: File, result: MutableList<File>, depth: Int) {
        if (depth > 20) return // 防止过深递归
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (!child.isDirectory) continue
            if (child.name == ".git" || child.name == ".gradle" || child.name == "node_modules") continue
            if (child.name == "build" && hasBuildGradleMarker(child.parentFile)) {
                result.add(child)
            } else {
                findBuildDirsRecursive(child, result, depth + 1)
            }
        }
    }

    /**
     * 判断一个目录是否是 Gradle 模块的 build 目录
     * 通过检查父目录是否有 build.gradle.kts 或 build.gradle
     */
    private fun hasBuildGradleMarker(dir: File): Boolean {
        return File(dir, "build.gradle.kts").exists() ||
               File(dir, "build.gradle").exists() ||
               // 也匹配 build-logic 等没有 build.gradle 但有 settings.gradle 的情况
               File(dir, "settings.gradle.kts").exists() ||
               File(dir, "settings.gradle").exists()
    }

    private fun appendGitignoreRule(gitignoreFile: File, existingContent: String) {
        val newContent = buildString {
            append(existingContent)
            if (existingContent.isNotEmpty() && !existingContent.endsWith("\n")) {
                append("\n")
            }
            append("\n# Gradle build directories\n")
            append("build/\n")
        }
        gitignoreFile.writeText(newContent)
    }

    private fun findTrackedBuildFiles(baseDir: File): List<String> {
        return try {
            val process = ProcessBuilder("git", "ls-files", "--cached")
                .directory(baseDir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.lines()
                .filter { it.isNotBlank() }
                .filter { isBuildDirPath(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 判断路径是否属于 build 目录
     * 匹配: build/xxx, foo/build/xxx, foo/bar/build/xxx 等
     */
    private fun isBuildDirPath(path: String): Boolean {
        val segments = path.split("/")
        return segments.any { it == "build" }
    }

    private fun cleanTrackedBuildFiles(baseDir: File, files: List<String>): Int {
        if (files.isEmpty()) return 0
        var cleaned = 0
        // 分批执行，避免命令行参数过长
        files.chunked(100).forEach { batch ->
            try {
                val cmd = mutableListOf("git", "rm", "--cached", "-r", "--quiet")
                // 用目录去重，找出所有 build/ 目录前缀
                val buildDirPrefixes = batch.map { path ->
                    val segments = path.split("/")
                    val buildIdx = segments.indexOf("build")
                    if (buildIdx >= 0) segments.take(buildIdx + 1).joinToString("/")
                    else path
                }.distinct()

                cmd.addAll(buildDirPrefixes)
                val process = ProcessBuilder(cmd)
                    .directory(baseDir)
                    .redirectErrorStream(true)
                    .start()
                process.waitFor()
                cleaned += batch.size
            } catch (_: Exception) {
            }
        }
        return cleaned
    }
}

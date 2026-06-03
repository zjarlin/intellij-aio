package site.addzero.composebuddy.run

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

data class KmpStartupRunTarget(
    val modulePath: String,
    val taskName: String,
    val externalProjectPath: String,
) {
    val fullTaskName: String =
        if (modulePath == ":") ":$taskName" else "$modulePath:$taskName"

    val configurationName: String = "KMP App $fullTaskName"
}

object KmpStartupTargetResolver {
    fun isCommonMainSourceFile(file: VirtualFile): Boolean {
        return file.path.contains("/src/commonMain/")
    }

    fun resolve(project: Project, startupFile: VirtualFile): KmpStartupRunTarget? {
        val projectRoot = project.resolveProjectRoot(startupFile) ?: return null
        val sharedModuleDir = startupFile.findNearestGradleModuleDir() ?: return null
        val sharedModulePath = sharedModuleDir.toGradlePath(projectRoot) ?: return null

        val currentModuleTarget = sharedModuleDir.resolveRunTarget(project, projectRoot, scoreBoost = 0)
        val consumerTarget = findConsumerRunTargets(project, projectRoot, sharedModuleDir, sharedModulePath)
            .maxByOrNull(KmpStartupTargetResolver::targetScore)

        return (consumerTarget ?: currentModuleTarget)?.target
    }

    private fun findConsumerRunTargets(
        project: Project,
        projectRoot: VirtualFile,
        sharedModuleDir: VirtualFile,
        sharedModulePath: String,
    ): List<ScoredRunTarget> {
        val dependencyMatchers = listOf(
            Regex("""project\(\s*["']${Regex.escape(sharedModulePath)}["']\s*\)"""),
            Regex("""\b${Regex.escape(sharedModulePath.toTypesafeProjectAccessor())}\b"""),
        )

        return buildGradleFiles(project).mapNotNull { buildFile ->
            val moduleDir = buildFile.parent ?: return@mapNotNull null
            if (moduleDir == sharedModuleDir) {
                return@mapNotNull null
            }

            val text = buildFile.loadTextOrNull() ?: return@mapNotNull null
            if (dependencyMatchers.none { it.containsMatchIn(text) }) {
                return@mapNotNull null
            }

            moduleDir.resolveRunTarget(
                project = project,
                projectRoot = projectRoot,
                scoreBoost = if (moduleDir.parent == sharedModuleDir.parent) 25 else 0,
            )
        }.toList()
    }

    private fun VirtualFile.resolveRunTarget(
        project: Project,
        projectRoot: VirtualFile,
        scoreBoost: Int,
    ): ScoredRunTarget? {
        val buildFile = findChild("build.gradle.kts") ?: findChild("build.gradle") ?: return null
        val text = buildFile.loadTextOrNull() ?: return null
        val modulePath = toGradlePath(projectRoot) ?: return null
        val externalProjectPath = projectRoot.path

        return preferredTask(text)?.let { preferredTask ->
            ScoredRunTarget(
                target = KmpStartupRunTarget(
                    modulePath = modulePath,
                    taskName = preferredTask.taskName,
                    externalProjectPath = externalProjectPath,
                ),
                score = preferredTask.score + scoreBoost,
            )
        }
    }

    private fun preferredTask(buildScriptText: String): PreferredTask? {
        val normalized = buildScriptText.lowercase()
        return when {
            normalized.contains("compose.desktop") ||
                normalized.contains("mainrun") ||
                normalized.contains("cmp-desktop") -> PreferredTask("run", 300)

            normalized.contains("wasmjs") && normalized.contains("browser") -> {
                PreferredTask("wasmJsBrowserDevelopmentRun", 200)
            }

            Regex("""\bjs\s*\{""").containsMatchIn(buildScriptText) &&
                normalized.contains("browser") -> PreferredTask("jsBrowserDevelopmentRun", 190)

            normalized.contains("com.android.application") ||
                normalized.contains("androidapp") -> PreferredTask("installDebug", 100)

            else -> null
        }
    }

    private fun buildGradleFiles(project: Project): Sequence<VirtualFile> {
        val scope = GlobalSearchScope.projectScope(project)
        return sequence {
            yieldAll(FilenameIndex.getVirtualFilesByName(project, "build.gradle.kts", scope))
            yieldAll(FilenameIndex.getVirtualFilesByName(project, "build.gradle", scope))
        }
    }

    private fun targetScore(scoredTarget: ScoredRunTarget): Int = scoredTarget.score

    private fun VirtualFile.findNearestGradleModuleDir(): VirtualFile? {
        var directory = if (isDirectory) this else parent
        while (directory != null) {
            if (directory.findChild("build.gradle.kts") != null || directory.findChild("build.gradle") != null) {
                return directory
            }
            directory = directory.parent
        }
        return null
    }

    private fun VirtualFile.toGradlePath(projectRoot: VirtualFile): String? {
        val relativePath = VfsUtilCore.getRelativePath(this, projectRoot, '/') ?: return null
        if (relativePath.isBlank()) {
            return ":"
        }

        return relativePath
            .split('/')
            .filter(String::isNotBlank)
            .joinToString(separator = ":", prefix = ":")
    }

    private fun Project.resolveProjectRoot(file: VirtualFile): VirtualFile? {
        val baseRoot = basePath?.let { basePath ->
            LocalFileSystem.getInstance().findFileByPath(basePath)
        }
        if (baseRoot != null && VfsUtilCore.isAncestor(baseRoot, file, false)) {
            return baseRoot
        }

        return ProjectRootManager.getInstance(this).contentRoots.firstOrNull { contentRoot ->
            VfsUtilCore.isAncestor(contentRoot, file, false)
        } ?: baseRoot
    }

    private fun VirtualFile.loadTextOrNull(): String? {
        return runCatching {
            VfsUtilCore.loadText(this)
        }.getOrNull()
    }

    private fun String.toTypesafeProjectAccessor(): String {
        val segments = removePrefix(":")
            .split(':')
            .filter(String::isNotBlank)
            .map { segment ->
                segment
                    .split('-', '_')
                    .filter(String::isNotBlank)
                    .mapIndexed { index, part ->
                        if (index == 0) part else part.replaceFirstChar(Char::uppercaseChar)
                    }
                    .joinToString("")
            }

        return (listOf("projects") + segments).joinToString(".")
    }
}

private data class PreferredTask(
    val taskName: String,
    val score: Int,
)

private data class ScoredRunTarget(
    val target: KmpStartupRunTarget,
    val score: Int,
)

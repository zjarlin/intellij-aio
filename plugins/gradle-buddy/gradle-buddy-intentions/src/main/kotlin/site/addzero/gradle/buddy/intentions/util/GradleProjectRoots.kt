package site.addzero.gradle.buddy.intentions.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.settings.GradleSettings
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import java.io.File
import java.util.LinkedHashSet

/**
 * 统一收集 Gradle 相关搜索根目录。
 *
 * 主要解决两类场景：
 * 1. IDEA 只打开了仓库子目录，但真正的 Gradle root 在上层目录。
 * 2. Version catalog 位于 checkouts/build-logic/gradle 这类非默认位置。
 */
object GradleProjectRoots {

    fun collectSearchRoots(project: Project): List<VirtualFile> {
        val localFileSystem = LocalFileSystem.getInstance()
        val paths = LinkedHashSet<String>()

        // 最优先使用设置中配置的版本目录路径来反推 Gradle root。
        // 这是用户显式配置的信号，比扫描目录或猜测父级更可靠。
        collectRootsFromConfiguredCatalog(project).forEach(paths::add)

        // 优先使用已经链接的 Gradle root。
        try {
            GradleSettings.getInstance(project).linkedProjectsSettings
                .mapNotNullTo(paths) { it.externalProjectPath?.takeIf(String::isNotBlank) }
        } catch (_: Throwable) {
            // 某些场景下 GradleSettings 尚不可用，直接走后续兜底逻辑。
        }

        project.basePath?.takeIf(String::isNotBlank)?.let(paths::add)
        collectAncestorGradleRoots(project.basePath).forEach(paths::add)

        return paths.mapNotNull { path ->
            localFileSystem.findFileByPath(File(path).absolutePath)
        }
    }

    private fun collectRootsFromConfiguredCatalog(project: Project): List<String> {
        val settings = GradleBuddySettingsService.getInstance(project)
        val configuredPath = settings.getVersionCatalogPath().trim()
        if (configuredPath.isBlank()) {
            return emptyList()
        }

        val result = LinkedHashSet<String>()
        val resolvedCatalogFile = settings.resolveVersionCatalogFile(project).absoluteFile

        inferRootFromConfiguredPath(resolvedCatalogFile, configuredPath)?.let(result::add)
        collectAncestorGradleRoots(resolvedCatalogFile.parentFile?.absolutePath).forEach(result::add)

        return result.toList()
    }

    private fun inferRootFromConfiguredPath(catalogFile: File, configuredPath: String): String? {
        val configuredFile = File(configuredPath)
        if (configuredFile.isAbsolute) {
            return null
        }

        val normalizedCatalogPath = catalogFile.invariantSeparatorsPath
        val normalizedConfiguredPath = configuredPath
            .replace('\\', '/')
            .trimStart('.')
            .trimStart('/')

        if (normalizedConfiguredPath.isEmpty()) {
            return null
        }

        val suffix = "/$normalizedConfiguredPath"
        if (!normalizedCatalogPath.endsWith(suffix)) {
            return null
        }

        return normalizedCatalogPath.removeSuffix(suffix).ifBlank { "/" }
    }

    private fun collectAncestorGradleRoots(basePath: String?): List<String> {
        val result = mutableListOf<String>()
        var current = basePath?.let(::File)?.absoluteFile
        var depth = 0

        while (current != null && depth < MAX_ANCESTOR_DEPTH) {
            if (current.resolve("settings.gradle.kts").exists() || current.resolve("settings.gradle").exists()) {
                result += current.absolutePath
            }
            current = current.parentFile
            depth++
        }

        return result
    }

    private const val MAX_ANCESTOR_DEPTH = 6
}

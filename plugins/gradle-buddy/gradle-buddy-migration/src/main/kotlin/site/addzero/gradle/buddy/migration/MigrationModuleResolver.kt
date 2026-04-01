package site.addzero.gradle.buddy.migration

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import org.jetbrains.plugins.gradle.settings.GradleSettings
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import java.io.File
import java.util.LinkedHashSet

internal object MigrationModuleResolver {

    data class ModuleInfo(
        val path: String,
        val buildFile: VirtualFile,
        val rootDir: VirtualFile,
        val typeSafeAccessor: String
    )

    fun findByTypeSafeAccessor(project: Project, accessor: String): ModuleInfo? {
        val normalized = accessor.removePrefix("projects.")
        return scanModules(project).firstOrNull { it.typeSafeAccessor.removePrefix("projects.") == normalized }
    }

    fun findByProjectPath(project: Project, modulePath: String): ModuleInfo? {
        return scanModules(project).firstOrNull { it.path == modulePath }
    }

    fun scanModules(project: Project): List<ModuleInfo> {
        val modules = mutableListOf<ModuleInfo>()
        val seenBuildFiles = linkedSetOf<String>()

        for (baseDir in collectSearchRoots(project)) {
            val basePath = baseDir.path
            VfsUtilCore.visitChildrenRecursively(baseDir, object : VirtualFileVisitor<Void>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (!file.isDirectory) {
                        return true
                    }

                    val name = file.name
                    if (name.startsWith(".") || name in SKIP_DIRS) {
                        return false
                    }
                    if (file != baseDir && hasSettingsFile(file)) {
                        return false
                    }

                    val buildFile = findBuildFile(file)
                    if (buildFile != null && seenBuildFiles.add(buildFile.path)) {
                        val rel = file.path.removePrefix(basePath).trimStart('/')
                        val modulePath = if (rel.isEmpty()) ":" else ":${rel.replace('/', ':')}"
                        if (modulePath != ":") {
                            modules += ModuleInfo(
                                path = modulePath,
                                buildFile = buildFile,
                                rootDir = baseDir,
                                typeSafeAccessor = modulePathToTypeSafeAccessor(modulePath)
                            )
                        }
                    }

                    return true
                }
            })
        }

        return modules
    }

    private fun collectSearchRoots(project: Project): List<VirtualFile> {
        val localFileSystem = LocalFileSystem.getInstance()
        val paths = LinkedHashSet<String>()

        collectRootsFromConfiguredCatalog(project).forEach(paths::add)

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

    private fun modulePathToTypeSafeAccessor(modulePath: String): String {
        val segments = modulePath.trim(':')
            .split(':')
            .filter { it.isNotBlank() }
            .map(::segmentToAccessor)

        return buildString {
            append("projects")
            for (segment in segments) {
                append('.')
                append(segment)
            }
        }
    }

    private fun segmentToAccessor(segment: String): String {
        val tokens = segment.split('-', '_').filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            return segment
        }

        return buildString {
            append(tokens.first().lowercase())
            for (token in tokens.drop(1)) {
                append(token.lowercase().replaceFirstChar { it.uppercase() })
            }
        }
    }

    private fun findBuildFile(dir: VirtualFile): VirtualFile? {
        return dir.findChild("build.gradle.kts") ?: dir.findChild("build.gradle")
    }

    private fun hasSettingsFile(dir: VirtualFile): Boolean {
        return dir.findChild("settings.gradle.kts") != null || dir.findChild("settings.gradle") != null
    }

    private val SKIP_DIRS = setOf("build", "out", ".gradle", "node_modules", "target", "buildSrc")
    private const val MAX_ANCESTOR_DEPTH = 6
}

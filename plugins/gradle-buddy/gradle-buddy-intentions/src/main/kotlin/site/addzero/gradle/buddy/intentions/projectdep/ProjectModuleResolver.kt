package site.addzero.gradle.buddy.intentions.projectdep

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor

object ProjectModuleResolver {

    data class ModuleInfo(
        val path: String,
        val buildFile: VirtualFile,
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
        val basePath = project.basePath ?: return emptyList()
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return emptyList()
        val modules = mutableListOf<ModuleInfo>()

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
                if (buildFile != null) {
                    val rel = file.path.removePrefix(basePath).trimStart('/')
                    val modulePath = if (rel.isEmpty()) ":" else ":${rel.replace('/', ':')}"
                    if (modulePath != ":") {
                        modules += ModuleInfo(
                            path = modulePath,
                            buildFile = buildFile,
                            typeSafeAccessor = modulePathToTypeSafeAccessor(modulePath)
                        )
                    }
                }

                return true
            }
        })

        return modules
    }

    fun modulePathToTypeSafeAccessor(modulePath: String): String {
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
}

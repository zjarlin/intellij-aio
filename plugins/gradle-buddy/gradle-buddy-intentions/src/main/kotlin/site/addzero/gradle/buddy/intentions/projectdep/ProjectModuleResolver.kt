package site.addzero.gradle.buddy.intentions.projectdep

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import site.addzero.gradle.buddy.intentions.util.GradleProjectRoots

object ProjectModuleResolver {

    data class ModuleInfo(
        val path: String,
        val buildFile: VirtualFile,
        val rootDir: VirtualFile,
        val typeSafeAccessor: String
    )

    fun findByTypeSafeAccessor(project: Project, accessor: String, anchorFile: VirtualFile? = null): ModuleInfo? {
        val normalized = accessor.removePrefix("projects.")
        val matched = scanModules(project).filter { it.typeSafeAccessor.removePrefix("projects.") == normalized }
        return chooseBestMatch(project, matched, anchorFile)
    }

    fun findByProjectPath(project: Project, modulePath: String, anchorFile: VirtualFile? = null): ModuleInfo? {
        val matched = scanModules(project).filter { it.path == modulePath }
        return chooseBestMatch(project, matched, anchorFile)
    }

    fun scanModules(project: Project): List<ModuleInfo> {
        val modules = mutableListOf<ModuleInfo>()
        val seenBuildFiles = linkedSetOf<String>()

        for (baseDir in GradleProjectRoots.collectSearchRoots(project)) {
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

    fun findOwningRoot(project: Project, file: VirtualFile?): VirtualFile? {
        val targetPath = file?.path ?: return null
        return site.addzero.gradle.buddy.intentions.util.GradleProjectRoots.collectSearchRoots(project)
            .filter { root ->
                targetPath == root.path || targetPath.startsWith(root.path.trimEnd('/') + "/")
            }
            .maxByOrNull { it.path.length }
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

    private fun chooseBestMatch(
        project: Project,
        candidates: List<ModuleInfo>,
        anchorFile: VirtualFile?
    ): ModuleInfo? {
        if (candidates.isEmpty()) {
            return null
        }
        val owningRoot = findOwningRoot(project, anchorFile)
        return candidates.firstOrNull { candidate ->
            owningRoot != null && candidate.rootDir.path == owningRoot.path
        } ?: candidates.first()
    }

    private val SKIP_DIRS = setOf("build", "out", ".gradle", "node_modules", "target", "buildSrc")
}

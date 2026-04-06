package site.addzero.gradle.buddy.filter

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import site.addzero.gradle.buddy.util.GradleCommandLineUtil

internal object GradlePublishSelectionResolver {

    data class PublishTarget(
        val moduleDir: VirtualFile,
        val modulePath: String,
        val rootPath: String,
    ) {
        val taskPath: String
            get() = GradleCommandLineUtil.publishToMavenCentralTaskPath(modulePath)

        val command: String
            get() = GradleCommandLineUtil.publishToMavenCentralCommand(modulePath)
    }

    fun canResolve(project: Project, selections: List<VirtualFile>): Boolean {
        if (selections.isEmpty()) {
            return false
        }

        val roots = collectSearchRoots(project)
        return selections.any { selection ->
            roots.any { root ->
                selection.path.startsWith(root.path) || root.path.startsWith(selection.path)
            }
        }
    }

    fun resolve(project: Project, selections: List<VirtualFile>): List<PublishTarget> {
        if (selections.isEmpty()) {
            return emptyList()
        }

        val roots = collectSearchRoots(project)
        if (roots.isEmpty()) {
            return emptyList()
        }

        val resolved = linkedMapOf<String, PublishTarget>()
        selections.forEach { selection ->
            resolveSelection(roots, selection).forEach { target ->
                resolved.putIfAbsent(target.rootPath + "|" + target.modulePath, target)
            }
        }
        return resolved.values.sortedWith(compareBy(PublishTarget::rootPath, PublishTarget::modulePath))
    }

    private fun resolveSelection(roots: List<VirtualFile>, selection: VirtualFile): List<PublishTarget> {
        val primaryScope = when {
            selection.isDirectory -> SelectionScope(selection, preferLeafModules = true, includeSelectedModule = false)
            isBuildFile(selection) -> SelectionScope(selection.parent ?: return emptyList(), preferLeafModules = false, includeSelectedModule = true)
            else -> SelectionScope(selection.parent ?: return emptyList(), preferLeafModules = true, includeSelectedModule = false)
        }

        val primaryTargets = collectTargets(roots, primaryScope)
        if (primaryTargets.isNotEmpty()) {
            return primaryTargets
        }

        if (selection.isDirectory || isBuildFile(selection)) {
            return emptyList()
        }

        val moduleDir = findNearestModuleDir(roots, selection.parent ?: return emptyList()) ?: return emptyList()
        return collectTargets(
            roots,
            SelectionScope(moduleDir, preferLeafModules = false, includeSelectedModule = true),
        )
    }

    private fun collectTargets(
        roots: List<VirtualFile>,
        scope: SelectionScope,
    ): List<PublishTarget> {
        val targets = linkedMapOf<String, PublishTarget>()

        roots.forEach { root ->
            val effectiveScope = when {
                scope.directory.path.startsWith(root.path) -> scope.directory
                root.path.startsWith(scope.directory.path) -> root
                else -> null
            } ?: return@forEach

            scanModules(root, effectiveScope).forEach { target ->
                targets.putIfAbsent(target.rootPath + "|" + target.modulePath, target)
            }
        }

        if (targets.isEmpty()) {
            return emptyList()
        }

        val values = targets.values.toList()
        return if (scope.preferLeafModules) filterLeafModules(values, scope) else values
    }

    private fun filterLeafModules(
        targets: List<PublishTarget>,
        scope: SelectionScope,
    ): List<PublishTarget> {
        val leafTargets = targets.filter { candidate ->
            targets.none { other ->
                other !== candidate && other.moduleDir.path.startsWith(candidate.moduleDir.path + "/")
            }
        }

        if (scope.includeSelectedModule) {
            return leafTargets
        }

        val scopePath = scope.directory.path
        val scopeTarget = leafTargets.firstOrNull { it.moduleDir.path == scopePath }
        return when {
            scopeTarget == null -> leafTargets
            leafTargets.size == 1 -> leafTargets
            else -> leafTargets.filterNot { it.moduleDir.path == scopePath }
        }
    }

    private fun scanModules(root: VirtualFile, scope: VirtualFile): List<PublishTarget> {
        val targets = mutableListOf<PublishTarget>()
        val seenBuildFiles = linkedSetOf<String>()
        val rootPath = root.path

        VfsUtilCore.visitChildrenRecursively(scope, object : VirtualFileVisitor<Void>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (!file.isDirectory) {
                    return true
                }

                val name = file.name
                if (name.startsWith(".") || name in SKIP_DIRS) {
                    return false
                }
                if (file != scope && hasSettingsFile(file)) {
                    return false
                }

                val buildFile = findBuildFile(file)
                if (buildFile != null && seenBuildFiles.add(buildFile.path)) {
                    val relativePath = file.path.removePrefix(rootPath).trimStart('/')
                    val modulePath = if (relativePath.isEmpty()) ":" else ":${relativePath.replace('/', ':')}"
                    targets += PublishTarget(
                        moduleDir = file,
                        modulePath = modulePath,
                        rootPath = root.path,
                    )
                }

                return true
            }
        })

        return targets
    }

    private fun findNearestModuleDir(roots: List<VirtualFile>, startDir: VirtualFile): VirtualFile? {
        var current: VirtualFile? = startDir
        while (current != null) {
            if (findBuildFile(current) != null) {
                return current
            }

            val parent = current.parent ?: return null
            if (roots.none { root -> parent.path.startsWith(root.path) || root.path == parent.path }) {
                return null
            }
            current = parent
        }
        return null
    }

    private fun collectSearchRoots(project: Project): List<VirtualFile> {
        val localFileSystem = LocalFileSystem.getInstance()
        return GradleBuddySettingsService.getInstance(project)
            .collectGradleSearchRoots(project)
            .mapNotNull { root -> localFileSystem.findFileByPath(root.absolutePath) }
            .sortedBy { it.path.length }
    }

    private fun isBuildFile(file: VirtualFile): Boolean {
        return file.name == "build.gradle.kts" || file.name == "build.gradle"
    }

    private fun findBuildFile(dir: VirtualFile): VirtualFile? {
        return dir.findChild("build.gradle.kts") ?: dir.findChild("build.gradle")
    }

    private fun hasSettingsFile(dir: VirtualFile): Boolean {
        return dir.findChild("settings.gradle.kts") != null || dir.findChild("settings.gradle") != null
    }

    private data class SelectionScope(
        val directory: VirtualFile,
        val preferLeafModules: Boolean,
        val includeSelectedModule: Boolean,
    )

    private val SKIP_DIRS = setOf("build", "out", ".gradle", "node_modules", "target", "buildSrc")
}

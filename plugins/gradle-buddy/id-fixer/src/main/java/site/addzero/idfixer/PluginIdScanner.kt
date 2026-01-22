package site.addzero.idfixer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtFile

/**
 * Scans the project for precompiled script plugins in build-logic directories.
 */
class PluginIdScanner(private val project: Project) {

    /**
     * Finds all build-logic directories in the project.
     */
    fun findBuildLogicDirectories(): List<VirtualFile> {
        val buildLogicDirs = mutableListOf<VirtualFile>()
        val projectBaseDir = project.baseDir ?: return emptyList()

        // Search for build-logic directories recursively
        findBuildLogicDirectoriesRecursive(projectBaseDir, buildLogicDirs)

        return buildLogicDirs
    }

    /**
     * Recursively searches for build-logic directories.
     */
    private fun findBuildLogicDirectoriesRecursive(dir: VirtualFile, result: MutableList<VirtualFile>) {
        if (!dir.isDirectory) return

        // Skip excluded directories for performance
        if (shouldSkipDirectory(dir)) return

        // Check if this is a build-logic directory
        if (dir.name == "build-logic") {
            result.add(dir)
            // Don't recurse into build-logic directories themselves
            return
        }

        // Recurse into subdirectories
        dir.children?.forEach { child ->
            if (child.isDirectory) {
                findBuildLogicDirectoriesRecursive(child, result)
            }
        }
    }

    /**
     * Determines if a directory should be skipped during the search.
     */
    private fun shouldSkipDirectory(dir: VirtualFile): Boolean {
        val name = dir.name
        return name.startsWith(".") && name != ".." ||
                name == "build" ||
                name == "out" ||
                name == "target" ||
                name == "node_modules" ||
                name == ".gradle" ||
                name == ".idea"
    }

    /**
     * Scans a build-logic directory for precompiled script plugins.
     */
    fun scanBuildLogic(buildLogicDir: VirtualFile): List<PluginIdInfo> {
        val plugins = mutableListOf<PluginIdInfo>()

        // Navigate to src/main/kotlin directory
        val srcMainKotlin = buildLogicDir.findFileByRelativePath("src/main/kotlin")
        if (srcMainKotlin == null || !srcMainKotlin.isDirectory) {
            return emptyList()
        }

        // Recursively scan for .gradle.kts files
        scanForGradleKtsFiles(srcMainKotlin, plugins)

        return plugins
    }

    /**
     * Recursively scans a directory for .gradle.kts files.
     */
    private fun scanForGradleKtsFiles(dir: VirtualFile, result: MutableList<PluginIdInfo>) {
        if (!dir.isDirectory) return

        dir.children?.forEach { child ->
            when {
                child.isDirectory -> {
                    // Recurse into subdirectories
                    scanForGradleKtsFiles(child, result)
                }
                child.extension == "kts" && child.name.endsWith(".gradle.kts") -> {
                    // Found a .gradle.kts file, try to extract plugin info
                    val pluginInfo = extractPluginInfo(child)
                    if (pluginInfo != null) {
                        result.add(pluginInfo)
                    }
                }
            }
        }
    }

    /**
     * Extracts plugin information from a .gradle.kts file.
     */
    private fun extractPluginInfo(file: VirtualFile): PluginIdInfo? {
        // Extract short ID from filename (remove .gradle.kts extension)
        val fileName = file.name
        if (!fileName.endsWith(".gradle.kts")) {
            return null
        }
        val shortId = fileName.removeSuffix(".gradle.kts")

        // Get PSI file and extract package name - must be done in read action
        val psiManager = PsiManager.getInstance(project)
        val packageName = com.intellij.openapi.application.ReadAction.compute<String?, Throwable> {
            val psiFile = psiManager.findFile(file) as? KtFile
            psiFile?.let { extractPackageName(it) }
        }

        // Construct fully qualified ID
        val fullyQualifiedId = if (packageName != null && packageName.isNotEmpty()) {
            "$packageName.$shortId"
        } else {
            // No package declaration, use short ID as fully qualified ID
            shortId
        }

        return PluginIdInfo(
            shortId = shortId,
            fullyQualifiedId = fullyQualifiedId,
            packageName = packageName ?: "",
            file = file
        )
    }

    /**
     * Extracts the package name from a Kotlin file.
     */
    private fun extractPackageName(psiFile: KtFile): String? {
        // Use the Kotlin PSI API to get the package directive
        val packageDirective = psiFile.packageDirective
        return packageDirective?.fqName?.asString()
    }
}

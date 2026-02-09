package site.addzero.gradle.buddy.filter

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Shared utility for detecting the Gradle module path of the currently active editor file.
 * Used by both [GradleAutoFocusStartupActivity] and [RunFavoriteTaskAction].
 */
object GradleModulePathUtil {

    /** Tasks that should only be visible in KMP (Kotlin Multiplatform) modules. */
    val KMP_ONLY_TASKS = setOf("kspCommonMainMetadata")

    /** Tasks that should only be visible in IntelliJ plugin development modules. */
    val INTELLIJ_PLUGIN_ONLY_TASKS = setOf("signPlugin", "publishPlugin", "runIde")

    private val KMP_MARKERS = listOf(
        "kotlin(\"multiplatform\")",
        "org.jetbrains.kotlin.multiplatform",
        "KotlinMultiplatformExtension",
    )

    private val INTELLIJ_PLUGIN_MARKERS = listOf(
        "intellijPlatform",
        "org.jetbrains.intellij",
        "intellij-platform-gradle-plugin",
        "buildlogic.intellij.",  // convention plugin: site.addzero.buildlogic.intellij.*
    )

    /**
     * Detect the Gradle module path for the currently active editor file.
     * Walks up from the file's directory looking for build.gradle.kts or build.gradle.
     *
     * @return e.g. ":plugins:gradle-buddy:gradle-buddy-tasks" or ":" for root, null if not found.
     */
    fun detectCurrentModulePath(project: Project): String? {
        val editor = FileEditorManager.getInstance(project).selectedEditor ?: return null
        val file = editor.file ?: return null
        val basePath = project.basePath ?: return null
        if (!file.path.startsWith(basePath)) return null

        var dir = file.parent
        while (dir != null && dir.path.startsWith(basePath)) {
            if (dir.findChild("build.gradle.kts") != null || dir.findChild("build.gradle") != null) {
                val rel = dir.path.removePrefix(basePath).trimStart('/')
                return if (rel.isEmpty()) ":" else ":${rel.replace('/', ':')}"
            }
            dir = dir.parent
        }
        return null
    }

    /**
     * Find the build script (build.gradle.kts or build.gradle) for the current editor file's module.
     */
    fun findCurrentModuleBuildFile(project: Project): VirtualFile? {
        val editor = FileEditorManager.getInstance(project).selectedEditor ?: return null
        val file = editor.file ?: return null
        val basePath = project.basePath ?: return null
        if (!file.path.startsWith(basePath)) return null

        var dir = file.parent
        while (dir != null && dir.path.startsWith(basePath)) {
            val buildFile = dir.findChild("build.gradle.kts") ?: dir.findChild("build.gradle")
            if (buildFile != null) return buildFile
            dir = dir.parent
        }
        return null
    }

    private fun readBuildFileContent(project: Project): String? {
        val buildFile = findCurrentModuleBuildFile(project) ?: return null
        return try {
            String(buildFile.contentsToByteArray(), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Check if the current module is a Kotlin Multiplatform module.
     */
    fun isKmpModule(project: Project): Boolean {
        val content = readBuildFileContent(project) ?: return false
        return KMP_MARKERS.any { it in content }
    }

    /**
     * Check if the current module is an IntelliJ plugin development module.
     */
    fun isIntellijPluginModule(project: Project): Boolean {
        val content = readBuildFileContent(project) ?: return false
        return INTELLIJ_PLUGIN_MARKERS.any { it in content }
    }
}

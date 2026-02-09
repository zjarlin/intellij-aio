package site.addzero.gradle.buddy.filter

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

/**
 * Shared utility for detecting the Gradle module path of the currently active editor file.
 * Used by both [GradleAutoFocusStartupActivity] and [RunFavoriteTaskAction].
 */
object GradleModulePathUtil {

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
}

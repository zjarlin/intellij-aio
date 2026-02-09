package site.addzero.gradle.buddy.filter

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider

/**
 * Floating toolbar that appears on .gradle.kts / .gradle files, showing favorite Gradle tasks.
 * Each button runs the task scoped to the current module.
 *
 * The toolbar appears when hovering near the top of the editor (default autoHideable behavior).
 */
class FavoriteTasksFloatingToolbarProvider :
    AbstractFloatingToolbarProvider("GradleBuddy.FavoriteTasksToolbarGroup") {

    override fun isApplicable(dataContext: DataContext): Boolean {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
        val file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext) ?: return false
        if (!file.isValid || file.isDirectory) return false
        val name = file.name
        if (!name.endsWith(".gradle.kts") && !name.endsWith(".gradle")) return false
        return super.isApplicable(dataContext) && editor.project == project
    }
}

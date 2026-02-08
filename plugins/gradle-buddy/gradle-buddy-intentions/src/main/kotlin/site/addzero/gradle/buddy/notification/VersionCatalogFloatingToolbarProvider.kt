package site.addzero.gradle.buddy.notification

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService

class VersionCatalogFloatingToolbarProvider :
    AbstractFloatingToolbarProvider("GradleBuddy.VersionCatalogFloatingToolbarGroup") {

    override val priority: Int = 100

    override fun isApplicable(dataContext: DataContext): Boolean {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
        val file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext) ?: return false
        if (!file.isValid || file.isDirectory) return false
        val basePath = project.basePath ?: return false
        if (!file.path.startsWith(basePath)) return false
        // Only show on the configured version catalog file
        val catalogPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        val expectedPath = "$basePath/$catalogPath"
        if (file.path != expectedPath) return false
        return super.isApplicable(dataContext) && editor.project == project
    }
}

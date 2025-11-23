package site.addzero.gradle.favorites.listener

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.gradle.favorites.action.ShowFavoritesNotificationAction
import site.addzero.gradle.favorites.service.GradleFavoritesService

class EditorFileOpenListener(private val project: Project) : FileEditorManagerListener {
    
    private val notificationAction = ShowFavoritesNotificationAction()
    private val shownNotifications = mutableSetOf<String>()
    
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val module = ModuleUtil.findModuleForFile(file, project) ?: return
        val modulePath = convertModuleNameToGradlePath(module.name)
        
        if (shownNotifications.contains(modulePath)) return
        
        val service = GradleFavoritesService.getInstance(project)
        val favorites = service.getFavoritesForModule(modulePath)
        
        if (favorites.isNotEmpty()) {
            notificationAction.showNotificationForModule(project, modulePath)
            shownNotifications.add(modulePath)
        }
    }
    
    private fun convertModuleNameToGradlePath(moduleName: String): String {
        if (moduleName == project.name) {
            return ":"
        }
        
        val parts = moduleName.split(".")
        return when {
            parts.size == 1 -> ":$moduleName"
            parts[0] == project.name -> ":${parts.drop(1).joinToString(":")}"
            else -> ":${parts.joinToString(":")}"
        }
    }
}

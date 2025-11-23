package site.addzero.gradle.favorites.strategy

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.roots.ProjectRootManager
import site.addzero.gradle.favorites.model.FavoriteGradleTask

class EditorContextStrategy : AbstractGradleTaskContextStrategy() {
    
    override fun support(event: AnActionEvent): Boolean {
        val editor = event.getData(CommonDataKeys.EDITOR)
        return editor != null
    }
    
    override fun extractTaskInfo(event: AnActionEvent): FavoriteGradleTask? {
        return null
    }
    
    override fun getCurrentModulePath(event: AnActionEvent): String? {
        val project = event.project ?: return null
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return null
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        
        val module = ModuleUtil.findModuleForFile(virtualFile, project) ?: return null
        val moduleName = module.name
        
        return convertModuleNameToGradlePath(moduleName, project)
    }
    
    private fun convertModuleNameToGradlePath(moduleName: String, project: com.intellij.openapi.project.Project): String {
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

package site.addzero.gradle.favorites.strategy

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import site.addzero.gradle.favorites.model.FavoriteGradleTask

class GradleToolWindowContextStrategy : AbstractGradleTaskContextStrategy() {
    
    override fun support(event: AnActionEvent): Boolean {
        val dataContext = event.dataContext
        val contextComponent = CommonDataKeys.CONTEXT_COMPONENT.getData(dataContext)
        return contextComponent?.javaClass?.name?.contains("GradleToolWindow") == true ||
               contextComponent?.javaClass?.name?.contains("gradle") == true
    }
    
    override fun extractTaskInfo(event: AnActionEvent): FavoriteGradleTask? {
        return try {
            val selectedText = extractSelectedTaskFromGradlePanel(event)
            parseGradleTaskPath(selectedText)
        } catch (e: Exception) {
            null
        }
    }
    
    override fun getCurrentModulePath(event: AnActionEvent): String? {
        return extractTaskInfo(event)?.projectPath?.substringBeforeLast(":")
    }
    
    private fun extractSelectedTaskFromGradlePanel(event: AnActionEvent): String? {
        val dataContext = event.dataContext
        
        val selectionData = dataContext.getData(CommonDataKeys.PSI_ELEMENT)?.toString()
            ?: dataContext.getData(CommonDataKeys.NAVIGATABLE)?.toString()
        
        return selectionData?.let { data ->
            Regex("""(:[a-zA-Z0-9\-_:]+:[a-zA-Z0-9\-_]+)""").find(data)?.value
        }
    }
    
    private fun parseGradleTaskPath(taskPath: String?): FavoriteGradleTask? {
        if (taskPath.isNullOrBlank()) return null
        
        val parts = taskPath.split(":")
        if (parts.size < 2) return null
        
        val taskName = parts.last()
        val projectPath = parts.dropLast(1).joinToString(":")
        
        return FavoriteGradleTask(
            projectPath = projectPath.ifEmpty { ":" },
            taskName = taskName
        )
    }
}

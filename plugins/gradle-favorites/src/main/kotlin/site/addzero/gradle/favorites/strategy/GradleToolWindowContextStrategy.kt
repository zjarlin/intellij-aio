package site.addzero.gradle.favorites.strategy

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import site.addzero.gradle.favorites.model.FavoriteGradleTask

class GradleToolWindowContextStrategy : AbstractGradleTaskContextStrategy() {
    
    override fun support(event: AnActionEvent): Boolean {
        return event.place?.contains("Gradle") == true ||
               event.place == "GRADLE_VIEW_TOOLBAR"
    }
    
    override fun extractTaskInfo(event: AnActionEvent): FavoriteGradleTask? {
        return null
    }
    
    override fun getCurrentModulePath(event: AnActionEvent): String? {
        return null
    }
}

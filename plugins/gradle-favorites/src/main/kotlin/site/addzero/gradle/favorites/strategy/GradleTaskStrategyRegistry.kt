package site.addzero.gradle.favorites.strategy

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class GradleTaskStrategyRegistry {
    
    private val strategies: List<GradleTaskContextStrategy> = listOf(
        GradleToolWindowContextStrategy(),
        EditorContextStrategy()
    )
    
    fun findSupportedStrategy(event: AnActionEvent): GradleTaskContextStrategy? =
        strategies.firstOrNull { it.support(event) }
    
    companion object {
        fun getInstance(project: Project): GradleTaskStrategyRegistry =
            project.getService(GradleTaskStrategyRegistry::class.java)
    }
}

package site.addzero.koog.agent

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import site.addzero.koog.agent.settings.KoogAgentSettingsService

class KoogAgentProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        KoogAgentSettingsService.getInstance().initializeDetectedModelsIfNeeded()
    }
}

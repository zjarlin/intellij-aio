package site.addzero.lsi.analyzer

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import site.addzero.lsi.analyzer.extension.LsiExtensionInitializer
import site.addzero.lsi.analyzer.service.PojoScanService
import site.addzero.lsi.analyzer.settings.PojoMetaSettingsService

class PojoMetaStartupActivity : ProjectActivity {
    
    override suspend fun execute(project: Project) {
        // 初始化扩展系统
        LsiExtensionInitializer.initialize()
        
        val settings = PojoMetaSettingsService.getInstance().state
        
        if (settings.autoScanOnStartup) {
            val scanService = PojoScanService.getInstance(project)
            scanService.startScheduledScan(settings.scanIntervalMinutes)
            scanService.scanNowAsync()
        }
    }
}

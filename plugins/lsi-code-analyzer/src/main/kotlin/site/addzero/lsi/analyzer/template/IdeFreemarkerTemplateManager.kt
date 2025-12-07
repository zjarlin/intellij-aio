package site.addzero.lsi.analyzer.template

import site.addzero.lsi.analyzer.settings.PojoMetaSettingsService
import site.addzero.lsi.analyzer.template.FreemarkerTemplateManager as CoreFreemarkerTemplateManager

object IdeFreemarkerTemplateManager {
    
    fun createFromSettings(): CoreFreemarkerTemplateManager = CoreFreemarkerTemplateManager()

    fun getAvailableTemplates(): Map<String, String> =
        PojoMetaSettingsService.getInstance().state.ftlTemplates

    fun saveTemplate(name: String, content: String) {
        PojoMetaSettingsService.getInstance().state.ftlTemplates[name] = content
    }

    fun deleteTemplate(name: String) {
        PojoMetaSettingsService.getInstance().state.ftlTemplates.remove(name)
    }
}

package site.addzero.lsi.analyzer.template

import site.addzero.lsi.analyzer.settings.PojoMetaSettingsService
import site.addzero.lsi.analyzer.template.JteTemplateManager as CoreJteTemplateManager

/**
 * IDE 版 JteTemplateManager，封装核心库并添加 IDE 设置相关的静态方法
 */
object IdeJteTemplateManager {
    
    fun createFromSettings(): CoreJteTemplateManager = CoreJteTemplateManager()

    fun getAvailableTemplates(): Map<String, String> =
        PojoMetaSettingsService.getInstance().state.jteTemplates

    fun saveTemplate(name: String, content: String) {
        PojoMetaSettingsService.getInstance().state.jteTemplates[name] = content
    }

    fun deleteTemplate(name: String) {
        PojoMetaSettingsService.getInstance().state.jteTemplates.remove(name)
    }
}

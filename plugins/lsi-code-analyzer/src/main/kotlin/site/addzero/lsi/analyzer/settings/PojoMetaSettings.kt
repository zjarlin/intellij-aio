package site.addzero.lsi.analyzer.settings

import site.addzero.lsi.analyzer.template.FreemarkerTemplateManager

data class PojoMetaSettings(
    var scanIntervalMinutes: Int = 5,
    var autoScanOnStartup: Boolean = true,
    var metadataExportPath: String = ".pojometa",
    var generateKotlinDataClass: Boolean = true,
    var ftlTemplates: MutableMap<String, String> = mutableMapOf(
        "default" to FreemarkerTemplateManager.DEFAULT_TEMPLATE
    )
)

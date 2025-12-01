package site.addzero.lsi.analyzer.settings

import site.addzero.lsi.analyzer.template.JteTemplateManager

data class PojoMetaSettings(
    var scanIntervalMinutes: Int = 5,
    var autoScanOnStartup: Boolean = true,
    var metadataExportPath: String = ".pojometa",
    var generateKotlinDataClass: Boolean = true,
    var jteTemplates: MutableMap<String, String> = mutableMapOf(
        "default" to JteTemplateManager.DEFAULT_TEMPLATE
    )
)

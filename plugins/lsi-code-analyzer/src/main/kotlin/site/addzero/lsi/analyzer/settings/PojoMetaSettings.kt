package site.addzero.lsi.analyzer.settings

data class PojoMetaSettings(
    var scanIntervalMinutes: Int = 5,
    var autoScanOnStartup: Boolean = true,
    var metadataExportPath: String = ".pojometa",
    var generateKotlinDataClass: Boolean = true,
    var jteTemplates: MutableMap<String, String> = mutableMapOf(
        "default" to DEFAULT_TEMPLATE
    )
) {
    companion object {
        const val DEFAULT_TEMPLATE = """@param metadata site.addzero.lsi.analyzer.metadata.PojoMetadata
// Generated from ${'$'}{metadata.className}
data class ${'$'}{metadata.className}DTO(
@for(field in metadata.fields)
    val ${'$'}{field.name}: ${'$'}{field.typeName}?,
@endfor
)
"""
    }
}

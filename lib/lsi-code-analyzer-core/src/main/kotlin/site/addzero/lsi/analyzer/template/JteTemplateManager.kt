package site.addzero.lsi.analyzer.template

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.output.StringOutput
import gg.jte.resolve.DirectoryCodeResolver
import site.addzero.lsi.analyzer.metadata.PojoMetadata
import java.io.File
import java.nio.file.Path

class JteTemplateManager(templateDir: Path? = null) {
    
    private val engine: TemplateEngine = templateDir?.let {
        TemplateEngine.create(DirectoryCodeResolver(it), ContentType.Plain)
    } ?: TemplateEngine.createPrecompiled(ContentType.Plain)

    fun render(templateName: String, metadata: PojoMetadata): String {
        val output = StringOutput()
        engine.render(templateName, metadata, output)
        return output.toString()
    }

    fun renderWithString(templateContent: String, metadata: PojoMetadata): String {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "jte_templates_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        
        return try {
            val templateFile = File(tempDir, "template.jte")
            templateFile.writeText(templateContent)
            
            val tempEngine = TemplateEngine.create(
                DirectoryCodeResolver(tempDir.toPath()),
                ContentType.Plain
            )
            
            val output = StringOutput()
            tempEngine.render("template.jte", metadata, output)
            output.toString()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun renderAll(templateName: String, metadataList: List<PojoMetadata>): Map<String, String> =
        metadataList.associateBy(
            keySelector = { it.className },
            valueTransform = { render(templateName, it) }
        )

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

package site.addzero.lsi.analyzer.template

import freemarker.template.Configuration
import freemarker.template.Template
import freemarker.template.TemplateExceptionHandler
import site.addzero.util.lsi.clazz.LsiClass
import java.io.File
import java.io.StringWriter
import java.nio.file.Path

class FreemarkerTemplateManager(templateDir: Path? = null) {
    
    private val config: Configuration = Configuration(Configuration.VERSION_2_3_32).apply {
        defaultEncoding = "UTF-8"
        templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        logTemplateExceptions = false
        wrapUncheckedExceptions = true
        
        templateDir?.let { setDirectoryForTemplateLoading(it.toFile()) }
    }

    fun render(templateName: String, metadata: LsiClass): String {
        val template = config.getTemplate(templateName)
        return StringWriter().also { template.process(metadata, it) }.toString()
    }

    fun renderWithString(templateContent: String, metadata: LsiClass): String {
        val template = Template("inline", templateContent, config)
        return StringWriter().also { template.process(metadata, it) }.toString()
    }

    fun renderAll(templateName: String, metadataList: List<LsiClass>): Map<String, String> =
        metadataList.associateBy(
            keySelector = { it.name ?: "Unknown" },
            valueTransform = { render(templateName, it) }
        )

    companion object {
        const val DEFAULT_TEMPLATE = """<#-- Generated from ${"$"}{className} -->
data class ${"$"}{className}DTO(
<#list fields as field>
    val ${"$"}{field.name}: ${"$"}{field.typeName}?,
</#list>
)
"""
    }
}

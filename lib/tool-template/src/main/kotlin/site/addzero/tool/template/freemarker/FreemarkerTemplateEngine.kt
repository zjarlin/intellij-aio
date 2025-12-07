package site.addzero.tool.template.freemarker

import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import java.io.StringWriter
import java.nio.file.Path

class FreemarkerTemplateEngine(
    templateDir: Path? = null,
    classLoader: ClassLoader? = null,
    classLoaderPath: String = "templates"
) {
    private val config: Configuration = Configuration(Configuration.VERSION_2_3_32).apply {
        defaultEncoding = "UTF-8"
        templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        logTemplateExceptions = false
        wrapUncheckedExceptions = true
        
        when {
            templateDir != null -> setDirectoryForTemplateLoading(templateDir.toFile())
            classLoader != null -> setClassLoaderForTemplateLoading(classLoader, classLoaderPath)
            else -> setClassLoaderForTemplateLoading(this::class.java.classLoader, classLoaderPath)
        }
    }

    fun <T : Any> render(templateName: String, model: T): String {
        val template = config.getTemplate(templateName)
        return StringWriter().also { template.process(model, it) }.toString()
    }

    fun renderWithMap(templateName: String, params: Map<String, Any?>): String {
        val template = config.getTemplate(templateName)
        return StringWriter().also { template.process(params, it) }.toString()
    }
    
    fun renderString(templateContent: String, params: Map<String, Any?>): String {
        val template = freemarker.template.Template("inline", templateContent, config)
        return StringWriter().also { template.process(params, it) }.toString()
    }

    companion object {
        fun createForDevelopment(templateDir: Path): FreemarkerTemplateEngine =
            FreemarkerTemplateEngine(templateDir = templateDir)

        fun createFromClasspath(classLoader: ClassLoader, path: String = "templates"): FreemarkerTemplateEngine =
            FreemarkerTemplateEngine(classLoader = classLoader, classLoaderPath = path)
    }
}

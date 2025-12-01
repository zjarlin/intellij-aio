package site.addzero.lsi.generator.template

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.output.StringOutput
import gg.jte.resolve.DirectoryCodeResolver
import java.nio.file.Path

class JteTemplateEngine(
    templateDir: Path? = null,
    private val contentType: ContentType = ContentType.Plain
) {
    private val engine: TemplateEngine = if (templateDir != null) {
        TemplateEngine.create(DirectoryCodeResolver(templateDir), contentType)
    } else {
        TemplateEngine.createPrecompiled(contentType)
    }
    
    fun <T> render(templateName: String, model: T): String {
        val output = StringOutput()
        engine.render(templateName, model, output)
        return output.toString()
    }
    
    fun renderWithMap(templateName: String, params: Map<String, Any?>): String {
        val output = StringOutput()
        engine.render(templateName, params, output)
        return output.toString()
    }
    
    companion object {
        fun createForDevelopment(templateDir: Path): JteTemplateEngine {
            return JteTemplateEngine(templateDir)
        }
        
        fun createPrecompiled(): JteTemplateEngine {
            return JteTemplateEngine()
        }
    }
}

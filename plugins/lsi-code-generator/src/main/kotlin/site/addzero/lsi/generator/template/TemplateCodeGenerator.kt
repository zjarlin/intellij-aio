package site.addzero.lsi.generator.template

import site.addzero.lsi.analyzer.metadata.PojoMetadata
import site.addzero.lsi.generator.contract.CodeGenerator
import java.nio.file.Path

class TemplateCodeGenerator(
    private val templateDir: Path,
    private val templateName: String
) : CodeGenerator<PojoMetadata, String> {
    
    private val engine = JteTemplateEngine.createForDevelopment(templateDir)
    
    override fun support(input: PojoMetadata): Boolean = true
    
    override fun generate(input: PojoMetadata): String {
        return engine.render(templateName, input)
    }
}

class TemplateMapCodeGenerator(
    private val templateDir: Path,
    private val templateName: String
) : CodeGenerator<Map<String, Any?>, String> {
    
    private val engine = JteTemplateEngine.createForDevelopment(templateDir)
    
    override fun support(input: Map<String, Any?>): Boolean = true
    
    override fun generate(input: Map<String, Any?>): String {
        return engine.renderWithMap(templateName, input)
    }
}

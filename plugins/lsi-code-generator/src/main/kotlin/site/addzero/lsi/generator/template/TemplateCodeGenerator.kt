package site.addzero.lsi.generator.template

import site.addzero.lsi.analyzer.metadata.LsiClass
import site.addzero.lsi.generator.contract.CodeGenerator
import site.addzero.tool.template.freemarker.FreemarkerTemplateEngine
import java.nio.file.Path

class TemplateCodeGenerator(
    private val templateDir: Path,
    private val templateName: String
) : CodeGenerator<LsiClass, String> {
    
    private val engine = FreemarkerTemplateEngine.createForDevelopment(templateDir)
    
    override fun support(input: LsiClass): Boolean = true
    
    override fun generate(input: LsiClass): String {
        return engine.render(templateName, input)
    }
}

class TemplateMapCodeGenerator(
    private val templateDir: Path,
    private val templateName: String
) : CodeGenerator<Map<String, Any?>, String> {
    
    private val engine = FreemarkerTemplateEngine.createForDevelopment(templateDir)
    
    override fun support(input: Map<String, Any?>): Boolean = true
    
    override fun generate(input: Map<String, Any?>): String {
        return engine.renderWithMap(templateName, input)
    }
}

package site.addzero.lsi.analyzer.ddl

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.output.StringOutput
import gg.jte.resolve.ResourceCodeResolver
import site.addzero.lsi.analyzer.metadata.PojoMetadata
import java.io.File

/**
 * DDL 模板管理器
 * 
 * 支持：
 * 1. 内置模板（resources/templates/ddl/）
 * 2. 用户自定义模板（项目目录 .lsi/templates/ddl/）
 * 3. 全局自定义模板（~/.lsi/templates/ddl/）
 */
class DdlTemplateManager(
    private val projectPath: String? = null
) {
    
    private val builtinEngine: TemplateEngine by lazy {
        TemplateEngine.create(
            ResourceCodeResolver("templates/ddl"),
            ContentType.Plain
        )
    }
    
    /**
     * 生成 DDL
     */
    fun generate(
        metadata: PojoMetadata,
        dialect: DatabaseDialect,
        operation: DdlOperationType = DdlOperationType.CREATE_TABLE,
        context: Map<String, Any> = emptyMap()
    ): String {
        val templatePath = "${dialect.templateDir}/${operation.templateName}.jte"
        
        // 构建模板上下文
        val templateContext = DdlContext(
            metadata = metadata,
            dialect = dialect,
            operation = operation,
            extra = context
        )
        
        // 优先使用自定义模板
        val customTemplate = findCustomTemplate(dialect, operation)
        if (customTemplate != null) {
            return renderCustomTemplate(customTemplate, templateContext)
        }
        
        // 使用内置模板
        return renderBuiltinTemplate(templatePath, templateContext)
    }
    
    /**
     * 批量生成 DDL
     */
    fun generateBatch(
        metadataList: List<PojoMetadata>,
        dialect: DatabaseDialect,
        operation: DdlOperationType = DdlOperationType.CREATE_TABLE,
        separator: String = "\n\n"
    ): String {
        return metadataList.joinToString(separator) { metadata ->
            generate(metadata, dialect, operation)
        }
    }
    
    /**
     * 获取所有可用的方言
     */
    fun getAvailableDialects(): List<DatabaseDialect> = DatabaseDialect.entries
    
    /**
     * 获取方言支持的操作类型
     */
    fun getSupportedOperations(dialect: DatabaseDialect): List<DdlOperationType> {
        return DdlOperationType.entries.filter { operation ->
            hasTemplate(dialect, operation)
        }
    }
    
    private fun hasTemplate(dialect: DatabaseDialect, operation: DdlOperationType): Boolean {
        val path = "templates/ddl/${dialect.templateDir}/${operation.templateName}.jte"
        return javaClass.classLoader.getResource(path) != null ||
               findCustomTemplate(dialect, operation) != null
    }
    
    private fun findCustomTemplate(dialect: DatabaseDialect, operation: DdlOperationType): File? {
        val templateName = "${operation.templateName}.jte"
        
        // 1. 项目级模板
        projectPath?.let { path ->
            val projectTemplate = File(path, ".lsi/templates/ddl/${dialect.templateDir}/$templateName")
            if (projectTemplate.exists()) return projectTemplate
        }
        
        // 2. 全局模板
        val globalTemplate = File(
            System.getProperty("user.home"),
            ".lsi/templates/ddl/${dialect.templateDir}/$templateName"
        )
        if (globalTemplate.exists()) return globalTemplate
        
        return null
    }
    
    private fun renderBuiltinTemplate(templatePath: String, context: DdlContext): String {
        val output = StringOutput()
        builtinEngine.render(templatePath, context, output)
        return output.toString()
    }
    
    private fun renderCustomTemplate(templateFile: File, context: DdlContext): String {
        val tempEngine = TemplateEngine.create(
            gg.jte.resolve.DirectoryCodeResolver(templateFile.parentFile.toPath()),
            ContentType.Plain
        )
        val output = StringOutput()
        tempEngine.render(templateFile.name, context, output)
        return output.toString()
    }
    
    companion object {
        private var instance: DdlTemplateManager? = null
        
        fun getInstance(projectPath: String? = null): DdlTemplateManager {
            if (instance == null || projectPath != null) {
                instance = DdlTemplateManager(projectPath)
            }
            return instance!!
        }
    }
}

/**
 * DDL 模板上下文
 */
data class DdlContext(
    val metadata: PojoMetadata,
    val dialect: DatabaseDialect,
    val operation: DdlOperationType,
    val extra: Map<String, Any> = emptyMap()
) {
    // 便捷访问
    val tableName: String get() = metadata.tableName ?: metadata.className.toSnakeCase()
    val fields get() = metadata.dbFields
    val className get() = metadata.className
    val comment get() = metadata.comment
    
    private fun String.toSnakeCase(): String {
        return this.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
    }
}

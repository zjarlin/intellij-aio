package site.addzero.lsi.analyzer.ddl

import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import site.addzero.util.db.DatabaseType
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.clazz.guessTableName
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.field.isNullable
import site.addzero.util.lsi.field.isPrimaryKey
import site.addzero.util.lsi.field.isTransient
import java.io.File
import java.io.StringWriter

class DdlTemplateManager(
    private val projectPath: String? = null
) {
    
    private val builtinConfig: Configuration by lazy {
        Configuration(Configuration.VERSION_2_3_32).apply {
            setClassLoaderForTemplateLoading(
                DdlTemplateManager::class.java.classLoader,
                "templates/ddl"
            )
            defaultEncoding = "UTF-8"
            templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
            logTemplateExceptions = false
            wrapUncheckedExceptions = true
        }
    }
    
    fun generate(
        lsiClass: LsiClass,
        dialect: DatabaseType,
        operation: DdlOperationType = DdlOperationType.CREATE_TABLE,
        context: Map<String, Any> = emptyMap()
    ): String {
        val templatePath = "${dialect.code}/${operation.templateName}.ftl"
        
        val templateContext = DdlContext(
            lsiClass = lsiClass,
            dialect = dialect,
            operation = operation,
            extra = context
        )
        
        val customTemplate = findCustomTemplate(dialect, operation)
        if (customTemplate != null) {
            return renderCustomTemplate(customTemplate, templateContext)
        }
        
        return renderBuiltinTemplate(templatePath, templateContext)
    }
    
    fun generateBatch(
        classes: List<LsiClass>,
        dialect: DatabaseType,
        operation: DdlOperationType = DdlOperationType.CREATE_TABLE,
        separator: String = "\n\n"
    ): String = classes.joinToString(separator) { generate(it, dialect, operation) }
    
    fun getAvailableDialects(): List<DatabaseType> = DatabaseType.entries
    
    fun getSupportedOperations(dialect: DatabaseType): List<DdlOperationType> =
        DdlOperationType.entries.filter { hasTemplate(dialect, it) }
    
    private fun hasTemplate(dialect: DatabaseType, operation: DdlOperationType): Boolean {
        val path = "templates/ddl/${dialect.code}/${operation.templateName}.ftl"
        return javaClass.classLoader.getResource(path) != null ||
               findCustomTemplate(dialect, operation) != null
    }
    
    private fun findCustomTemplate(dialect: DatabaseType, operation: DdlOperationType): File? {
        val templateName = "${operation.templateName}.ftl"
        
        projectPath?.let { path ->
            val projectTemplate = File(path, ".lsi/templates/ddl/${dialect.code}/$templateName")
            if (projectTemplate.exists()) return projectTemplate
        }
        
        val globalTemplate = File(
            System.getProperty("user.home"),
            ".lsi/templates/ddl/${dialect.code}/$templateName"
        )
        if (globalTemplate.exists()) return globalTemplate
        
        return null
    }
    
    private fun renderBuiltinTemplate(templatePath: String, context: DdlContext): String {
        val template = builtinConfig.getTemplate(templatePath)
        return StringWriter().also { template.process(context.toMap(), it) }.toString()
    }
    
    private fun renderCustomTemplate(templateFile: File, context: DdlContext): String {
        val config = Configuration(Configuration.VERSION_2_3_32).apply {
            setDirectoryForTemplateLoading(templateFile.parentFile)
            defaultEncoding = "UTF-8"
            templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        }
        val template = config.getTemplate(templateFile.name)
        return StringWriter().also { template.process(context.toMap(), it) }.toString()
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
 * FreeMarker 模板可访问的字段包装类
 * 将 LsiField 的扩展属性暴露为普通属性
 */
class TemplateField(field: LsiField, private val dialect: DatabaseType) {
    private val _field = field
    val name: String = field.name ?: ""
    val typeName: String = field.typeName ?: ""
    val comment: String? = field.comment
    val columnName: String = field.columnName ?: name.toSnakeCase()
    val nullable: Boolean = field.isNullable
    val isPrimaryKey: Boolean = field.isPrimaryKey
    val isTransient: Boolean = field.isTransient
    val isStatic: Boolean = field.isStatic
    val isCollectionType: Boolean = field.isCollectionType
    val defaultValue: String? = field.defaultValue
    
    fun toColumnType(dialect: DatabaseType): String = TypeMapping.getColumnType(_field, dialect)
    
    private fun String.toSnakeCase(): String =
        replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
}

data class DdlContext(
    val lsiClass: LsiClass,
    val dialect: DatabaseType,
    val operation: DdlOperationType,
    val extra: Map<String, Any> = emptyMap()
) {
    val tableName: String get() = lsiClass.guessTableName.takeIf { it.isNotBlank() } 
        ?: (lsiClass.name ?: "unknown").toSnakeCase()
    
    val fields: List<TemplateField> get() = lsiClass.fields
        .filter { !it.isStatic && !it.isCollectionType && !it.isTransient }
        .map { TemplateField(it, dialect) }
    
    val className: String get() = lsiClass.name ?: ""
    val comment: String? get() = lsiClass.comment
    
    private fun String.toSnakeCase(): String =
        replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
    
    fun toMap(): Map<String, Any?> = mapOf(
        "ctx" to this,
        "lsiClass" to lsiClass,
        "dialect" to dialect,
        "operation" to operation,
        "tableName" to tableName,
        "fields" to fields,
        "className" to className,
        "comment" to comment,
        "extra" to extra
    )
}

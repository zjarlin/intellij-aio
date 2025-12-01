package site.addzero.ddl.sql.dialect

import site.addzero.ddl.core.model.ColumnDefinition
import site.addzero.ddl.core.model.TableDefinition
import site.addzero.ddl.sql.SqlDialect

/**
 * 类型映射配置接口
 */
interface TypeMapping {
    /**
     * Java类型到数据库类型的映射函数
     * @param column 列定义
     * @return 数据库类型字符串，如果不能处理则返回null
     */
    fun mapType(column: ColumnDefinition): String?
}

/**
 * 抽象SQL方言基类
 * 提取各数据库方言的公共功能
 */
abstract class AbstractSqlDialect : SqlDialect {

    /**
     * 类型映射注册表
     */
    private val typeMappings = mutableListOf<TypeMapping>()

    /**
     * 注册类型映射
     */
    fun registerTypeMapping(mapping: TypeMapping) {
        typeMappings.add(mapping)
    }

    /**
     * 注册类型映射函数
     */
    fun registerTypeMapping(mappingFunction: (ColumnDefinition) -> String?) {
        typeMappings.add(object : TypeMapping {
            override fun mapType(column: ColumnDefinition): String? = mappingFunction(column)
        })
    }

    /**
     * 判断是否为长文本类型
     */
    fun isTextType(column: ColumnDefinition): Boolean {
        val textKeywords = listOf("url", "base64", "text", "path", "introduction", "content", "description")
        return textKeywords.any { column.name.contains(it, ignoreCase = true) }
    }
    
    /**
     * 应用已注册的类型映射
     */
    protected fun mapJavaTypeWithMappings(column: ColumnDefinition): String? {
        for (mapping in typeMappings) {
            val result = mapping.mapType(column)
            if (result != null) {
                return result
            }
        }
        return null
    }
    
    /**
     * 格式化ALTER TABLE语句的默认实现
     */
    override fun formatAlterTable(table: TableDefinition): List<String> {
        return table.columns.map { column ->
            val tableRef = if (table.databaseName.isNotBlank()) {
                "${quoteIdentifier(table.databaseName)}.${quoteIdentifier(table.name)}"
            } else {
                quoteIdentifier(table.name)
            }

            "ALTER TABLE $tableRef ADD COLUMN ${formatColumnDefinition(column)};"
        }
    }

    /**
     * 格式化CREATE TABLE语句的默认实现
     * 子类可以覆盖此方法以提供特定数据库的实现
     */
    override fun formatCreateTable(table: TableDefinition): String {
        val lines = mutableListOf<String>()
        
        // 默认的CREATE TABLE语句
        lines.add("CREATE TABLE IF NOT EXISTS ${quoteIdentifier(table.name)} (")
        
        // 列定义
        val columnDefs = table.columns.map { "    ${formatColumnDefinition(it)}" }
        lines.addAll(columnDefs.map { "$it," })
        
        // 主键约束
        table.primaryKey?.let {
            lines.add("    PRIMARY KEY (${quoteIdentifier(it)})")
        }
        
        // 添加表选项
        val tableOptions = getTableOptions()
        if (tableOptions.isNotBlank()) {
            lines.add(") $tableOptions")
        } else {
            lines.add(")")
        }
        
        // 表注释
        if (table.comment.isNotBlank()) {
            lines.add("COMMENT = '${escapeString(table.comment)}'")
        }
        
        lines.add(";")
        
        return lines.joinToString("\n")
    }
    
    /**
     * 获取表选项的默认实现
     * 子类可以覆盖此方法以提供特定数据库的表选项
     */
    protected open fun getTableOptions(): String = ""
    
    /**
     * 格式化列定义的默认实现
     * 子类可以覆盖此方法以提供特定数据库的实现
     */
    override fun formatColumnDefinition(column: ColumnDefinition): String {
        val parts = mutableListOf<String>()
        
        // 添加基本的列定义部分
        parts.addAll(getBaseColumnDefinitionParts(column))
        
        // 注释
        if (column.comment.isNotBlank()) {
            parts.add("COMMENT '${escapeString(column.comment)}'")
        }
        
        return parts.joinToString(" ")
    }
    
    /**
     * 获取基础列定义部分的默认实现
     * 子类可以覆盖此方法以提供特定数据库的实现
     */
    protected open fun getBaseColumnDefinitionParts(column: ColumnDefinition): List<String> {
        val parts = mutableListOf<String>()
        
        // 列名
        parts.add(quoteIdentifier(column.name))
        
        // 数据类型
        parts.add(mapJavaType(column))
        
        // NULL约束
        if (!column.nullable || column.primaryKey) {
            parts.add("NOT NULL")
        }
        
        return parts
    }
    
    /**
     * 默认的标识符引用方式（MySQL风格）
     */
    override fun quoteIdentifier(identifier: String): String = "`$identifier`"
    
    /**
     * 默认的字符串转义方式
     */
    override fun escapeString(value: String): String = value.replace("'", "''")
}

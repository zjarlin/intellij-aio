package site.addzero.ddl.sql.dialect

import site.addzero.ddl.core.model.ColumnDefinition

/**
 * 自定义类型映射示例
 * 
 * 展示如何为特定需求添加自定义类型映射
 */
class CustomTypeMappingExample {
    
    /**
     * 示例：为MySQL方言添加自定义类型映射
     */
    fun addCustomMappingsToMysql() {
        val mysqlDialect = MysqlDialect()
        
        // 添加自定义类型映射
        mysqlDialect.registerTypeMapping(object : TypeMapping {
            override fun mapType(column: ColumnDefinition): String? {
                // 为特定包名的类添加自定义映射
                if (column.javaType.startsWith("com.mycompany.custom.")) {
                    return "JSON"
                }
                return null
            }
        })
        
        // 使用lambda表达式添加映射
        mysqlDialect.registerTypeMapping { column ->
            // 为特定注解的字段添加自定义映射
            if (column.comment.contains("@Json")) {
                "JSON"
            } else {
                null
            }
        }
        
        // 为特定长度的字符串添加LONGTEXT映射
        mysqlDialect.registerTypeMapping { column ->
            if (column.javaType == "java.lang.String" && column.length > 10000) {
                "LONGTEXT"
            } else {
                null
            }
        }
    }
    
    /**
     * 示例：创建带有自定义映射的方言
     */
    fun createCustomDialect(): AbstractSqlDialect {
        val customDialect = object : AbstractSqlDialect() {
            override val name: String = "custom-mysql"
            
            override fun mapJavaType(column: ColumnDefinition): String {
                // 首先尝试使用注册的类型映射
                mapJavaTypeWithMappings(column)?.let { return it }
                
                // 默认回退到VARCHAR
                return "VARCHAR(255)"
            }
            
            override fun formatColumnDefinition(column: ColumnDefinition): String {
                return "${quoteIdentifier(column.name)} ${mapJavaType(column)}"
            }
        }
        
        // 注册默认映射
        DefaultTypeMappings.registerDefaults(customDialect)
        
        // 添加自定义映射
        customDialect.registerTypeMapping { column ->
            if (column.javaType == "java.util.UUID") {
                "CHAR(36)"
            } else {
                null
            }
        }
        
        return customDialect
    }
}
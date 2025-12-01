package site.addzero.ddl.sql.dialect

import site.addzero.ddl.core.model.ColumnDefinition

/**
 * 达梦数据库方言
 * DM数据库语法与Oracle类似
 */
class DmDialect : AbstractSqlDialect() {
    
    init {
        // 注册默认类型映射
        DefaultTypeMappings.registerDefaults(this)
    }
    
    override val name: String = "dm"
    
    override fun mapJavaType(column: ColumnDefinition): String {
        val javaType = column.javaType
        
        // 使用注册的类型映射
        val mappedType = mapJavaTypeWithMappings(column)
        if (mappedType != null) {
            return mappedType
        }
        
        // 达梦数据库特定的映射
        return when {
            // 布尔型的特殊处理
            javaType in setOf("boolean", "java.lang.Boolean") -> "BIT"
            
            // 字符串型的特殊处理
            javaType == "java.lang.String" -> {
                val length = if (column.length > 0) column.length else 255
                if (isTextType(column)) {
                    "TEXT"
                } else {
                    "VARCHAR($length)"
                }
            }
            
            // 默认
            else -> "VARCHAR(255)"
        }
    }
}
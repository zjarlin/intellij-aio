package site.addzero.ddl.sql.dialect

import site.addzero.ddl.core.model.ColumnDefinition
import site.addzero.ddl.core.model.TableDefinition

/**
 * H2数据库方言
 * H2语法与MySQL类似，但有一些差异
 */
class H2Dialect : AbstractSqlDialect() {
    
    init {
        // 注册默认类型映射
        DefaultTypeMappings.registerDefaults(this)
    }
    
    override val name: String = "h2"
    
    override fun mapJavaType(column: ColumnDefinition): String {
        val javaType = column.javaType
        
        // 使用注册的类型映射
        val mappedType = mapJavaTypeWithMappings(column)
        if (mappedType != null) {
            return mappedType
        }
        
        // H2特定的映射
        return when {
            // 字符串型的特殊处理
            javaType == "java.lang.String" -> {
                if (isTextType(column)) {
                    "CLOB"
                } else {
                    val length = if (column.length > 0) column.length else 255
                    "VARCHAR($length)"
                }
            }
            
            // 默认
            else -> "VARCHAR(255)"
        }
    }
    
    // 覆盖默认实现以处理H2特定的自增标识
    override fun getBaseColumnDefinitionParts(column: ColumnDefinition): List<String> {
        // 对于自增主键，使用IDENTITY
        if (column.primaryKey && column.autoIncrement) {
            val parts = mutableListOf<String>()
            parts.add(quoteIdentifier(column.name))
            parts.add("IDENTITY")
            
            if (!column.nullable || column.primaryKey) {
                parts.add("NOT NULL")
            }
            
            return parts
        }
        
        // 其他情况使用默认实现
        return super.getBaseColumnDefinitionParts(column)
    }
}
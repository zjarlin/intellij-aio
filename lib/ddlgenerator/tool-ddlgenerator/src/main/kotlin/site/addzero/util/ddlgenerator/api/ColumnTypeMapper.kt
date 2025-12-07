package site.addzero.util.ddlgenerator.api

import site.addzero.util.lsi.field.LsiField

/**
 * 列类型映射器接口
 * 
 * 每个数据库策略实现自己的类型映射逻辑，支持数据库特有类型
 */
interface ColumnTypeMapper {
    
    /**
     * 将Java/Kotlin类型映射到数据库列类型
     * 
     * @param field 字段信息（包含类型、注解等）
     * @return SQL列类型定义（如 "VARCHAR(255)", "BIGINT", "TEXT"等）
     */
    fun mapFieldToColumnType(field: LsiField): String
    
    /**
     * 检查字段是否应该使用数据库特有类型
     * 
     * 例如：
     * - MySQL的TINYINT用于Boolean
     * - PostgreSQL的BYTEA用于byte[]
     * - Oracle的CLOB用于长文本
     * 
     * @param field 字段信息
     * @return 数据库特有类型名称，如果没有则返回null
     */
    fun getDatabaseSpecificType(field: LsiField): String? = null
}

/**
 * 抽象的列类型映射器基类
 * 
 * 提供通用的类型映射逻辑，子类可以覆盖特定类型的映射
 */
abstract class AbstractColumnTypeMapper : ColumnTypeMapper {
    
    /**
     * 类型映射表
     * Key: Java/Kotlin完全限定类型名（如 "java.lang.String"）
     * Value: SQL类型生成函数
     */
    protected abstract val typeMappings: Map<String, (LsiField) -> String>
    
    /**
     * 简单类型名映射（不含包名）
     * Key: 简单类型名（如 "String", "Long"）
     * Value: SQL类型生成函数
     */
    protected abstract val simpleTypeMappings: Map<String, (LsiField) -> String>
    
    override fun mapFieldToColumnType(field: LsiField): String {
        // 1. 优先检查数据库特有类型
        getDatabaseSpecificType(field)?.let { return it }
        
        // 2. 检查完全限定类型名
        val qualifiedTypeName = field.type?.qualifiedName ?: field.typeName
        qualifiedTypeName?.let { fqn ->
            typeMappings[fqn]?.invoke(field)?.let { return it }
        }
        
        // 3. 检查简单类型名
        field.typeName?.let { simpleName ->
            simpleTypeMappings[simpleName.substringAfterLast('.')]?.invoke(field)?.let { return it }
        }
        
        // 4. 默认映射
        return getDefaultMapping(field)
    }
    
    /**
     * 默认映射（当没有匹配的类型时）
     */
    protected open fun getDefaultMapping(field: LsiField): String = "VARCHAR(255)"
}

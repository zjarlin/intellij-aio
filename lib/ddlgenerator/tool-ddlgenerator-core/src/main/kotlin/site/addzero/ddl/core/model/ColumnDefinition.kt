package site.addzero.ddl.core.model

/**
 * 列定义
 * 
 * @property name 列名（通常为下划线命名）
 * @property javaType Java类型的完全限定名
 * @property comment 列注释
 * @property nullable 是否可空
 * @property primaryKey 是否为主键
 * @property autoIncrement 是否自增
 * @property length 长度限制（-1表示不限制）
 * @property precision 精度（用于数值类型）
 * @property scale 小数位数（用于数值类型）
 */
data class ColumnDefinition(
    val name: String,
    val javaType: String,
    val comment: String,
    val nullable: Boolean = true,
    val primaryKey: Boolean = false,
    val autoIncrement: Boolean = false,
    val length: Int = -1,
    val precision: Int = -1,
    val scale: Int = -1
) {
    /**
     * 简单的Java类型名（不含包名）
     */
    val simpleJavaType: String
        get() = javaType.substringAfterLast('.')
}

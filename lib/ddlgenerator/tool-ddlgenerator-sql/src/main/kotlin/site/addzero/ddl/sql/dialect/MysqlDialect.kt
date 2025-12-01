package site.addzero.ddl.sql.dialect

import site.addzero.ddl.core.model.ColumnDefinition

/**
 * MySQL方言
 */
class MysqlDialect : AbstractSqlDialect() {

    init {
        // 注册默认类型映射
        DefaultTypeMappings.registerDefaults(this)

        // 注册MySQL特定的类型映射
        registerTypeMapping { column ->
            when (column.javaType) {
                // MySQL特定的布尔型映射
                "boolean", "java.lang.Boolean" -> "TINYINT(1)"

                // MySQL特定的日期时间型映射
                "java.time.ZonedDateTime", "java.time.OffsetDateTime" -> "DATETIME"

                else -> null
            }
        }
    }

    override val name: String = "mysql"

    override fun mapJavaType(column: ColumnDefinition): String {
        val javaType = column.javaType

        // 使用注册的类型映射
        val mappedType = mapJavaTypeWithMappings(column)
        if (mappedType != null) {
            return mappedType
        }

        // MySQL特定的映射
        return when {
            // 整型的特殊处理
            javaType in setOf("int", "java.lang.Integer", "short", "java.lang.Short") -> "INT"
            javaType in setOf("long", "java.lang.Long") -> "BIGINT"
            javaType in setOf("byte", "java.lang.Byte") -> "TINYINT"

            // 浮点型的特殊处理
            javaType in setOf("float", "java.lang.Float") -> "FLOAT"
            javaType in setOf("double", "java.lang.Double") -> "DOUBLE"

            // BigDecimal的特殊处理
            javaType == "java.math.BigDecimal" -> {
                val precision = if (column.precision > 0) column.precision else 10
                val scale = if (column.scale >= 0) column.scale else 2
                "DECIMAL($precision, $scale)"
            }

            // 布尔型的特殊处理
            javaType in setOf("boolean", "java.lang.Boolean") -> "TINYINT(1)"

            // 字符型的特殊处理
            javaType in setOf("char", "java.lang.Character") -> "CHAR(1)"

            // 字符串型的特殊处理
            javaType == "java.lang.String" -> {
                if (isTextType(column)) {
                    "TEXT"
                } else {
                    val length = if (column.length > 0) column.length else 255
                    "VARCHAR($length)"
                }
            }

            // 日期时间型的特殊处理
            javaType in setOf("java.time.LocalDate", "java.sql.Date") -> "DATE"
            javaType in setOf("java.time.LocalTime", "java.sql.Time") -> "TIME"
            javaType in setOf(
                "java.time.LocalDateTime",
                "java.util.Date",
                "java.sql.Timestamp",
                "java.time.ZonedDateTime",
                "java.time.OffsetDateTime"
            ) -> "DATETIME"

            // 默认
            else -> "VARCHAR(255)"
        }
    }

    // 覆盖基类方法以处理MySQL特定的列定义（自增和NULL处理）
    override fun getBaseColumnDefinitionParts(column: ColumnDefinition): List<String> {
        val parts = super.getBaseColumnDefinitionParts(column).toMutableList()

        // MySQL特定的NULL处理
        if (!column.nullable && !column.primaryKey) {
            // 找到NOT NULL的位置并替换为NULL
            val notNullIndex = parts.indexOf("NOT NULL")
            if (notNullIndex >= 0) {
                parts[notNullIndex] = "NULL"
            }
        }

        // 自增
        if (column.autoIncrement) {
            parts.add("AUTO_INCREMENT")
        }

        return parts
    }

    // MySQL需要特殊的表选项
    override fun getTableOptions(): String = "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
}

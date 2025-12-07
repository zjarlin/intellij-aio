package site.addzero.ddl.sql.dialect

import site.addzero.ddl.core.model.ColumnDefinition

/**
 * TDengine时序数据库方言
 */
class TdengineDialect : AbstractIDdlGen() {

    init {
        // 注册默认类型映射
        DefaultTypeMappings.registerDefaults(this)

        // 注册TDengine特定的类型映射
        registerTypeMapping { column ->
            when (column.javaType) {
                // TDengine特定的浮点型映射
                "java.math.BigDecimal" -> "DOUBLE" // TDengine使用DOUBLE代替DECIMAL

                // TDengine特定的布尔型映射
                "boolean", "java.lang.Boolean" -> "BOOL"

                // TDengine特定的字符串映射
                "java.lang.String" -> {
                    val length = if (column.length > 0) column.length else 255
                    "NCHAR($length)"
                }

                // TDengine特定的日期时间映射
                "java.time.LocalDate", "java.sql.Date",
                "java.time.LocalTime", "java.sql.Time" -> "TIMESTAMP"

                else -> null
            }
        }
    }

    override val name: String = "tdengine"

    override fun mapJavaType(column:LsiField): String {
        val javaType = column.javaType

        // 使用注册的类型映射
        val mappedType = mapJavaTypeWithMappings(column)
        if (mappedType != null) {
            return mappedType
        }

        // TDengine特定的映射
        return when {
            // 整型的特殊处理
            javaType in setOf("int", "java.lang.Integer") -> "INT"
            javaType in setOf("long", "java.lang.Long") -> "BIGINT"
            javaType in setOf("short", "java.lang.Short") -> "SMALLINT"
            javaType in setOf("byte", "java.lang.Byte") -> "TINYINT"

            // 浮点型的特殊处理
            javaType in setOf("float", "java.lang.Float") -> "FLOAT"
            javaType in setOf("double", "java.lang.Double") -> "DOUBLE"
            javaType == "java.math.BigDecimal" -> "DOUBLE" // TDengine使用DOUBLE代替DECIMAL

            // 布尔型的特殊处理
            javaType in setOf("boolean", "java.lang.Boolean") -> "BOOL"

            // 字符串型的特殊处理
            javaType == "java.lang.String" -> {
                val length = if (column.length > 0) column.length else 255
                if (isTextType(column)) {
                    "NCHAR($length)"
                } else {
                    "NCHAR($length)"
                }
            }

            // 日期时间型的特殊处理
            javaType in setOf(
                "java.time.LocalDateTime",
                "java.util.Date",
                "java.sql.Timestamp",
                "java.time.ZonedDateTime",
                "java.time.OffsetDateTime"
            ) -> "TIMESTAMP"

            // TDengine不支持单独的DATE和TIME类型，统一使用TIMESTAMP
            javaType in setOf("java.time.LocalDate", "java.sql.Date") -> "TIMESTAMP"
            javaType in setOf("java.time.LocalTime", "java.sql.Time") -> "TIMESTAMP"

            else -> "NCHAR(255)"
        }
    }

    // 覆盖基类方法以处理TDengine特定的列定义
    override fun getBaseColumnDefinitionParts(column:LsiField): List<String> {
        val parts = mutableListOf<String>()

        parts.add(column.name) // TDengine不需要引号
        parts.add(mapJavaType(column))

        // TDengine不支持内联注释

        return parts
    }

    override fun formatCreateTable(table:LsiClass): String {
        val lines = mutableListOf<String>()

        // TDengine创建普通表（超级表需要单独处理）
        lines.add("CREATE TABLE IF NOT EXISTS ${table.name} (")

        // TDengine要求第一列必须是时间戳类型作为主键
        val timestampColumn = table.columns.find {
            it.javaType.contains("LocalDateTime") ||
            it.javaType.contains("Date") ||
            it.javaType.contains("Timestamp")
        }

        if (timestampColumn != null) {
            lines.add("    ts TIMESTAMP,")
            val otherColumns = table.columns.filter { it != timestampColumn }
            val columnDefs = otherColumns.map { "    ${formatColumnDefinition(it)}" }
            lines.addAll(columnDefs)
        } else {
            // 如果没有时间戳列，自动添加一个
            lines.add("    ts TIMESTAMP,")
            val columnDefs = table.columns.map { "    ${formatColumnDefinition(it)}" }
            lines.addAll(columnDefs)
        }

        lines.add(");")

        return lines.joinToString("\n")
    }

    override fun formatAlterTable(table:LsiClass): List<String> {
        return table.columns.map { column ->
            val tableRef = if (table.databaseName.isNotBlank()) {
                "${table.databaseName}.${table.name}"
            } else {
                table.name
            }

            "ALTER TABLE $tableRef ADD COLUMN ${formatColumnDefinition(column)};"
        }
    }

    override fun quoteIdentifier(identifier: String): String = identifier // TDengine不使用引号
}

package site.addzero.ddl.sql.dialect

import site.addzero.ddl.core.model.ColumnDefinition

/**
 * Oracle方言
 */
class OracleDialect : AbstractIDdlGen() {

    init {
        // 注册默认类型映射
        DefaultTypeMappings.registerDefaults(this)

        // 注册Oracle特定的类型映射
        registerTypeMapping { column ->
            when (column.javaType) {
                // Oracle特定的整型映射
                "int", "java.lang.Integer", "short", "java.lang.Short" -> "NUMBER(10)"
                "long", "java.lang.Long" -> "NUMBER(19)"
                "byte", "java.lang.Byte" -> "NUMBER(3)"

                // Oracle特定的浮点型映射
                "float", "java.lang.Float" -> "BINARY_FLOAT"
                "double", "java.lang.Double" -> "BINARY_DOUBLE"

                // Oracle特定的BigDecimal映射
                "java.math.BigDecimal" -> {
                    val precision = if (column.precision > 0) column.precision else 10
                    val scale = if (column.scale >= 0) column.scale else 2
                    "NUMBER($precision, $scale)"
                }

                // Oracle特定的布尔型映射
                "boolean", "java.lang.Boolean" -> "NUMBER(1)"

                // Oracle特定的字符串映射
                "java.lang.String" -> {
                    val length = if (column.length > 0) column.length else 255
                    if (length > 4000 || (this as AbstractIDdlGen).isTextType(column)) {
                        "CLOB"
                    } else {
                        "VARCHAR2($length)"
                    }
                }

                // Oracle特定的日期时间映射
                "java.time.ZonedDateTime", "java.time.OffsetDateTime" -> "TIMESTAMP"
                "java.time.LocalTime", "java.sql.Time" -> "TIMESTAMP"

                else -> null
            }
        }
    }

    override val name: String = "oracle"

    override fun mapJavaType(column:LsiField): String {
        val javaType = column.javaType

        // 使用注册的类型映射
        val mappedType = mapJavaTypeWithMappings(column)
        if (mappedType != null) {
            return mappedType
        }

        // Oracle特定的映射
        return when {
            // 整型的特殊处理
            javaType in setOf("int", "java.lang.Integer", "short", "java.lang.Short") -> "NUMBER(10)"
            javaType in setOf("long", "java.lang.Long") -> "NUMBER(19)"
            javaType in setOf("byte", "java.lang.Byte") -> "NUMBER(3)"

            // 浮点型的特殊处理
            javaType in setOf("float", "java.lang.Float") -> "BINARY_FLOAT"
            javaType in setOf("double", "java.lang.Double") -> "BINARY_DOUBLE"
            javaType == "java.math.BigDecimal" -> {
                val precision = if (column.precision > 0) column.precision else 10
                val scale = if (column.scale >= 0) column.scale else 2
                "NUMBER($precision, $scale)"
            }

            // 布尔型的特殊处理
            javaType in setOf("boolean", "java.lang.Boolean") -> "NUMBER(1)"

            // 字符型的特殊处理
            javaType in setOf("char", "java.lang.Character") -> "CHAR(1)"

            // 字符串型的特殊处理
            javaType == "java.lang.String" -> {
                val length = if (column.length > 0) column.length else 255
                if (length > 4000 || isTextType(column)) {
                    "CLOB"
                } else {
                    "VARCHAR2($length)"
                }
            }

            // 日期时间型的特殊处理
            javaType in setOf("java.time.LocalDate", "java.sql.Date") -> "DATE"
            javaType in setOf(
                "java.time.LocalDateTime",
                "java.util.Date",
                "java.sql.Timestamp",
                "java.time.ZonedDateTime",
                "java.time.OffsetDateTime"
            ) -> "TIMESTAMP"
            javaType in setOf("java.time.LocalTime", "java.sql.Time") -> "TIMESTAMP"

            else -> "VARCHAR2(255)"
        }
    }

    // 覆盖基类方法以处理Oracle特定的列定义
    override fun getBaseColumnDefinitionParts(column:LsiField): List<String> {
        val parts = mutableListOf<String>()

        parts.add(quoteIdentifier(column.name))
        parts.add(mapJavaType(column))

        if (!column.nullable || column.primaryKey) {
            parts.add("NOT NULL")
        }

        return parts
    }

    override fun formatCreateTable(table:LsiClass): String {
        val lines = mutableListOf<String>()

        lines.add("CREATE TABLE ${quoteIdentifier(table.name)} (")

        val columnDefs = table.columns.map { "    ${formatColumnDefinition(it)}" }
        lines.addAll(columnDefs.map { "$it," })

        table.primaryKeyName?.let {
            lines.add("    PRIMARY KEY (${quoteIdentifier(it)})")
        }

        lines.add(");")

        // Oracle使用COMMENT ON语句
        val comments = mutableListOf<String>()
        if (table.comment.isNotBlank()) {
            comments.add("COMMENT ON TABLE ${quoteIdentifier(table.name)} IS '${escapeString(table.comment)}';")
        }

        table.columns.forEach { column ->
            if (column.comment.isNotBlank()) {
                comments.add(
                    "COMMENT ON COLUMN ${quoteIdentifier(table.name)}.${quoteIdentifier(column.name)} " +
                    "IS '${escapeString(column.comment)}';"
                )
            }
        }

        return if (comments.isNotEmpty()) {
            lines.joinToString("\n") + "\n\n" + comments.joinToString("\n")
        } else {
            lines.joinToString("\n")
        }
    }

    override fun formatAlterTable(table:LsiClass): List<String> {
        val statements = mutableListOf<String>()

        table.columns.forEach { column ->
            val tableRef = if (table.databaseName.isNotBlank()) {
                "${quoteIdentifier(table.databaseName)}.${quoteIdentifier(table.name)}"
            } else {
                quoteIdentifier(table.name)
            }

            statements.add("ALTER TABLE $tableRef ADD ${formatColumnDefinition(column)};")

            if (column.comment.isNotBlank()) {
                statements.add(
                    "COMMENT ON COLUMN $tableRef.${quoteIdentifier(column.name)} " +
                    "IS '${escapeString(column.comment)}';"
                )
            }
        }

        return statements
    }

    // Oracle使用双引号引用标识符并转换为大写
    override fun quoteIdentifier(identifier: String): String = "\"${identifier.uppercase()}\""
}

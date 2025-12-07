package site.addzero.ddl.sql.dialect

import site.addzero.ddl.core.model.ColumnDefinition

/**
 * PostgreSQL方言
 */
class PostgresqlDialect : AbstractIDdlGen() {

    init {
        // 注册默认类型映射
        DefaultTypeMappings.registerDefaults(this)

        // 注册PostgreSQL特定的类型映射
        registerTypeMapping { column ->
            when (column.javaType) {
                // PostgreSQL特定的整型映射
                "int", "java.lang.Integer" -> "INTEGER"
                "short", "java.lang.Short" -> "SMALLINT"
                "byte", "java.lang.Byte" -> "SMALLINT"

                // PostgreSQL特定的浮点型映射
                "float", "java.lang.Float" -> "REAL"
                "double", "java.lang.Double" -> "DOUBLE PRECISION"

                // PostgreSQL特定的BigDecimal映射
                "java.math.BigDecimal" -> {
                    val precision = if (column.precision > 0) column.precision else 10
                    val scale = if (column.scale >= 0) column.scale else 2
                    "NUMERIC($precision, $scale)"
                }

                // PostgreSQL特定的字符串映射
                "java.lang.String" -> "TEXT"

                // PostgreSQL特定的日期时间映射
                "java.time.ZonedDateTime", "java.time.OffsetDateTime" -> "TIMESTAMP WITH TIME ZONE"

                else -> null
            }
        }
    }

    override val name: String = "pg"

    override fun mapJavaType(column:LsiField): String {
        val javaType = column.javaType

        // 使用注册的类型映射
        val mappedType = mapJavaTypeWithMappings(column)
        if (mappedType != null) {
            return mappedType
        }

        // PostgreSQL特定的映射
        return when {
            // 整型的特殊处理
            javaType in setOf("int", "java.lang.Integer") -> "INTEGER"
            javaType in setOf("long", "java.lang.Long") -> "BIGINT"
            javaType in setOf("short", "java.lang.Short") -> "SMALLINT"
            javaType in setOf("byte", "java.lang.Byte") -> "SMALLINT"

            // 浮点型的特殊处理
            javaType in setOf("float", "java.lang.Float") -> "REAL"
            javaType in setOf("double", "java.lang.Double") -> "DOUBLE PRECISION"
            javaType == "java.math.BigDecimal" -> {
                val precision = if (column.precision > 0) column.precision else 10
                val scale = if (column.scale >= 0) column.scale else 2
                "NUMERIC($precision, $scale)"
            }

            // 布尔型的特殊处理
            javaType in setOf("boolean", "java.lang.Boolean") -> "BOOLEAN"

            // 字符型的特殊处理
            javaType in setOf("char", "java.lang.Character") -> "CHAR(1)"

            // 字符串型的特殊处理
            javaType == "java.lang.String" -> {
                // PostgreSQL推荐使用TEXT而不是VARCHAR
                "TEXT"
            }

            // 日期时间型的特殊处理
            javaType in setOf("java.time.LocalDate", "java.sql.Date") -> "DATE"
            javaType in setOf("java.time.LocalTime", "java.sql.Time") -> "TIME"
            javaType in setOf(
                "java.time.LocalDateTime",
                "java.util.Date",
                "java.sql.Timestamp"
            ) -> "TIMESTAMP"
            javaType in setOf("java.time.ZonedDateTime", "java.time.OffsetDateTime") -> "TIMESTAMP WITH TIME ZONE"

            else -> "TEXT"
        }
    }

    // 覆盖基类方法以处理PostgreSQL特定的列定义（SERIAL类型）
    override fun getBaseColumnDefinitionParts(column:LsiField): List<String> {
        val parts = mutableListOf<String>()

        parts.add(quoteIdentifier(column.name))

        // 自增主键使用SERIAL
        if (column.primaryKey && column.autoIncrement) {
            parts.add("SERIAL")
        } else {
            parts.add(mapJavaType(column))
        }

        if (!column.nullable || column.primaryKey) {
            parts.add("NOT NULL")
        }

        return parts
    }

    override fun formatCreateTable(table:LsiClass): String {
        val lines = mutableListOf<String>()

        lines.add("CREATE TABLE IF NOT EXISTS ${quoteIdentifier(table.name)} (")

        val columnDefs = table.columns.map { "    ${formatColumnDefinition(it)}" }
        lines.addAll(columnDefs.map { "$it," })

        table.primaryKeyName?.let {
            lines.add("    PRIMARY KEY (${quoteIdentifier(it)})")
        }

        lines.add(");")

        // PostgreSQL使用COMMENT ON语句添加注释
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

    // PostgreSQL使用双引号引用标识符
    override fun quoteIdentifier(identifier: String): String = "\"$identifier"
}

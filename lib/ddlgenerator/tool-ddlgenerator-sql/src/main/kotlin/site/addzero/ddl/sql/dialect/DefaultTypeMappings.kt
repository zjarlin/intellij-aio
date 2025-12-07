package site.addzero.ddl.sql.dialect

/**
 * 默认类型映射配置
 */
object DefaultTypeMappings {

    /**
     * 注册默认的类型映射到方言
     */
    fun registerDefaults(dialect: AbstractIDdlGen) {
        // 整型映射
        dialect.registerTypeMapping { column ->
            when (column.javaType) {
                "int", "java.lang.Integer" -> "INT"
                "long", "java.lang.Long" -> "BIGINT"
                "short", "java.lang.Short" -> "SMALLINT"
                "byte", "java.lang.Byte" -> "TINYINT"
                else -> null
            }
        }

        // 浮点型映射
        dialect.registerTypeMapping { column ->
            when (column.javaType) {
                "float", "java.lang.Float" -> "FLOAT"
                "double", "java.lang.Double" -> "DOUBLE"
                else -> null
            }
        }

        // 布尔型映射
        dialect.registerTypeMapping { column ->
            when (column.javaType) {
                "boolean", "java.lang.Boolean" -> "BOOLEAN"
                else -> null
            }
        }

        // 字符型映射
        dialect.registerTypeMapping { column ->
            when (column.javaType) {
                "char", "java.lang.Character" -> "CHAR(1)"
                else -> null
            }
        }

        // BigDecimal映射
        dialect.registerTypeMapping { column ->
            if (column.javaType == "java.math.BigDecimal") {
                val precision = if (column.precision > 0) column.precision else 10
                val scale = if (column.scale >= 0) column.scale else 2
                "DECIMAL($precision, $scale)"
            } else {
                null
            }
        }

        // 字符串型映射
        dialect.registerTypeMapping { column ->
            if (column.javaType == "java.lang.String") {
                val dialect = dialect
                if (dialect.isTextType(column)) {
                    "TEXT"
                } else {
                    val length = if (column.length > 0) column.length else 255
                    "VARCHAR($length)"
                }
            } else {
                null
            }
        }

        // 日期时间型映射
        dialect.registerTypeMapping { column ->
            when (column.javaType) {
                "java.time.LocalDate", "java.sql.Date" -> "DATE"
                "java.time.LocalTime", "java.sql.Time" -> "TIME"
                "java.time.LocalDateTime", "java.util.Date", "java.sql.Timestamp" -> "TIMESTAMP"
                else -> null
            }
        }
    }
}

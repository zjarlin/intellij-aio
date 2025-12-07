package site.addzero.util.ddlgenerator.strategy

import site.addzero.util.ddlgenerator.api.AbstractColumnTypeMapper
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.database.*

/**
 * PostgreSQL列类型映射器
 * 
 * 支持PostgreSQL特有类型：
 * - SERIAL/BIGSERIAL 用于自增
 * - BYTEA 用于二进制数据
 * - JSON/JSONB 用于JSON数据
 * - UUID 用于UUID（原生支持）
 * - ARRAY 用于数组类型
 * - TEXT 无长度限制
 * - CITEXT 用于大小写不敏感字符串
 */
class PostgreSqlColumnTypeMapper : AbstractColumnTypeMapper() {
    
    override val typeMappings: Map<String, (LsiField) -> String> = mapOf(
        // 整数类型
        "java.lang.Integer" to { "INTEGER" },
        "java.lang.Long" to { "BIGINT" },
        "java.lang.Short" to { "SMALLINT" },
        "java.lang.Byte" to { "SMALLINT" }, // PostgreSQL没有TINYINT
        
        // 浮点类型
        "java.lang.Float" to { "REAL" },
        "java.lang.Double" to { "DOUBLE PRECISION" },
        "java.math.BigDecimal" to { field ->
            val precision = field.precision
            val scale = field.scale
            when {
                precision > 0 && scale > 0 -> "NUMERIC($precision, $scale)"
                precision > 0 -> "NUMERIC($precision)"
                else -> "NUMERIC(19, 2)"
            }
        },
        "java.math.BigInteger" to { "NUMERIC(65, 0)" },
        
        // 字符串类型
        "java.lang.String" to { field -> mapStringType(field) },
        
        // 字符类型
        "java.lang.Character" to { "CHAR(1)" },
        
        // 布尔类型
        "java.lang.Boolean" to { "BOOLEAN" },
        
        // 日期时间类型
        "java.util.Date" to { "TIMESTAMP" },
        "java.sql.Date" to { "DATE" },
        "java.sql.Time" to { "TIME" },
        "java.sql.Timestamp" to { "TIMESTAMP" },
        "java.time.LocalDate" to { "DATE" },
        "java.time.LocalTime" to { "TIME" },
        "java.time.LocalDateTime" to { "TIMESTAMP" },
        "java.time.ZonedDateTime" to { "TIMESTAMP WITH TIME ZONE" },
        "java.time.OffsetDateTime" to { "TIMESTAMP WITH TIME ZONE" },
        "java.time.Instant" to { "TIMESTAMP WITH TIME ZONE" },
        "java.time.Duration" to { "INTERVAL" },
        
        // 二进制类型
        "byte[]" to { "BYTEA" },
        "[B" to { "BYTEA" },
        
        // UUID类型（PostgreSQL原生支持）
        "java.util.UUID" to { "UUID" },
        
        // JSON类型
        "org.babyfish.jimmer.sql.JsonNode" to { "JSONB" },
        "com.fasterxml.jackson.databind.JsonNode" to { "JSONB" },
        
        // 数组类型（PostgreSQL特有）
        "java.lang.Integer[]" to { "INTEGER[]" },
        "java.lang.Long[]" to { "BIGINT[]" },
        "java.lang.String[]" to { "TEXT[]" },
        "[Ljava.lang.Integer;" to { "INTEGER[]" },
        "[Ljava.lang.Long;" to { "BIGINT[]" },
        "[Ljava.lang.String;" to { "TEXT[]" }
    )
    
    override val simpleTypeMappings: Map<String, (LsiField) -> String> = mapOf(
        // Kotlin类型
        "Int" to { "INTEGER" },
        "Long" to { "BIGINT" },
        "Short" to { "SMALLINT" },
        "Byte" to { "SMALLINT" },
        "Float" to { "REAL" },
        "Double" to { "DOUBLE PRECISION" },
        "String" to { field -> mapStringType(field) },
        "Char" to { "CHAR(1)" },
        "Boolean" to { "BOOLEAN" },
        
        // Kotlin日期时间
        "LocalDate" to { "DATE" },
        "LocalTime" to { "TIME" },
        "LocalDateTime" to { "TIMESTAMP" },
        "Instant" to { "TIMESTAMP WITH TIME ZONE" },
        "Duration" to { "INTERVAL" },
        
        // Kotlin数组
        "IntArray" to { "INTEGER[]" },
        "LongArray" to { "BIGINT[]" },
        "Array<String>" to { "TEXT[]" }
    )
    
    override fun getDatabaseSpecificType(field: LsiField): String? {
        // 检查@Column(columnDefinition)
        field.annotations.firstOrNull { 
            it.qualifiedName?.endsWith("Column") == true 
        }?.let { columnAnno ->
            columnAnno.getAttribute("columnDefinition")?.toString()?.let { def ->
                if (def.isNotBlank() && def != "null") {
                    return def
                }
            }
        }
        
        // 检查是否使用CITEXT（大小写不敏感）
        field.annotations.firstOrNull {
            it.simpleName == "CaseInsensitive"
        }?.let {
            return "CITEXT"
        }
        
        return null
    }
    
    /**
     * PostgreSQL字符串类型映射
     * 
     * PostgreSQL的TEXT类型没有长度限制，性能与VARCHAR相同
     * 推荐策略：
     * - 有明确长度限制的用VARCHAR(n)
     * - 长文本统一用TEXT
     */
    private fun mapStringType(field: LsiField): String {
        // 1. 检查是否为长文本
        if (field.isText) {
            return "TEXT"
        }
        
        // 2. 普通字符串
        val length = field.length
        return when {
            length > 0 -> "VARCHAR($length)"
            else -> "VARCHAR(255)" // 默认长度
        }
    }
}

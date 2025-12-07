package site.addzero.util.ddlgenerator.strategy

import site.addzero.util.ddlgenerator.api.AbstractColumnTypeMapper
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.database.*

/**
 * MySQL列类型映射器
 * 
 * 支持MySQL特有类型：
 * - TINYINT(1) 用于Boolean
 * - TINYINT/SMALLINT/MEDIUMINT 用于小整数
 * - TEXT/MEDIUMTEXT/LONGTEXT 用于不同长度的文本
 * - JSON 用于JSON类型（MySQL 5.7+）
 * - YEAR 用于年份
 * - ENUM 用于枚举（需要显式指定）
 */
class MySqlColumnTypeMapper : AbstractColumnTypeMapper() {
    
    override val typeMappings: Map<String, (LsiField) -> String> = mapOf(
        // 整数类型
        "java.lang.Integer" to { "INT" },
        "java.lang.Long" to { "BIGINT" },
        "java.lang.Short" to { "SMALLINT" },
        "java.lang.Byte" to { "TINYINT" },
        
        // 浮点类型
        "java.lang.Float" to { "FLOAT" },
        "java.lang.Double" to { "DOUBLE" },
        "java.math.BigDecimal" to { field ->
            val precision = field.precision
            val scale = field.scale
            when {
                precision > 0 && scale > 0 -> "DECIMAL($precision, $scale)"
                precision > 0 -> "DECIMAL($precision)"
                else -> "DECIMAL(19, 2)"
            }
        },
        "java.math.BigInteger" to { "DECIMAL(65, 0)" },
        
        // 字符串类型
        "java.lang.String" to { field -> mapStringType(field) },
        
        // 字符类型
        "java.lang.Character" to { "CHAR(1)" },
        
        // 布尔类型
        "java.lang.Boolean" to { "TINYINT(1)" },
        
        // 日期时间类型
        "java.util.Date" to { "DATETIME" },
        "java.sql.Date" to { "DATE" },
        "java.sql.Time" to { "TIME" },
        "java.sql.Timestamp" to { "TIMESTAMP" },
        "java.time.LocalDate" to { "DATE" },
        "java.time.LocalTime" to { "TIME" },
        "java.time.LocalDateTime" to { "DATETIME" },
        "java.time.ZonedDateTime" to { "TIMESTAMP" },
        "java.time.OffsetDateTime" to { "TIMESTAMP" },
        "java.time.Instant" to { "TIMESTAMP" },
        "java.time.Year" to { "YEAR" },
        
        // 二进制类型
        "byte[]" to { field -> 
            if (field.length > 0 && field.length <= 255) {
                "VARBINARY(${field.length})"
            } else {
                "BLOB"
            }
        },
        "[B" to { "BLOB" },
        
        // UUID类型
        "java.util.UUID" to { "CHAR(36)" },
        
        // JSON类型（MySQL 5.7+）
        "org.babyfish.jimmer.sql.JsonNode" to { "JSON" },
        "com.fasterxml.jackson.databind.JsonNode" to { "JSON" }
    )
    
    override val simpleTypeMappings: Map<String, (LsiField) -> String> = mapOf(
        // Kotlin类型
        "Int" to { "INT" },
        "Long" to { "BIGINT" },
        "Short" to { "SMALLINT" },
        "Byte" to { "TINYINT" },
        "Float" to { "FLOAT" },
        "Double" to { "DOUBLE" },
        "String" to { field -> mapStringType(field) },
        "Char" to { "CHAR(1)" },
        "Boolean" to { "TINYINT(1)" },
        
        // Kotlin日期时间（kotlinx-datetime）
        "LocalDate" to { "DATE" },
        "LocalTime" to { "TIME" },
        "LocalDateTime" to { "DATETIME" },
        "Instant" to { "TIMESTAMP" }
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
        
        return null
    }
    
    /**
     * MySQL字符串类型映射
     * 
     * 根据长度智能选择：
     * - <= 255: VARCHAR(n)
     * - 256 - 65,535: VARCHAR(n) 或 TEXT
     * - 65,536 - 16,777,215: MEDIUMTEXT
     * - > 16,777,215: LONGTEXT
     */
    private fun mapStringType(field: LsiField): String {
        // 1. 检查是否为长文本
        if (field.isText) {
            val length = field.length
            return when {
                length > 16_777_215 -> "LONGTEXT"
                length > 65_535 -> "MEDIUMTEXT"
                else -> "TEXT"
            }
        }
        
        // 2. 普通字符串
        val length = field.length
        return when {
            length > 0 -> "VARCHAR($length)"
            else -> "VARCHAR(255)" // 默认长度
        }
    }
}

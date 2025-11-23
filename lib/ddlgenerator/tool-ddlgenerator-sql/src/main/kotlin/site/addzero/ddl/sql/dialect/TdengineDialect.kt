package site.addzero.ddl.sql.dialect

import site.addzero.ddl.core.model.ColumnDefinition
import site.addzero.ddl.core.model.TableDefinition
import site.addzero.ddl.sql.SqlDialect


/**
 * TDengine时序数据库方言
 */
class TdengineDialect : SqlDialect {
    
    override val name: String = "tdengine"
    
    override fun mapJavaType(column: ColumnDefinition): String {
        val javaType = column.javaType
        
        return when {
            javaType in setOf("int", "java.lang.Integer") -> "INT"
            javaType in setOf("long", "java.lang.Long") -> "BIGINT"
            javaType in setOf("short", "java.lang.Short") -> "SMALLINT"
            javaType in setOf("byte", "java.lang.Byte") -> "TINYINT"
            
            javaType in setOf("float", "java.lang.Float") -> "FLOAT"
            javaType in setOf("double", "java.lang.Double") -> "DOUBLE"
            javaType == "java.math.BigDecimal" -> "DOUBLE" // TDengine使用DOUBLE代替DECIMAL
            
            javaType in setOf("boolean", "java.lang.Boolean") -> "BOOL"
            
            javaType == "java.lang.String" -> {
                val length = if (column.length > 0) column.length else 255
                if (isTextType(column)) {
                    "NCHAR($length)"
                } else {
                    "NCHAR($length)"
                }
            }
            
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
    
    private fun isTextType(column: ColumnDefinition): Boolean {
        val textKeywords = listOf("url", "base64", "text", "path", "introduction", "content", "description")
        return textKeywords.any { column.name.contains(it, ignoreCase = true) }
    }
    
    override fun formatColumnDefinition(column: ColumnDefinition): String {
        val parts = mutableListOf<String>()
        
        parts.add(column.name) // TDengine不需要引号
        parts.add(mapJavaType(column))
        
        // TDengine不支持内联注释
        
        return parts.joinToString(" ")
    }
    
    override fun formatCreateTable(table: TableDefinition): String {
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
    
    override fun formatAlterTable(table: TableDefinition): List<String> {
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

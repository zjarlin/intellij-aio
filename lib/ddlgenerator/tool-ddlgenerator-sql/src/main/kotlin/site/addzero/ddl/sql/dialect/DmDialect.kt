package site.addzero.ddl.sql.dialect

import site.addzero.ddl.core.model.ColumnDefinition
import site.addzero.ddl.core.model.TableDefinition
import site.addzero.ddl.sql.SqlDialect


/**
 * 达梦数据库方言
 * DM数据库语法与Oracle类似
 */
class DmDialect : SqlDialect {
    
    override val name: String = "dm"
    
    override fun mapJavaType(column: ColumnDefinition): String {
        val javaType = column.javaType
        
        return when {
            javaType in setOf("int", "java.lang.Integer", "short", "java.lang.Short") -> "INT"
            javaType in setOf("long", "java.lang.Long") -> "BIGINT"
            javaType in setOf("byte", "java.lang.Byte") -> "TINYINT"
            
            javaType in setOf("float", "java.lang.Float") -> "FLOAT"
            javaType in setOf("double", "java.lang.Double") -> "DOUBLE"
            javaType == "java.math.BigDecimal" -> {
                val precision = if (column.precision > 0) column.precision else 10
                val scale = if (column.scale >= 0) column.scale else 2
                "DECIMAL($precision, $scale)"
            }
            
            javaType in setOf("boolean", "java.lang.Boolean") -> "BIT"
            javaType in setOf("char", "java.lang.Character") -> "CHAR(1)"
            
            javaType == "java.lang.String" -> {
                val length = if (column.length > 0) column.length else 255
                if (isTextType(column)) {
                    "TEXT"
                } else {
                    "VARCHAR($length)"
                }
            }
            
            javaType in setOf("java.time.LocalDate", "java.sql.Date") -> "DATE"
            javaType in setOf("java.time.LocalTime", "java.sql.Time") -> "TIME"
            javaType in setOf(
                "java.time.LocalDateTime",
                "java.util.Date",
                "java.sql.Timestamp",
                "java.time.ZonedDateTime",
                "java.time.OffsetDateTime"
            ) -> "TIMESTAMP"
            
            else -> "VARCHAR(255)"
        }
    }
    
    private fun isTextType(column: ColumnDefinition): Boolean {
        val textKeywords = listOf("url", "base64", "text", "path", "introduction", "content", "description")
        return textKeywords.any { column.name.contains(it, ignoreCase = true) }
    }
    
    override fun formatColumnDefinition(column: ColumnDefinition): String {
        val parts = mutableListOf<String>()
        
        parts.add(quoteIdentifier(column.name))
        parts.add(mapJavaType(column))
        
        if (!column.nullable || column.primaryKey) {
            parts.add("NOT NULL")
        }
        
        if (column.comment.isNotBlank()) {
            parts.add("COMMENT '${escapeString(column.comment)}'")
        }
        
        return parts.joinToString(" ")
    }
    
    override fun formatCreateTable(table: TableDefinition): String {
        val lines = mutableListOf<String>()
        
        lines.add("CREATE TABLE IF NOT EXISTS ${quoteIdentifier(table.name)} (")
        
        val columnDefs = table.columns.map { "    ${formatColumnDefinition(it)}" }
        lines.addAll(columnDefs.map { "$it," })
        
        table.primaryKey?.let {
            lines.add("    PRIMARY KEY (${quoteIdentifier(it)})")
        }
        
        lines.add(")")
        
        if (table.comment.isNotBlank()) {
            lines.add("COMMENT = '${escapeString(table.comment)}'")
        }
        
        lines.add(";")
        
        return lines.joinToString("\n")
    }
    
    override fun formatAlterTable(table: TableDefinition): List<String> {
        return table.columns.map { column ->
            val tableRef = if (table.databaseName.isNotBlank()) {
                "${quoteIdentifier(table.databaseName)}.${quoteIdentifier(table.name)}"
            } else {
                quoteIdentifier(table.name)
            }
            
            "ALTER TABLE $tableRef ADD COLUMN ${formatColumnDefinition(column)};"
        }
    }
}

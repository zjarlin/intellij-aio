package site.addzero.ddl.sql.dialect

import site.addzero.ddl.core.model.ColumnDefinition
import site.addzero.ddl.core.model.TableDefinition
import site.addzero.ddl.sql.SqlDialect


/**
 * Oracle方言
 */
class OracleDialect : SqlDialect {
    
    override val name: String = "oracle"
    
    override fun mapJavaType(column: ColumnDefinition): String {
        val javaType = column.javaType
        
        return when {
            javaType in setOf("int", "java.lang.Integer", "short", "java.lang.Short") -> "NUMBER(10)"
            javaType in setOf("long", "java.lang.Long") -> "NUMBER(19)"
            javaType in setOf("byte", "java.lang.Byte") -> "NUMBER(3)"
            
            javaType in setOf("float", "java.lang.Float") -> "BINARY_FLOAT"
            javaType in setOf("double", "java.lang.Double") -> "BINARY_DOUBLE"
            javaType == "java.math.BigDecimal" -> {
                val precision = if (column.precision > 0) column.precision else 10
                val scale = if (column.scale >= 0) column.scale else 2
                "NUMBER($precision, $scale)"
            }
            
            javaType in setOf("boolean", "java.lang.Boolean") -> "NUMBER(1)"
            javaType in setOf("char", "java.lang.Character") -> "CHAR(1)"
            
            javaType == "java.lang.String" -> {
                val length = if (column.length > 0) column.length else 255
                if (length > 4000 || isTextType(column)) {
                    "CLOB"
                } else {
                    "VARCHAR2($length)"
                }
            }
            
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
    
    private fun isTextType(column: ColumnDefinition): Boolean {
        val textKeywords = listOf("url", "base64", "text", "path", "introduction", "content", "description")
        return textKeywords.any { column.name.contains(it, ignoreCase = true) }
    }
    
    override fun quoteIdentifier(identifier: String): String = "\"${identifier.uppercase()}\""
    
    override fun formatColumnDefinition(column: ColumnDefinition): String {
        val parts = mutableListOf<String>()
        
        parts.add(quoteIdentifier(column.name))
        parts.add(mapJavaType(column))
        
        if (!column.nullable || column.primaryKey) {
            parts.add("NOT NULL")
        }
        
        return parts.joinToString(" ")
    }
    
    override fun formatCreateTable(table: TableDefinition): String {
        val lines = mutableListOf<String>()
        
        lines.add("CREATE TABLE ${quoteIdentifier(table.name)} (")
        
        val columnDefs = table.columns.map { "    ${formatColumnDefinition(it)}" }
        lines.addAll(columnDefs.map { "$it," })
        
        table.primaryKey?.let {
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
    
    override fun formatAlterTable(table: TableDefinition): List<String> {
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
}

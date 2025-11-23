package site.addzero.ddl.sql.dialect

import site.addzero.ddl.core.model.ColumnDefinition
import site.addzero.ddl.core.model.TableDefinition
import site.addzero.ddl.sql.SqlDialect


/**
 * PostgreSQL方言
 */
class PostgresqlDialect : SqlDialect {
    
    override val name: String = "pg"
    
    override fun mapJavaType(column: ColumnDefinition): String {
        val javaType = column.javaType
        
        return when {
            javaType in setOf("int", "java.lang.Integer") -> "INTEGER"
            javaType in setOf("long", "java.lang.Long") -> "BIGINT"
            javaType in setOf("short", "java.lang.Short") -> "SMALLINT"
            javaType in setOf("byte", "java.lang.Byte") -> "SMALLINT"
            
            javaType in setOf("float", "java.lang.Float") -> "REAL"
            javaType in setOf("double", "java.lang.Double") -> "DOUBLE PRECISION"
            javaType == "java.math.BigDecimal" -> {
                val precision = if (column.precision > 0) column.precision else 10
                val scale = if (column.scale >= 0) column.scale else 2
                "NUMERIC($precision, $scale)"
            }
            
            javaType in setOf("boolean", "java.lang.Boolean") -> "BOOLEAN"
            javaType in setOf("char", "java.lang.Character") -> "CHAR(1)"
            
            javaType == "java.lang.String" -> {
                // PostgreSQL推荐使用TEXT而不是VARCHAR
                "TEXT"
            }
            
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
    
    override fun formatColumnDefinition(column: ColumnDefinition): String {
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
    
    override fun formatAlterTable(table: TableDefinition): List<String> {
        val statements = mutableListOf<String>()
        
        table.columns.forEach { column ->
            val tableRef = if (table.databaseName.isNotBlank()) {
                "${quoteIdentifier(table.databaseName)}.${quoteIdentifier(table.name)}"
            } else {
                quoteIdentifier(table.name)
            }
            
            // ADD COLUMN
            statements.add("ALTER TABLE $tableRef ADD COLUMN ${formatColumnDefinition(column)};")
            
            // 添加注释
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

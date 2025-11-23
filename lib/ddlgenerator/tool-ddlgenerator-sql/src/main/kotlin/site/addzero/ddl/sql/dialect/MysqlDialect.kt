package site.addzero.ddl.sql.dialect

import site.addzero.ddl.core.model.ColumnDefinition
import site.addzero.ddl.core.model.TableDefinition
import site.addzero.ddl.sql.SqlDialect


/**
 * MySQL方言
 */
class MysqlDialect : SqlDialect {
    
    override val name: String = "mysql"
    
    override fun mapJavaType(column: ColumnDefinition): String {
        val javaType = column.javaType
        
        return when {
            // 整型
            javaType in setOf("int", "java.lang.Integer", "short", "java.lang.Short") -> "INT"
            javaType in setOf("long", "java.lang.Long") -> "BIGINT"
            javaType in setOf("byte", "java.lang.Byte") -> "TINYINT"
            
            // 浮点型
            javaType in setOf("float", "java.lang.Float") -> "FLOAT"
            javaType in setOf("double", "java.lang.Double") -> "DOUBLE"
            javaType == "java.math.BigDecimal" -> {
                val precision = if (column.precision > 0) column.precision else 10
                val scale = if (column.scale >= 0) column.scale else 2
                "DECIMAL($precision, $scale)"
            }
            
            // 布尔型
            javaType in setOf("boolean", "java.lang.Boolean") -> "TINYINT(1)"
            
            // 字符型
            javaType in setOf("char", "java.lang.Character") -> "CHAR(1)"
            
            // 字符串型
            javaType == "java.lang.String" -> {
                if (isTextType(column)) {
                    "TEXT"
                } else {
                    val length = if (column.length > 0) column.length else 255
                    "VARCHAR($length)"
                }
            }
            
            // 日期时间型
            javaType in setOf("java.time.LocalDate", "java.sql.Date") -> "DATE"
            javaType in setOf("java.time.LocalTime", "java.sql.Time") -> "TIME"
            javaType in setOf(
                "java.time.LocalDateTime",
                "java.util.Date",
                "java.sql.Timestamp",
                "java.time.ZonedDateTime",
                "java.time.OffsetDateTime"
            ) -> "DATETIME"
            
            // 默认
            else -> "VARCHAR(255)"
        }
    }
    
    /**
     * 判断是否为长文本类型
     */
    private fun isTextType(column: ColumnDefinition): Boolean {
        val textKeywords = listOf("url", "base64", "text", "path", "introduction", "content", "description")
        return textKeywords.any { column.name.contains(it, ignoreCase = true) }
    }
    
    override fun formatColumnDefinition(column: ColumnDefinition): String {
        val parts = mutableListOf<String>()
        
        // 列名
        parts.add(quoteIdentifier(column.name))
        
        // 数据类型
        parts.add(mapJavaType(column))
        
        // NULL约束
        if (!column.nullable || column.primaryKey) {
            parts.add("NOT NULL")
        } else {
            parts.add("NULL")
        }
        
        // 自增
        if (column.autoIncrement) {
            parts.add("AUTO_INCREMENT")
        }
        
        // 注释
        if (column.comment.isNotBlank()) {
            parts.add("COMMENT '${escapeString(column.comment)}'")
        }
        
        return parts.joinToString(" ")
    }
    
    override fun formatCreateTable(table: TableDefinition): String {
        val lines = mutableListOf<String>()
        
        // CREATE TABLE header
        lines.add("CREATE TABLE IF NOT EXISTS ${quoteIdentifier(table.name)} (")
        
        // 列定义
        val columnDefs = table.columns.map { "    ${formatColumnDefinition(it)}" }
        lines.addAll(columnDefs.map { "$it," })
        
        // 主键约束
        table.primaryKey?.let {
            lines.add("    PRIMARY KEY (${quoteIdentifier(it)})")
        }
        
        lines.add(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4")
        
        // 表注释
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

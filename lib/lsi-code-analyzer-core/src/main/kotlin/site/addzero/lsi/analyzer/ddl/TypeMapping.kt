package site.addzero.lsi.analyzer.ddl

import site.addzero.lsi.analyzer.metadata.FieldMetadata

/**
 * Java/Kotlin 类型到数据库类型的映射
 */
object TypeMapping {
    
    private val mysqlMapping = mapOf(
        "String" to "VARCHAR(255)",
        "java.lang.String" to "VARCHAR(255)",
        "Int" to "INT",
        "Integer" to "INT",
        "java.lang.Integer" to "INT",
        "Long" to "BIGINT",
        "java.lang.Long" to "BIGINT",
        "Double" to "DOUBLE",
        "java.lang.Double" to "DOUBLE",
        "Float" to "FLOAT",
        "java.lang.Float" to "FLOAT",
        "Boolean" to "TINYINT(1)",
        "java.lang.Boolean" to "TINYINT(1)",
        "BigDecimal" to "DECIMAL(19,4)",
        "java.math.BigDecimal" to "DECIMAL(19,4)",
        "Date" to "DATETIME",
        "java.util.Date" to "DATETIME",
        "LocalDate" to "DATE",
        "java.time.LocalDate" to "DATE",
        "LocalDateTime" to "DATETIME",
        "java.time.LocalDateTime" to "DATETIME",
        "LocalTime" to "TIME",
        "java.time.LocalTime" to "TIME",
        "Instant" to "TIMESTAMP",
        "java.time.Instant" to "TIMESTAMP",
        "ByteArray" to "BLOB",
        "[B" to "BLOB",
        "UUID" to "VARCHAR(36)",
        "java.util.UUID" to "VARCHAR(36)"
    )
    
    private val postgresqlMapping = mapOf(
        "String" to "VARCHAR(255)",
        "java.lang.String" to "VARCHAR(255)",
        "Int" to "INTEGER",
        "Integer" to "INTEGER",
        "java.lang.Integer" to "INTEGER",
        "Long" to "BIGINT",
        "java.lang.Long" to "BIGINT",
        "Double" to "DOUBLE PRECISION",
        "java.lang.Double" to "DOUBLE PRECISION",
        "Float" to "REAL",
        "java.lang.Float" to "REAL",
        "Boolean" to "BOOLEAN",
        "java.lang.Boolean" to "BOOLEAN",
        "BigDecimal" to "NUMERIC(19,4)",
        "java.math.BigDecimal" to "NUMERIC(19,4)",
        "Date" to "TIMESTAMP",
        "java.util.Date" to "TIMESTAMP",
        "LocalDate" to "DATE",
        "java.time.LocalDate" to "DATE",
        "LocalDateTime" to "TIMESTAMP",
        "java.time.LocalDateTime" to "TIMESTAMP",
        "LocalTime" to "TIME",
        "java.time.LocalTime" to "TIME",
        "Instant" to "TIMESTAMPTZ",
        "java.time.Instant" to "TIMESTAMPTZ",
        "ByteArray" to "BYTEA",
        "[B" to "BYTEA",
        "UUID" to "UUID",
        "java.util.UUID" to "UUID"
    )
    
    private val oracleMapping = mapOf(
        "String" to "VARCHAR2(255)",
        "java.lang.String" to "VARCHAR2(255)",
        "Int" to "NUMBER(10)",
        "Integer" to "NUMBER(10)",
        "java.lang.Integer" to "NUMBER(10)",
        "Long" to "NUMBER(19)",
        "java.lang.Long" to "NUMBER(19)",
        "Double" to "NUMBER(19,4)",
        "java.lang.Double" to "NUMBER(19,4)",
        "Float" to "NUMBER(19,4)",
        "java.lang.Float" to "NUMBER(19,4)",
        "Boolean" to "NUMBER(1)",
        "java.lang.Boolean" to "NUMBER(1)",
        "BigDecimal" to "NUMBER(19,4)",
        "java.math.BigDecimal" to "NUMBER(19,4)",
        "Date" to "TIMESTAMP",
        "java.util.Date" to "TIMESTAMP",
        "LocalDate" to "DATE",
        "java.time.LocalDate" to "DATE",
        "LocalDateTime" to "TIMESTAMP",
        "java.time.LocalDateTime" to "TIMESTAMP",
        "LocalTime" to "TIMESTAMP",
        "java.time.LocalTime" to "TIMESTAMP",
        "Instant" to "TIMESTAMP WITH TIME ZONE",
        "java.time.Instant" to "TIMESTAMP WITH TIME ZONE",
        "ByteArray" to "BLOB",
        "[B" to "BLOB",
        "UUID" to "VARCHAR2(36)",
        "java.util.UUID" to "VARCHAR2(36)"
    )
    
    private val dmMapping = oracleMapping // 达梦兼容Oracle
    
    private val h2Mapping = mapOf(
        "String" to "VARCHAR(255)",
        "java.lang.String" to "VARCHAR(255)",
        "Int" to "INT",
        "Integer" to "INT",
        "java.lang.Integer" to "INT",
        "Long" to "BIGINT",
        "java.lang.Long" to "BIGINT",
        "Double" to "DOUBLE",
        "java.lang.Double" to "DOUBLE",
        "Float" to "REAL",
        "java.lang.Float" to "REAL",
        "Boolean" to "BOOLEAN",
        "java.lang.Boolean" to "BOOLEAN",
        "BigDecimal" to "DECIMAL(19,4)",
        "java.math.BigDecimal" to "DECIMAL(19,4)",
        "Date" to "TIMESTAMP",
        "java.util.Date" to "TIMESTAMP",
        "LocalDate" to "DATE",
        "java.time.LocalDate" to "DATE",
        "LocalDateTime" to "TIMESTAMP",
        "java.time.LocalDateTime" to "TIMESTAMP",
        "LocalTime" to "TIME",
        "java.time.LocalTime" to "TIME",
        "Instant" to "TIMESTAMP WITH TIME ZONE",
        "java.time.Instant" to "TIMESTAMP WITH TIME ZONE",
        "ByteArray" to "BLOB",
        "[B" to "BLOB",
        "UUID" to "UUID",
        "java.util.UUID" to "UUID"
    )
    
    private val taosMapping = mapOf(
        "String" to "NCHAR(255)",
        "java.lang.String" to "NCHAR(255)",
        "Int" to "INT",
        "Integer" to "INT",
        "java.lang.Integer" to "INT",
        "Long" to "BIGINT",
        "java.lang.Long" to "BIGINT",
        "Double" to "DOUBLE",
        "java.lang.Double" to "DOUBLE",
        "Float" to "FLOAT",
        "java.lang.Float" to "FLOAT",
        "Boolean" to "BOOL",
        "java.lang.Boolean" to "BOOL",
        "BigDecimal" to "DOUBLE",
        "java.math.BigDecimal" to "DOUBLE",
        "Date" to "TIMESTAMP",
        "java.util.Date" to "TIMESTAMP",
        "LocalDate" to "TIMESTAMP",
        "java.time.LocalDate" to "TIMESTAMP",
        "LocalDateTime" to "TIMESTAMP",
        "java.time.LocalDateTime" to "TIMESTAMP",
        "Instant" to "TIMESTAMP",
        "java.time.Instant" to "TIMESTAMP"
    )
    
    fun getColumnType(field: FieldMetadata, dialect: DatabaseDialect): String {
        val mapping = when (dialect) {
            DatabaseDialect.MYSQL -> mysqlMapping
            DatabaseDialect.POSTGRESQL -> postgresqlMapping
            DatabaseDialect.ORACLE -> oracleMapping
            DatabaseDialect.DM -> dmMapping
            DatabaseDialect.H2 -> h2Mapping
            DatabaseDialect.TAOS -> taosMapping
            DatabaseDialect.SQLITE -> mysqlMapping // SQLite 比较灵活
            DatabaseDialect.SQLSERVER -> mysqlMapping // 简化处理
        }
        
        val typeName = field.typeName.substringAfterLast('.')
        val qualifiedName = field.typeQualifiedName
        
        return mapping[typeName] 
            ?: mapping[qualifiedName] 
            ?: guessType(typeName, dialect)
    }
    
    private fun guessType(typeName: String, dialect: DatabaseDialect): String {
        return when {
            typeName.contains("String", ignoreCase = true) -> 
                if (dialect == DatabaseDialect.ORACLE || dialect == DatabaseDialect.DM) "VARCHAR2(255)" else "VARCHAR(255)"
            typeName.contains("Int", ignoreCase = true) -> "INT"
            typeName.contains("Long", ignoreCase = true) -> "BIGINT"
            typeName.contains("Double", ignoreCase = true) || typeName.contains("Float", ignoreCase = true) -> "DOUBLE"
            typeName.contains("Boolean", ignoreCase = true) -> 
                if (dialect == DatabaseDialect.MYSQL) "TINYINT(1)" else "BOOLEAN"
            typeName.contains("Date", ignoreCase = true) || typeName.contains("Time", ignoreCase = true) -> "TIMESTAMP"
            else -> "VARCHAR(255)"
        }
    }
}

/**
 * 字段扩展函数
 */
fun FieldMetadata.toColumnName(): String {
    return columnName ?: name.toSnakeCase()
}

fun FieldMetadata.toColumnType(dialect: DatabaseDialect): String {
    return TypeMapping.getColumnType(this, dialect)
}

private fun String.toSnakeCase(): String {
    return this.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
}

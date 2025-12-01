package com.addzero.util.database

object DialectInferrer {
    
    private val DIALECT_PATTERNS = listOf(
        DialectPattern("mysql", listOf("mysql", "mariadb")),
        DialectPattern("postgresql", listOf("postgresql", "postgres")),
        DialectPattern("oracle", listOf("oracle")),
        DialectPattern("h2", listOf("h2")),
        DialectPattern("dm", listOf("dm", "dameng")),
        DialectPattern("sqlserver", listOf("sqlserver", "mssql")),
        DialectPattern("sqlite", listOf("sqlite")),
        DialectPattern("tdengine", listOf("taos", "tdengine")),
    )
    
    fun inferFromJdbcUrl(jdbcUrl: String?): String {
        if (jdbcUrl.isNullOrBlank()) return "mysql"
        
        val lowerUrl = jdbcUrl.lowercase()
        
        for (pattern in DIALECT_PATTERNS) {
            if (pattern.keywords.any { lowerUrl.contains(it) }) {
                return pattern.dialect
            }
        }
        
        return "mysql"
    }
    
    fun inferFromDriverClass(driverClass: String?): String {
        if (driverClass.isNullOrBlank()) return "mysql"
        
        val lowerDriver = driverClass.lowercase()
        
        return when {
            lowerDriver.contains("mysql") || lowerDriver.contains("mariadb") -> "mysql"
            lowerDriver.contains("postgresql") || lowerDriver.contains("postgres") -> "postgresql"
            lowerDriver.contains("oracle") -> "oracle"
            lowerDriver.contains("h2") -> "h2"
            lowerDriver.contains("dm") || lowerDriver.contains("dameng") -> "dm"
            lowerDriver.contains("sqlserver") || lowerDriver.contains("mssql") -> "sqlserver"
            lowerDriver.contains("sqlite") -> "sqlite"
            lowerDriver.contains("taos") || lowerDriver.contains("tdengine") -> "tdengine"
            else -> "mysql"
        }
    }
    
    fun getSupportedDialects(): List<String> = DIALECT_PATTERNS.map { it.dialect }
    
    private data class DialectPattern(
        val dialect: String,
        val keywords: List<String>
    )
}

data class DatabaseConnectionInfo(
    val jdbcUrl: String,
    val username: String?,
    val password: String?,
    val driverClass: String?
) {
    val dialect: String by lazy { DialectInferrer.inferFromJdbcUrl(jdbcUrl) }
}

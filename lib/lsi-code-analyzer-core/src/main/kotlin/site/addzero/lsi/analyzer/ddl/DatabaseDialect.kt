package site.addzero.lsi.analyzer.ddl

/**
 * 数据库方言枚举
 */
enum class DatabaseDialect(
    val displayName: String,
    val templateDir: String,
    val fileExtension: String = "sql"
) {
    MYSQL("MySQL", "mysql"),
    POSTGRESQL("PostgreSQL", "postgresql"),
    ORACLE("Oracle", "oracle"),
    H2("H2", "h2"),
    DM("达梦 DM", "dm"),
    TAOS("TDengine/TaosDB", "taos"),
    SQLITE("SQLite", "sqlite"),
    SQLSERVER("SQL Server", "sqlserver");
    
    companion object {
        fun fromName(name: String): DatabaseDialect? {
            return entries.find { 
                it.name.equals(name, ignoreCase = true) || 
                it.displayName.equals(name, ignoreCase = true) 
            }
        }
    }
}

/**
 * DDL 操作类型
 */
enum class DdlOperationType(val templateName: String) {
    CREATE_TABLE("create-table"),
    ALTER_ADD_COLUMN("alter-add-column"),
    ALTER_MODIFY_COLUMN("alter-modify-column"),
    DROP_TABLE("drop-table"),
    CREATE_INDEX("create-index"),
    DROP_INDEX("drop-index"),
    ADD_FOREIGN_KEY("add-foreign-key"),
    ADD_COMMENT("add-comment");
}

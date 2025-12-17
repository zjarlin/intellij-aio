package site.addzero.lsi.analyzer.service

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import site.addzero.lsi.analyzer.service.JdbcConnectionDetectorService
import site.addzero.util.db.DatabaseType
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.clazz.guessTableName
import site.addzero.util.lsi.database.getDatabaseColumnType
import java.sql.*

/**
 * 数据库 Schema 服务
 * 用于获取数据库当前的表结构信息，以便进行差量对比
 */
class DatabaseSchemaService(private val project: Project) {

    private val jdbcDetector = com.intellij.openapi.components.ServiceManager.getService(JdbcConnectionDetectorService::class.java)

    /**
     * 获取数据库连接
     */
    fun getConnection(connInfo: JdbcConnectionDetectorService.ConnectionInfo): Connection {
        try {
            // 根据数据库类型加载驱动
            val dialect = connInfo.dialect.lowercase()
            when {
                dialect.contains("mysql") -> Class.forName("com.mysql.cj.jdbc.Driver")
                dialect.contains("postgresql") -> Class.forName("org.postgresql.Driver")
                dialect.contains("oracle") -> Class.forName("oracle.jdbc.OracleDriver")
                dialect.contains("sqlserver") -> Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
                else -> Class.forName("com.mysql.cj.jdbc.Driver")
            }
            return DriverManager.getConnection(
                connInfo.url,
                connInfo.username,
                connInfo.password
            )
        } catch (e: Exception) {
            throw RuntimeException("无法连接到数据库: ${e.message}", e)
        }
    }

    /**
     * 获取数据库中所有表的元数据信息
     */
    fun getDatabaseTables(connInfo: JdbcConnectionDetectorService.ConnectionInfo): Map<String, DatabaseTableInfo> {
        val tables = mutableMapOf<String, DatabaseTableInfo>()

        getConnection(connInfo).use { conn ->
            val databaseMetaData = conn.metaData

            // 获取所有表
            val rs = databaseMetaData.getTables(
                null,  // catalog
                null,  // schema
                null,  // tableNamePattern
                arrayOf("TABLE")  // types
            )

            while (rs.next()) {
                val tableName = rs.getString("TABLE_NAME")
                val tableComment = rs.getString("REMARKS")

                val tableInfo = DatabaseTableInfo(
                    name = tableName,
                    comment = tableComment,
                    columns = mutableMapOf()
                )

                // 获取表的列信息
                val columnsRs = databaseMetaData.getColumns(
                    null,
                    null,
                    tableName,
                    null
                )

                while (columnsRs.next()) {
                    val columnName = columnsRs.getString("COLUMN_NAME")
                    val columnType = columnsRs.getString("TYPE_NAME")
                    val columnSize = columnsRs.getInt("COLUMN_SIZE")
                    val isNullable = columnsRs.getString("IS_NULLABLE") == "YES"
                    val columnDefault = columnsRs.getString("COLUMN_DEF")
                    val comment = columnsRs.getString("REMARKS")

                    tableInfo.columns[columnName] = DatabaseColumnInfo(
                        name = columnName,
                        type = columnType,
                        size = columnSize,
                        isNullable = isNullable,
                        defaultValue = columnDefault,
                        comment = comment
                    )
                }

                // 获取主键信息
                val primaryKeyRs = databaseMetaData.getPrimaryKeys(
                    null,
                    null,
                    tableName
                )

                val primaryKeys = mutableListOf<String>()
                while (primaryKeyRs.next()) {
                    primaryKeys.add(primaryKeyRs.getString("COLUMN_NAME"))
                }
                tableInfo.primaryKeys = primaryKeys

                // 获取外键信息
                val foreignKeyRs = databaseMetaData.getImportedKeys(
                    null,
                    null,
                    tableName
                )

                val foreignKeys = mutableListOf<ForeignKeyInfo>()
                while (foreignKeyRs.next()) {
                    foreignKeys.add(
                        ForeignKeyInfo(
                            columnName = foreignKeyRs.getString("FKCOLUMN_NAME"),
                            referencedTable = foreignKeyRs.getString("PKTABLE_NAME"),
                            referencedColumn = foreignKeyRs.getString("PKCOLUMN_NAME"),
                            constraintName = foreignKeyRs.getString("FK_NAME")
                        )
                    )
                }
                tableInfo.foreignKeys = foreignKeys

                tables[tableName] = tableInfo
            }
        }

        return tables
    }

    /**
     * 比较数据库表结构和 POJO 元数据
     */
    fun compareWithDatabase(
        pojos: List<LsiClass>,
        databaseType: DatabaseType
    ): SchemaComparison {
        val connInfo = jdbcDetector.detectConnectionInfo(project)
        val databaseTables = getDatabaseTables(connInfo)
        val pojoTables = pojos.associateBy { pojo -> pojo.guessTableName }

        val newTables = mutableListOf<String>()
        val modifiedTables = mutableMapOf<String, TableModification>()
        val droppedTables = mutableListOf<String>()

        // 找出新增的表
        pojoTables.keys.forEach { tableName ->
            if (!databaseTables.containsKey(tableName)) {
                newTables.add(tableName)
            }
        }

        // 找出删除的表
        databaseTables.keys.forEach { tableName ->
            if (!pojoTables.containsKey(tableName)) {
                droppedTables.add(tableName)
            }
        }

        // 找出修改的表
        pojoTables.entries.forEach { (tableName, lsiClass) ->
            val dbTable = databaseTables[tableName]
            if (dbTable != null) {
                val modifications = compareTables(lsiClass, dbTable, databaseType)
                if (modifications.addedColumns.isNotEmpty() ||
                    modifications.modifiedColumns.isNotEmpty() ||
                    modifications.droppedColumns.isNotEmpty()) {
                    modifiedTables[tableName] = modifications
                }
            }
        }

        return SchemaComparison(
            newTables = newTables,
            modifiedTables = modifiedTables,
            droppedTables = droppedTables,
            databaseTables = databaseTables
        )
    }

    private fun compareTables(
        lsiClass: LsiClass,
        dbTable: DatabaseTableInfo,
        databaseType: DatabaseType
    ): TableModification {
        val addedColumns = mutableListOf<ColumnInfo>()
        val modifiedColumns = mutableListOf<ColumnInfo>()
        val droppedColumns = mutableListOf<ColumnInfo>()

        val pojoColumns = lsiClass.fields.associateBy { field -> field.columnName ?: field.name ?: "" }

        // 找出新增的列
        pojoColumns.forEach { (columnName, field) ->
            if (!dbTable.columns.containsKey(columnName)) {
                addedColumns.add(
                    ColumnInfo(
                        name = columnName,
                        type = field.getDatabaseColumnType().name,
                        isNullable = field.isNullable,
                        defaultValue = field.defaultValue,
                        comment = field.comment
                    )
                )
            }
        }

        // 找出删除的列
        dbTable.columns.keys.forEach { columnName ->
            if (!pojoColumns.containsKey(columnName)) {
                droppedColumns.add(
                    ColumnInfo(
                        name = columnName,
                        type = dbTable.columns[columnName]?.type ?: "",
                        isNullable = dbTable.columns[columnName]?.isNullable ?: true
                    )
                )
            }
        }

        // 找出修改的列
        pojoColumns.forEach { (columnName, field) ->
            val dbColumn = dbTable.columns[columnName]
            if (dbColumn != null) {
                // 这里可以比较类型、是否可空、默认值等
                // 简化处理，只比较类型
                val expectedType = mapDatabaseType(field.getDatabaseColumnType(), databaseType)
                if (expectedType != dbColumn.type) {
                    modifiedColumns.add(
                        ColumnInfo(
                            name = columnName,
                            type = expectedType,
                            isNullable = field.isNullable,
                            defaultValue = field.defaultValue,
                            comment = field.comment,
                            oldType = dbColumn.type
                        )
                    )
                }
            }
        }

        return TableModification(
            addedColumns = addedColumns,
            modifiedColumns = modifiedColumns,
            droppedColumns = droppedColumns
        )
    }

    private fun mapDatabaseType(columnType: site.addzero.util.lsi.database.DatabaseColumnType, databaseType: DatabaseType): String {
        // 根据数据库类型映射列类型名
        return when (databaseType) {
            DatabaseType.MYSQL -> {
                when (columnType) {
                    site.addzero.util.lsi.database.DatabaseColumnType.BIGINT -> "BIGINT"
                    site.addzero.util.lsi.database.DatabaseColumnType.INT -> "INT"
                    site.addzero.util.lsi.database.DatabaseColumnType.VARCHAR -> "VARCHAR"
                    site.addzero.util.lsi.database.DatabaseColumnType.TEXT -> "TEXT"
                    site.addzero.util.lsi.database.DatabaseColumnType.DATETIME -> "DATETIME"
                    site.addzero.util.lsi.database.DatabaseColumnType.BOOLEAN -> "TINYINT(1)"
                    else -> columnType.name
                }
            }
            DatabaseType.POSTGRESQL -> {
                when (columnType) {
                    site.addzero.util.lsi.database.DatabaseColumnType.BIGINT -> "BIGINT"
                    site.addzero.util.lsi.database.DatabaseColumnType.INT -> "INTEGER"
                    site.addzero.util.lsi.database.DatabaseColumnType.VARCHAR -> "VARCHAR"
                    site.addzero.util.lsi.database.DatabaseColumnType.TEXT -> "TEXT"
                    site.addzero.util.lsi.database.DatabaseColumnType.DATETIME -> "TIMESTAMP"
                    site.addzero.util.lsi.database.DatabaseColumnType.BOOLEAN -> "BOOLEAN"
                    else -> columnType.name
                }
            }
            else -> columnType.name
        }
    }
}

data class DatabaseTableInfo(
    val name: String,
    val comment: String?,
    val columns: MutableMap<String, DatabaseColumnInfo>,
    var primaryKeys: List<String> = emptyList(),
    var foreignKeys: List<ForeignKeyInfo> = emptyList()
)

data class DatabaseColumnInfo(
    val name: String,
    val type: String,
    val size: Int,
    val isNullable: Boolean,
    val defaultValue: String?,
    val comment: String?
)

data class ForeignKeyInfo(
    val columnName: String,
    val referencedTable: String,
    val referencedColumn: String,
    val constraintName: String?
)

data class SchemaComparison(
    val newTables: List<String>,
    val modifiedTables: Map<String, TableModification>,
    val droppedTables: List<String>,
    val databaseTables: Map<String, DatabaseTableInfo>
)

data class TableModification(
    val addedColumns: List<ColumnInfo>,
    val modifiedColumns: List<ColumnInfo>,
    val droppedColumns: List<ColumnInfo>
)

data class ColumnInfo(
    val name: String,
    val type: String,
    val isNullable: Boolean,
    val defaultValue: String? = null,
    val comment: String? = null,
    val oldType: String? = null
)
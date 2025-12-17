package site.addzero.lsi.analyzer.service

//import site.addzero.entity.JdbcTableMetadata
//import site.addzero.entity.JdbcColumnMetadata
import com.intellij.openapi.project.Project
import site.addzero.entity.JdbcColumnMetadata
import site.addzero.entity.JdbcTableMetadata
import site.addzero.util.db.DatabaseType
import site.addzero.util.db.SqlExecutor
import site.addzero.util.ddlgenerator.delta.compareTo
import site.addzero.util.ddlgenerator.delta.generateDeltaDdl
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.clazz.guessTableName
import site.addzero.util.lsi.database.getDatabaseColumnType

/**
 * 数据库 Schema 服务
 * 用于获取数据库当前的表结构信息，以便进行差量对比
 */
class DatabaseSchemaService(private val project: Project) {

    private val jdbcDetector = com.intellij.openapi.components.ServiceManager.getService(JdbcConnectionDetectorService::class.java)

    /**
     * 获取 SQL 执行器
     */
    fun getSqlExecutor(connInfo: JdbcConnectionDetectorService.ConnectionInfo): SqlExecutor {
        return SqlExecutor(
            url = connInfo.url,
            username = connInfo.username ?: "",
            password = connInfo.password ?: ""
        )
    }

    /**
     * 获取数据库中所有表的元数据信息（返回 JdbcTableMetadata 列表）
     */
    fun getDatabaseTablesAsJdbcMetadata(connInfo: JdbcConnectionDetectorService.ConnectionInfo): List<JdbcTableMetadata> {
        val tables = mutableListOf<JdbcTableMetadata>()

        // 建立数据库连接以获取元数据
        val conn = java.sql.DriverManager.getConnection(
            connInfo.url,
            connInfo.username ?: "",
            connInfo.password ?: ""
        )

        try {
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
                val tableSchema = rs.getString("TABLE_SCHEM") ?: ""
                val tableType = rs.getString("TABLE_TYPE")
                val tableComment = rs.getString("REMARKS") ?: ""

                // 获取表的列信息
                val columns = mutableListOf<JdbcColumnMetadata>()
                val columnsRs = databaseMetaData.getColumns(
                    null,
                    null,
                    tableName,
                    null
                )

                // 获取主键信息
                val primaryKeys = mutableSetOf<String>()
                val primaryKeyRs = databaseMetaData.getPrimaryKeys(
                    null,
                    null,
                    tableName
                )
                while (primaryKeyRs.next()) {
                    primaryKeys.add(primaryKeyRs.getString("COLUMN_NAME"))
                }
                primaryKeyRs.close()

                while (columnsRs.next()) {
                    val columnName = columnsRs.getString("COLUMN_NAME")
                    val columnType = columnsRs.getString("TYPE_NAME")
                    val columnSize = columnsRs.getInt("COLUMN_SIZE")
                    val isNullable = columnsRs.getString("IS_NULLABLE") == "YES"
                    val columnDefault = columnsRs.getString("COLUMN_DEF")
                    val comment = columnsRs.getString("REMARKS") ?: ""

                    // 获取 JDBC 类型
                    val jdbcType = columnsRs.getInt("DATA_TYPE")

                    columns.add(
                        JdbcColumnMetadata(
                            tableName = tableName,
                            columnName = columnName,
                            jdbcType = jdbcType,
                            columnType = columnType,
                            columnLength = if (columnSize > 0) columnSize else null,
                            nullable = isNullable,
                            nullableFlag = if (isNullable) "YES" else "NO",
                            remarks = comment,
                            defaultValue = columnDefault,
                            isPrimaryKey = primaryKeys.contains(columnName)
                        )
                    )
                }
                columnsRs.close()

                tables.add(
                    JdbcTableMetadata(
                        tableName = tableName,
                        schema = tableSchema,
                        tableType = tableType,
                        remarks = tableComment,
                        columns = columns
                    )
                )
            }

            rs.close()
        } finally {
            conn.close()
        }

        return tables
    }

    /**
     * 生成差量 DDL（使用现有的 delta 逻辑）
     */
    fun generateDeltaDdl(
        pojos: List<LsiClass>,
        databaseType: DatabaseType = DatabaseType.MYSQL
    ): String {
        val connInfo = jdbcDetector.detectConnectionInfo(project)
        val dbTables = getDatabaseTablesAsJdbcMetadata(connInfo)

        return pojos.generateDeltaDdl(dbTables, databaseType)
    }

    /**
     * 比较数据库表结构和 POJO 元数据（保留原有方法以兼容）
     * @deprecated 使用 generateDeltaDdl 方法代替
     */
    @Deprecated("使用 generateDeltaDdl 方法代替")
    fun compareWithDatabase(
        pojos: List<LsiClass>,
        databaseType: DatabaseType
    ): SchemaComparison {
        // 使用新的 delta 生成逻辑来获取比较结果
        val connInfo = jdbcDetector.detectConnectionInfo(project)
        val dbTables = getDatabaseTablesAsJdbcMetadata(connInfo)
        val schemaDiff = pojos.compareTo(dbTables)

        // 转换为旧格式以保持兼容
        val newTables = schemaDiff.newTables.map { it.guessTableName }
        val droppedTables = schemaDiff.droppedTables

        val modifiedTables = mutableMapOf<String, TableModification>()
        schemaDiff.modifiedTables.forEach { modifiedTable ->
            val tableDiff = modifiedTable.diff
            val modifications = TableModification(
                addedColumns = tableDiff.addedColumns.map { field ->
                    ColumnInfo(
                        name = field.columnName ?: field.name ?: "",
                        type = field.getDatabaseColumnType().name,
                        isNullable = field.isNullable,
                        defaultValue = field.defaultValue,
                        comment = field.comment
                    )
                },
                modifiedColumns = tableDiff.modifiedColumns.map { modification ->
                    ColumnInfo(
                        name = modification.field.columnName ?: modification.field.name ?: "",
                        type = modification.field.getDatabaseColumnType().name,
                        isNullable = modification.field.isNullable,
                        defaultValue = modification.field.defaultValue,
                        comment = modification.field.comment
                    )
                },
                droppedColumns = tableDiff.droppedColumns.map { column ->
                    ColumnInfo(
                        name = column.columnName,
                        type = "",
                        isNullable = true
                    )
                }
            )
            modifiedTables[tableDiff.tableName] = modifications
        }

        // 创建一个空的 DatabaseTableInfo map 以保持兼容
        val emptyDatabaseTables = emptyMap<String, DatabaseTableInfo>()

        return SchemaComparison(
            newTables = newTables,
            modifiedTables = modifiedTables,
            droppedTables = droppedTables,
            databaseTables = emptyDatabaseTables
        )
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
package site.addzero.util.ddlgenerator

/**
 * PostgreSQL 方言实现
 */
@Single
class PostgreSqlDdlDialect : DdlDialect {

    override val databaseType: DatabaseType = DatabaseType.POSTGRESQL

    override fun generateCreateTable(lsiClass: LsiClass): String {
        val tableName = lsiClass.guessTableName
        val columns = lsiClass.databaseFields

        val columnsSql = columns.joinToString(",\n  ") { field ->
            buildColumnDefinition(field)
        }

        return """
            |CREATE TABLE "$tableName" (
            |  $columnsSql
            |);
            """.trimMargin()
    }

    override fun generateDropTable(tableName: String): String {
        return "DROP TABLE IF EXISTS \"$tableName\";"
    }

    override fun generateAddColumn(tableName: String, field: LsiField): String {
        val columnDefinition = buildColumnDefinition(field)
        return "ALTER TABLE \"$tableName\" ADD COLUMN $columnDefinition;"
    }

    override fun generateDropColumn(tableName: String, columnName: String): String {
        return "ALTER TABLE \"$tableName\" DROP COLUMN \"$columnName\";"
    }

    override fun generateAddForeignKey(tableName: String, foreignKey: ForeignKeyInfo): String {
        return "ALTER TABLE \"$tableName\" ADD CONSTRAINT \"${foreignKey.name}\" FOREIGN KEY (\"${foreignKey.columnName}\") REFERENCES \"${foreignKey.referencedTable}\" (\"${foreignKey.referencedColumn}\");"
    }

    override fun generateAddComment(lsiClass: LsiClass): String {
        val statements = mutableListOf<String>()
        val tableName = lsiClass.guessTableName

        // 表注释
        if (lsiClass.comment != null) {
            statements.add("COMMENT ON TABLE \"$tableName\" IS '${lsiClass.comment}';")
        }

        // 列注释
        lsiClass.databaseFields.filter { it.comment != null }.forEach { field ->
            val columnName = field.columnName ?: field.name ?: return@forEach
            statements.add("COMMENT ON COLUMN \"$tableName\".\"$columnName\" IS '${field.comment}';")
        }

        return statements.joinToString("\n")
    }

    override fun getColumnTypeName(columnType: DatabaseColumnType, precision: Int?, scale: Int?): String {
        return when (columnType) {
            DatabaseColumnType.INT -> "INTEGER"
            DatabaseColumnType.BIGINT -> "BIGINT"
            DatabaseColumnType.SMALLINT -> "SMALLINT"
            DatabaseColumnType.TINYINT -> "SMALLINT"
            DatabaseColumnType.DECIMAL -> {
                if (precision != null && scale != null) {
                    "DECIMAL($precision, $scale)"
                } else if (precision != null) {
                    "DECIMAL($precision)"
                } else {
                    "DECIMAL"
                }
            }
            DatabaseColumnType.FLOAT -> "REAL"
            DatabaseColumnType.DOUBLE -> "DOUBLE PRECISION"
            DatabaseColumnType.VARCHAR -> {
                if (precision != null) {
                    "VARCHAR($precision)"
                } else {
                    "VARCHAR(255)"
                }
            }
            DatabaseColumnType.CHAR -> {
                if (precision != null) {
                    "CHAR($precision)"
                } else {
                    "CHAR(255)"
                }
            }
            DatabaseColumnType.TEXT -> "TEXT"
            DatabaseColumnType.LONGTEXT -> "TEXT"
            DatabaseColumnType.DATE -> "DATE"
            DatabaseColumnType.TIME -> "TIME"
            DatabaseColumnType.DATETIME -> "TIMESTAMP"
            DatabaseColumnType.TIMESTAMP -> "TIMESTAMP"
            DatabaseColumnType.BOOLEAN -> "BOOLEAN"
            DatabaseColumnType.BLOB -> "BYTEA"
            DatabaseColumnType.BYTES -> "BYTEA"
        }
    }

    override fun generateSchema(lsiClasses: List<LsiClass>): String {
        val createTableStatements = lsiClasses.map { lsiClass -> generateCreateTable(lsiClass) }
        val addConstraintsStatements = lsiClasses.flatMap { lsiClass ->
            val foreignKeyStatements = lsiClass.getDatabaseForeignKeys().map { fk ->
                generateAddForeignKey(lsiClass.guessTableName, fk)
            }
            val commentStatements = if (lsiClass.comment != null || lsiClass.databaseFields.any { it.comment != null }) {
                listOf(generateAddComment(lsiClass))
            } else {
                emptyList()
            }
            foreignKeyStatements + commentStatements
        }

        return (createTableStatements + addConstraintsStatements).joinToString("\n\n")
    }

    private fun buildColumnDefinition(field: LsiField): String {
        val builder = StringBuilder()
        val columnName = field.columnName ?: field.name ?: "unknown"
        val columnType = field.getDatabaseColumnType()

        builder.append("\"$columnName\" ${getColumnTypeName(columnType)}")

        if (!field.isNullable) {
            builder.append(" NOT NULL")
        }

        if (field.isAutoIncrement) {
            builder.append(" GENERATED BY DEFAULT AS IDENTITY")
        }

        if (field.defaultValue != null) {
            builder.append(" DEFAULT ${field.defaultValue}")
        }

        if (field.isPrimaryKey) {
            builder.append(" PRIMARY KEY")
        }

        return builder.toString()
    }
}

package site.addzero.util.ddlgenerator.strategy

import site.addzero.util.db.DatabaseType
import site.addzero.util.ddlgenerator.api.DdlGenerationStrategy
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.clazz.guessTableName
import site.addzero.util.lsi.database.*
import site.addzero.util.lsi.field.LsiField

/**
 * MySQL方言的DDL生成策略
 */
class MySqlDdlStrategy : DdlGenerationStrategy {
    
    private val columnTypeMapper = MySqlColumnTypeMapper()

    override fun getColumnTypeMapper() = columnTypeMapper

    override fun supports(dialect: DatabaseType): Boolean {
        return dialect == DatabaseType.MYSQL
    }

    override fun generateCreateTable(lsiClass: LsiClass): String {
        val tableName = lsiClass.guessTableName
        val columns = lsiClass.databaseFields

        val columnsSql = columns.joinToString(",\n  ") { field ->
            buildColumnDefinition(field)
        }

        // 查找自增主键列以设置AUTO_INCREMENT选项
        val autoIncrementOption = columns.find { it.isAutoIncrement }?.let {
            " AUTO_INCREMENT=1"
        } ?: ""

        return """
            |CREATE TABLE `$tableName` (
            |  $columnsSql
            |)$autoIncrementOption;
            """.trimMargin()
    }

    override fun generateDropTable(tableName: String): String {
        return "DROP TABLE IF EXISTS `$tableName`;"
    }

    override fun generateAddColumn(tableName: String, field: LsiField): String {
        val columnDefinition = buildColumnDefinition(field)
        return "ALTER TABLE `$tableName` ADD COLUMN $columnDefinition;"
    }

    override fun generateDropColumn(tableName: String, columnName: String): String {
        return "ALTER TABLE `$tableName` DROP COLUMN `$columnName`;"
    }

    override fun generateModifyColumn(tableName: String, field: LsiField): String {
        val columnDefinition = buildColumnDefinition(field)
        return "ALTER TABLE `$tableName` MODIFY COLUMN $columnDefinition;"
    }

    override fun generateAddForeignKey(tableName: String, foreignKey: ForeignKeyInfo): String {
        return "ALTER TABLE `$tableName` ADD CONSTRAINT `${foreignKey.name}` FOREIGN KEY (`${foreignKey.columnName}`) REFERENCES `${foreignKey.referencedTable}` (`${foreignKey.referencedColumn}`);"
    }

    override fun generateAddComment(lsiClass: LsiClass): String {
        val statements = mutableListOf<String>()
        val tableName = lsiClass.guessTableName

        // 表注释
        if (lsiClass.comment != null) {
            statements.add("ALTER TABLE `$tableName` COMMENT='${lsiClass.comment}';")
        }

        // 列注释
        lsiClass.databaseFields.filter { it.comment != null }.forEach { field ->
            val columnName = field.columnName ?: field.name ?: return@forEach
            val columnType = field.getDatabaseColumnType()
            statements.add("ALTER TABLE `$tableName` MODIFY `$columnName` ${getColumnTypeName(columnType)} COMMENT '${field.comment}';")
        }

        return statements.joinToString("\n")
    }

    override fun generateSchema(lsiClasses: List<LsiClass>): String {
        // MySQL支持在CREATE TABLE语句中定义外键，所以可以直接按顺序创建表
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

    override fun getColumnTypeName(columnType: DatabaseColumnType, precision: Int?, scale: Int?): String {
        return when (columnType) {
            DatabaseColumnType.INT -> "INT"
            DatabaseColumnType.BIGINT -> "BIGINT"
            DatabaseColumnType.SMALLINT -> "SMALLINT"
            DatabaseColumnType.TINYINT -> "TINYINT"
            DatabaseColumnType.DECIMAL -> {
                if (precision != null && scale != null) {
                    "DECIMAL($precision, $scale)"
                } else if (precision != null) {
                    "DECIMAL($precision)"
                } else {
                    "DECIMAL"
                }
            }
            DatabaseColumnType.FLOAT -> "FLOAT"
            DatabaseColumnType.DOUBLE -> "DOUBLE"
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
            DatabaseColumnType.LONGTEXT -> "LONGTEXT"
            DatabaseColumnType.DATE -> "DATE"
            DatabaseColumnType.TIME -> "TIME"
            DatabaseColumnType.DATETIME -> "DATETIME"
            DatabaseColumnType.TIMESTAMP -> "TIMESTAMP"
            DatabaseColumnType.BOOLEAN -> "TINYINT(1)"
            DatabaseColumnType.BLOB -> "BLOB"
            DatabaseColumnType.BYTES -> "BLOB"
        }
    }

    private fun buildColumnDefinition(field: LsiField): String {
        val builder = StringBuilder()
        val columnName = field.columnName ?: field.name ?: "unknown"
        
        // 检查是否为长文本字段
        val columnTypeName = if (field.isText) {
            // 根据长度选择TEXT类型
            val length = field.length
            when {
                length > 16777215 -> "LONGTEXT"  // > 16MB 使用 LONGTEXT (最大4GB)
                length > 65535 -> "MEDIUMTEXT"  // > 64KB 使用 MEDIUMTEXT (最大16MB)
                else -> "TEXT"  // 默认 TEXT (最大64KB)
            }
        } else {
            val columnType = field.getDatabaseColumnType()
            getColumnTypeName(columnType)
        }
        
        builder.append("`$columnName` $columnTypeName")

        if (!field.isNullable) {
            builder.append(" NOT NULL")
        }

        if (field.isAutoIncrement) {
            builder.append(" AUTO_INCREMENT")
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

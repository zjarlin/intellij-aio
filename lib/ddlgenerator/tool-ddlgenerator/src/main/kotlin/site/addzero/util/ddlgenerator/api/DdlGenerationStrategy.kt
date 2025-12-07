package site.addzero.util.ddlgenerator.api

import site.addzero.util.db.DatabaseType
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.database.DatabaseColumnType
import site.addzero.util.lsi.database.ForeignKeyInfo
import site.addzero.util.lsi.database.databaseFields
import site.addzero.util.lsi.database.getDatabaseForeignKeys

/**
 * DDL生成策略接口 - SPI服务接口
 *
 * 通过 ServiceLoader 机制自动发现和加载不同数据库的实现
 *
 * 注意：此接口通常不需要直接使用，推荐使用 LsiClass 和 LsiField 的扩展函数
 * 
 * 设计原则：
 * 1. 每个策略应实现自己的ColumnTypeMapper来处理类型映射
 * 2. 支持数据库特有类型的扩展
 * 3. getColumnTypeName方法保留用于向后兼容
 */
interface DdlGenerationStrategy {
    
    /**
     * 获取此策略的列类型映射器
     * 
     * 推荐做法：每个策略实现自己的ColumnTypeMapper
     * 例如：MySqlColumnTypeMapper, PostgreSqlColumnTypeMapper
     * 
     * @return 列类型映射器，如果为null则使用旧的getColumnTypeName方法
     */
    fun getColumnTypeMapper(): ColumnTypeMapper? = null
    
    /**
     * 检查此策略是否支持给定的数据库方言
     */
    fun supports(dialect: DatabaseType): Boolean

    /**
     * 生成创建表的DDL语句
     */
    fun generateCreateTable(lsiClass: LsiClass): String

    /**
     * 生成删除表的DDL语句
     */
    fun generateDropTable(tableName: String): String

    /**
     * 生成添加列的DDL语句
     */
    fun generateAddColumn(tableName: String, field: LsiField): String

    /**
     * 生成删除列的DDL语句
     */
    fun generateDropColumn(tableName: String, columnName: String): String

    /**
     * 生成修改列的DDL语句
     */
    fun generateModifyColumn(tableName: String, field: LsiField): String

    /**
     * 生成添加外键约束的DDL语句
     */
    fun generateAddForeignKey(tableName: String, foreignKey: ForeignKeyInfo): String

    /**
     * 生成添加注释的DDL语句
     */
    fun generateAddComment(lsiClass: LsiClass): String

    /**
     * 生成创建索引的DDL语句
     */
    fun generateCreateIndex(tableName: String, index: site.addzero.util.lsi.database.IndexDefinition): String {
        val indexType = if (index.unique) "UNIQUE INDEX" else "INDEX"
        val columns = index.columns.joinToString(", ") { "`$it`" }
        return "CREATE $indexType `${index.name}` ON `$tableName` ($columns);"
    }

    /**
     * 生成多对多中间表的DDL语句（不包含外键）
     * 外键应该在所有表创建完成后单独添加
     */
    fun generateManyToManyTable(table: site.addzero.util.lsi.database.ManyToManyTable): String {
        return """
            |CREATE TABLE `${table.tableName}` (
            |  `${table.leftColumnName}` BIGINT NOT NULL,
            |  `${table.rightColumnName}` BIGINT NOT NULL,
            |  PRIMARY KEY (`${table.leftColumnName}`, `${table.rightColumnName}`)
            |);
        """.trimMargin()
    }

    /**
     * 为多对多中间表生成外键约束
     */
    fun generateManyToManyTableForeignKeys(table: site.addzero.util.lsi.database.ManyToManyTable): List<String> {
        return listOf(
            "ALTER TABLE `${table.tableName}` ADD CONSTRAINT `fk_${table.tableName}_${table.leftColumnName}` FOREIGN KEY (`${table.leftColumnName}`) REFERENCES `${table.leftTableName}` (`id`);",
            "ALTER TABLE `${table.tableName}` ADD CONSTRAINT `fk_${table.tableName}_${table.rightColumnName}` FOREIGN KEY (`${table.rightColumnName}`) REFERENCES `${table.rightTableName}` (`id`);"
        )
    }

    /**
     * 获取特定列类型的数据库表示形式
     * 
     * @deprecated 使用 getColumnTypeMapper() 代替，以获得更好的数据库特有类型支持
     */
    @Deprecated(
        message = "Use getColumnTypeMapper() instead for better database-specific type support",
        replaceWith = ReplaceWith("getColumnTypeMapper()?.mapFieldToColumnType(field)")
    )
    fun getColumnTypeName(columnType: DatabaseColumnType, precision: Int? = null, scale: Int? = null): String

    /**
     * 生成基于多个 LSI 类的完整DDL语句（考虑表之间的依赖关系）
     */
    fun generateSchema(lsiClasses: List<LsiClass>): String {
        // 默认实现：先创建所有表，然后添加外键约束和注释
        val createTableStatements = lsiClasses.map { lsiClass -> generateCreateTable(lsiClass) }
        val addConstraintsStatements = lsiClasses.flatMap { lsiClass ->
            val foreignKeyStatements = lsiClass.getDatabaseForeignKeys().map { fk ->
                generateAddForeignKey(lsiClass.name ?: "", fk)
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
}

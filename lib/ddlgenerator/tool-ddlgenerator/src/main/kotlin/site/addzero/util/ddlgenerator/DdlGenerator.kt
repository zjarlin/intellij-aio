package site.addzero.util.ddlgenerator

import site.addzero.util.ddlgenerator.inter.TableContext
import site.addzero.util.ddlgenerator.model.*

/**
 * DDL生成器上下文类
 * 使用策略模式来适配不同的数据库方言
 */
class DdlGenerator(private val strategy: DdlGenerationStrategy) {
    
    /**
     * 生成创建表的DDL语句
     */
    fun createTable(table: TableDefinition): String {
        return strategy.generateCreateTable(table)
    }

    /**
     * 生成删除表的DDL语句
     */
    fun dropTable(tableName: String): String {
        return strategy.generateDropTable(tableName)
    }

    /**
     * 生成添加列的DDL语句
     */
    fun addColumn(tableName: String, column: ColumnDefinition): String {
        return strategy.generateAddColumn(tableName, column)
    }

    /**
     * 生成删除列的DDL语句
     */
    fun dropColumn(tableName: String, columnName: String): String {
        return strategy.generateDropColumn(tableName, columnName)
    }
    
    /**
     * 生成添加外键约束的DDL语句
     */
    fun addForeignKey(tableName: String, foreignKey: ForeignKeyDefinition): String {
        return strategy.generateAddForeignKey(tableName, foreignKey)
    }
    
    /**
     * 生成添加注释的DDL语句
     */
    fun addComment(table: TableDefinition): String {
        return strategy.generateAddComment(table)
    }
    
    /**
     * 生成基于多个表定义的完整DDL语句（考虑表之间的依赖关系）
     */
    fun createSchema(tables: List<TableDefinition>): String {
        return strategy.generateSchema(tables)
    }
    
    /**
     * 基于表上下文生成完整的数据库模式
     * 考虑表之间的依赖关系和约束
     */
    fun createSchema(context: TableContext): String {
        return strategy.generateSchema(context)
    }

    companion object {
        /**
         * 根据数据库方言创建对应的DDL生成器
         */
        fun createForDialect(dialect: Dialect): DdlGenerator {
            val strategy = when (dialect) {
                Dialect.MYSQL -> MySqlDdlGenerationStrategy()
                Dialect.POSTGRESQL -> PostgreSqlDdlGenerationStrategy()
                Dialect.ORACLE -> throw NotImplementedError("Oracle dialect not yet implemented")
                Dialect.SQLSERVER -> throw NotImplementedError("SQL Server dialect not yet implemented")
                Dialect.H2 -> throw NotImplementedError("H2 dialect not yet implemented")
                Dialect.SQLITE -> throw NotImplementedError("SQLite dialect not yet implemented")
            }
            return DdlGenerator(strategy)
        }
    }
}
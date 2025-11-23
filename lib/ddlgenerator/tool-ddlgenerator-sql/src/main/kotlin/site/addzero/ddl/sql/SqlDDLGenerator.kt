package site.addzero.ddl.sql

import site.addzero.ddl.core.contract.DDLGenerator
import site.addzero.ddl.core.model.TableDefinition

/**
 * 基于SQL方言的DDL生成器实现
 */
class SqlDDLGenerator(
    private val dialect: SqlDialect
) : DDLGenerator {
    
    override fun generateCreateTable(table: TableDefinition): String {
        return dialect.formatCreateTable(table)
    }
    
    override fun generateAlterTableAddColumn(table: TableDefinition): List<String> {
        return dialect.formatAlterTable(table)
    }
    
    companion object {
        /**
         * 根据数据库类型创建生成器
         */
        fun forDatabase(databaseType: String): SqlDDLGenerator {
            val dialect = SqlDialectRegistry.get(databaseType)
            return SqlDDLGenerator(dialect)
        }
    }
}

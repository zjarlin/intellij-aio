package site.addzero.ddl.core.contract

import site.addzero.ddl.core.model.TableDefinition

/**
 * DDL生成器接口
 * 
 * 定义了从表定义生成DDL语句的契约
 */
interface DDLGenerator {
    
    /**
     * 生成CREATE TABLE语句
     * 
     * @param table 表定义
     * @return CREATE TABLE SQL语句
     */
    fun generateCreateTable(table: TableDefinition): String
    
    /**
     * 生成ALTER TABLE ADD COLUMN语句
     * 
     * @param table 表定义（包含需要添加的列）
     * @return ALTER TABLE SQL语句列表
     */
    fun generateAlterTableAddColumn(table: TableDefinition): List<String>
}

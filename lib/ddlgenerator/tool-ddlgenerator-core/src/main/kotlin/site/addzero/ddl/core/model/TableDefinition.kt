package site.addzero.ddl.core.model

/**
 * 表定义
 * 
 * @property name 表英文名称
 * @property comment 表中文名称/注释
 * @property columns 列定义列表
 * @property primaryKey 主键列名
 * @property databaseName 数据库名称（可选）
 */
data class TableDefinition(
    val name: String,
    val comment: String,
    val columns: List<ColumnDefinition>,
    val primaryKey: String? = null,
    val databaseName: String = ""
) {
    /**
     * 获取所有非主键列
     */
    val nonPrimaryColumns: List<ColumnDefinition>
        get() = columns.filter { !it.primaryKey }
    
    /**
     * 获取主键列
     */
    val primaryKeyColumn: ColumnDefinition?
        get() = columns.find { it.primaryKey }
}

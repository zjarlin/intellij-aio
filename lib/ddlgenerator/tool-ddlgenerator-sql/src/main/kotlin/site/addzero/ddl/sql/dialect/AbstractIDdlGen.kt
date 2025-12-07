package site.addzero.ddl.sql.dialect

import site.addzero.ddl.sql.IDdlGen
import site.addzero.lsi.clazz.dataBaseOrSchemaName
import site.addzero.lsi.clazz.primaryKeyName
import site.addzero.lsi.field.isPrimaryKey
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.clazz.guessTableName
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.field.isNullable

/**
 * 类型映射配置接口
 */
interface TypeMapping {
    /**
     * Java类型到数据库类型的映射函数
     * @param column 列定义
     * @return 数据库类型字符串，如果不能处理则返回null
     */
    fun mapType(column: LsiField): String?
}

/**
 * 抽象SQL方言基类
 * 提取各数据库方言的公共功能
 */
abstract class AbstractIDdlGen : IDdlGen {

    /**
     * 格式化ALTER TABLE语句的默认实现
     */
    override fun formatAlterTable(table: LsiClass): List<String> {
        return table.fields.map { column ->
            val tableRef = if (table.dataBaseOrSchemaName.isNotBlank()) {
                quoteIdentifier(table.dataBaseOrSchemaName) +
                        ".${quoteIdentifier(table.guessTableName)}"
            } else {
                quoteIdentifier(table.guessTableName)
            }
            "ALTER TABLE $tableRef ADD COLUMN ${formatColumnDefinition(column)};"
        }
    }

    /**
     * 格式化CREATE TABLE语句的默认实现
     * 子类可以覆盖此方法以提供特定数据库的实现
     */
    override fun formatCreateTable(table: LsiClass): String {
        val lines = mutableListOf<String>()

        // 默认的CREATE TABLE语句
        val identifier = table.guessTableName
        lines.add("CREATE TABLE IF NOT EXISTS ${quoteIdentifier(identifier)} (")

        // 列定义
        val columnDefs = table.fields.map { "    ${formatColumnDefinition(it)}" }
        lines.addAll(columnDefs.map { "$it," })

        // 主键约束
        table.primaryKeyName?.let {
            lines.add("    PRIMARY KEY (${quoteIdentifier(it)})")
        }

        // 添加表选项
        val tableOptions = getTableOptions()
        if (tableOptions.isNotBlank()) {
            lines.add(") $tableOptions")
        } else {
            lines.add(")")
        }

        // 表注释
        val value = table.comment ?: ""
        if (value.isNotBlank()) {
            lines.add("COMMENT = '${escapeString(value)}'")
        }

        lines.add(";")

        return lines.joinToString("\n")
    }

    /**
     * 获取表选项的默认实现
     * 子类可以覆盖此方法以提供特定数据库的表选项
     */
    protected open fun getTableOptions(): String = ""

    /**
     * 格式化列定义的默认实现
     * 子类可以覆盖此方法以提供特定数据库的实现
     */
    override fun formatColumnDefinition(column: LsiField): String {
        val parts = mutableListOf<String>()

        // 添加基本的列定义部分
        parts.addAll(getBaseColumnDefinitionParts(column))

        // 注释
        val value = column.comment ?: ""
        if (value.isNotBlank()) {
            parts.add("COMMENT '${escapeString(value)}'")
        }

        return parts.joinToString(" ")
    }

    /**
     * 获取基础列定义部分的默认实现
     * 子类可以覆盖此方法以提供特定数据库的实现
     */
    protected open fun getBaseColumnDefinitionParts(column: LsiField): List<String> {
        val parts = mutableListOf<String>()

        // 列名
        val identifier = column.columnName?:""
        parts.add(quoteIdentifier(identifier))

        // 数据类型
        parts.add(mapJavaType(column))

        // NULL约束
        if (!column.isNullable || column.isPrimaryKey) {
            parts.add("NOT NULL")
        }

        return parts
    }

    /**
     * 默认的标识符引用方式（MySQL风格）
     */
    override fun quoteIdentifier(identifier: String): String = "`$identifier`"

    /**
     * 默认的字符串转义方式
     */
    override fun escapeString(value: String): String = value.replace("'", "''")
}

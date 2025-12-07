package site.addzero.ddl.sql

import site.addzero.util.db.DatabaseType
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField

/**
 * SQL方言接口
 *
 * 定义了不同数据库的SQL生成规则
 */
interface IDdlGen{

    /**
     * 方言名称（对应数据库类型）
     */
    val name: DatabaseType

    /**
     * 将Java类型映射为数据库类型
     *
     * @param column 列定义
     * @return 数据库类型字符串（如 "VARCHAR(255)"）
     */
    fun mapJavaType(column: LsiField): String

    /**
     * 格式化列定义
     *
     * @param column 列定义
     * @return SQL列定义片段（如 "`name` VARCHAR(255) NOT NULL COMMENT '姓名'"）
     */
    fun formatColumnDefinition(column: LsiField): String

    /**
     * 生成CREATE TABLE语句
     *
     * @param table 表定义
     * @return CREATE TABLE SQL语句
     */
    fun formatCreateTable(table: LsiClass): String

    /**
     * 生成ALTER TABLE ADD COLUMN语句
     *
     * @param table 表定义
     * @return ALTER TABLE SQL语句列表
     */
    fun formatAlterTable(table: LsiClass): List<String>

    /**
     * 引用标识符（表名、列名）
     *
     * @param identifier 标识符
     * @return 引用后的标识符
     */
    fun quoteIdentifier(identifier: String): String = "`$identifier`"

    /**
     * 转义字符串值
     *
     * @param value 字符串值
     * @return 转义后的值
     */
    fun escapeString(value: String): String = value.replace("'", "''")
}

/**
 * SQL方言注册表
 */
object SqlDialectRegistry {

    private val dialects = mutableMapOf<String, IDdlGen>()

    /**
     * 注册方言
     */
    fun register(dialect: IDdlGen) {
        dialects[dialect.name.toString()] = dialect
    }

    /**
     * 获取方言
     */
    fun get(databaseType: String): IDdlGen {
        return dialects[databaseType]
            ?: throw IllegalArgumentException("Unsupported database type: $databaseType")
    }

    /**
     * 获取所有已注册的方言
     */
    @Suppress("unused")
    fun getAll(): Map<String, IDdlGen> = dialects.toMap()

    /**
     * 检查是否支持指定的数据库类型
     */
    @Suppress("unused")
    fun isSupported(databaseType: String): Boolean {
        return dialects.containsKey(databaseType)
    }
}

package site.addzero.util.ddlgenerator.extension

import site.addzero.util.ddlgenerator.api.DdlGeneratorFactory

import site.addzero.util.db.DatabaseType
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.clazz.guessTableName
import site.addzero.util.lsi.field.LsiField

/**
 * LSI DDL 扩展函数 - Kotlin风格的DDL生成API
 *
 * 直接使用策略模式，无需中间包装层
 * 
 * 使用示例：
 * ```kotlin
 * val createDdl = userLsiClass.toCreateTableDDL(DatabaseType.MYSQL)
 * val dropDdl = userLsiClass.toDropTableDDL(DatabaseType.MYSQL)
 * val addColumnDdl = userIdField.toAddColumnDDL("users", DatabaseType.MYSQL)
 * ```
 */

// ============ LsiClass 扩展函数 ============

/**
 * 生成CREATE TABLE的DDL语句
 */
fun LsiClass.toCreateTableDDL(dialect: DatabaseType): String {
    return DdlGeneratorFactory.getStrategy(dialect).generateCreateTable(this)
}

/**
 * 生成CREATE TABLE的DDL语句（使用字符串方言名）
 */
fun LsiClass.toCreateTableDDL(dialectName: String): String {
    return toCreateTableDDL(DatabaseType.valueOf(dialectName.uppercase()))
}

/**
 * 生成DROP TABLE的DDL语句
 */
fun LsiClass.toDropTableDDL(dialect: DatabaseType): String {
    return DdlGeneratorFactory.getStrategy(dialect).generateDropTable(this.guessTableName)
}

/**
 * 生成DROP TABLE的DDL语句（使用字符串方言名）
 */
fun LsiClass.toDropTableDDL(dialectName: String): String {
    return toDropTableDDL(DatabaseType.valueOf(dialectName.uppercase()))
}

/**
 * 生成ALTER TABLE添加注释的DDL语句
 */
fun LsiClass.toAddCommentDDL(dialect: DatabaseType): String {
    return DdlGeneratorFactory.getStrategy(dialect).generateAddComment(this)
}

/**
 * 生成ALTER TABLE添加注释的DDL语句（使用字符串方言名）
 */
fun LsiClass.toAddCommentDDL(dialectName: String): String {
    return toAddCommentDDL(DatabaseType.valueOf(dialectName.uppercase()))
}

// ============ LsiField 扩展函数 ============

/**
 * 生成ADD COLUMN的DDL语句
 */
fun LsiField.toAddColumnDDL(tableName: String, dialect: DatabaseType): String {
    return DdlGeneratorFactory.getStrategy(dialect).generateAddColumn(tableName, this)
}

/**
 * 生成ADD COLUMN的DDL语句（使用字符串方言名）
 */
fun LsiField.toAddColumnDDL(tableName: String, dialectName: String): String {
    return toAddColumnDDL(tableName, DatabaseType.valueOf(dialectName.uppercase()))
}

/**
 * 生成DROP COLUMN的DDL语句
 */
fun LsiField.toDropColumnDDL(tableName: String, dialect: DatabaseType): String {
    val columnName = this.columnName ?: this.name ?: "unknown"
    return DdlGeneratorFactory.getStrategy(dialect).generateDropColumn(tableName, columnName)
}

/**
 * 生成DROP COLUMN的DDL语句（使用字符串方言名）
 */
fun LsiField.toDropColumnDDL(tableName: String, dialectName: String): String {
    return toDropColumnDDL(tableName, DatabaseType.valueOf(dialectName.uppercase()))
}

/**
 * 生成MODIFY COLUMN的DDL语句
 */
fun LsiField.toModifyColumnDDL(tableName: String, dialect: DatabaseType): String {
    return DdlGeneratorFactory.getStrategy(dialect).generateModifyColumn(tableName, this)
}

/**
 * 生成MODIFY COLUMN的DDL语句（使用字符串方言名）
 */
fun LsiField.toModifyColumnDDL(tableName: String, dialectName: String): String {
    return toModifyColumnDDL(tableName, DatabaseType.valueOf(dialectName.uppercase()))
}

// ============ 批量操作扩展函数 ============

/**
 * 生成多个表的完整数据库schema（包括外键约束和注释）
 */
fun List<LsiClass>.toSchemaDDL(dialect: DatabaseType): String {
    return DdlGeneratorFactory.getStrategy(dialect).generateSchema(this)
}

/**
 * 生成多个表的完整数据库schema（使用字符串方言名）
 */
fun List<LsiClass>.toSchemaDDL(dialectName: String): String {
    return toSchemaDDL(DatabaseType.valueOf(dialectName.uppercase()))
}

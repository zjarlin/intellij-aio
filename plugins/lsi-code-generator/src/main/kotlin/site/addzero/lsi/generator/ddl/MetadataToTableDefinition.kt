package site.addzero.lsi.generator.ddl

import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.clazz.guessTableName
import site.addzero.util.lsi.field.LsiField

import site.addzero.util.lsi.database.databaseFields
import site.addzero.util.lsi.database.isPrimaryKey

// 注意：TableDefinition 和 ColumnDefinition 需要定义或导入
// 如果这些类不存在，需要创建它们或使用其他方式

data class TableDefinition(
    val name: String,
    val comment: String,
    val columns: List<ColumnDefinition>,
    val primaryKey: String?
)

data class ColumnDefinition(
    val name: String,
    val javaType: String,
    val comment: String,
    val nullable: Boolean,
    val primaryKey: Boolean,
    val autoIncrement: Boolean
)

fun LsiClass.toTableDefinition(): TableDefinition {
    val columns = databaseFields.map { it.toColumnDefinition() }
    val primaryKey = columns.find { it.primaryKey }?.name

    return TableDefinition(
        name = this.guessTableName,
        comment = this.comment?.extractFirstLine() ?: (this.name ?: "unknown"),
        columns = columns,
        primaryKey = primaryKey
    )
}

fun LsiField.toColumnDefinition(): ColumnDefinition {
    val fieldName = this.name ?: "unknown"
    return ColumnDefinition(
        name = this.columnName ?: fieldName.camelToSnake(),
        javaType = this.type?.qualifiedName ?: "java.lang.String",
        comment = this.comment?.extractFirstLine() ?: fieldName,
        nullable = this.isNullable && !this.isPrimaryKey,
        primaryKey = this.isPrimaryKey,
        autoIncrement = this.isPrimaryKey && (this.typeName?.contains("Long") == true || this.typeName?.contains("Int") == true)
    )
}

private fun String.camelToSnake(): String {
    val result = StringBuilder()
    for ((index, char) in this.withIndex()) {
        if (char.isUpperCase() && index > 0) {
            result.append('_')
        }
        result.append(char.lowercaseChar())
    }
    return result.toString()
}

private fun String.extractFirstLine(): String {
    return this.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { it.removePrefix("/**").removePrefix("/*").removePrefix("*").removePrefix("//").trim() }
        .filter { it.isNotBlank() && !it.startsWith("@") }
        .firstOrNull() ?: this.take(50)
}

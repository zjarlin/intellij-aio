package site.addzero.lsi.generator.ddl

import site.addzero.ddl.core.model.ColumnDefinition
import site.addzero.ddl.core.model.TableDefinition
import site.addzero.lsi.analyzer.metadata.FieldMetadata
import site.addzero.lsi.analyzer.metadata.PojoMetadata

fun PojoMetadata.toTableDefinition(): TableDefinition {
    val columns = dbFields.map { it.toColumnDefinition() }
    val primaryKey = columns.find { it.primaryKey }?.name
    
    return TableDefinition(
        name = tableName ?: className.camelToSnake(),
        comment = comment?.extractFirstLine() ?: className,
        columns = columns,
        primaryKey = primaryKey
    )
}

fun FieldMetadata.toColumnDefinition(): ColumnDefinition {
    return ColumnDefinition(
        name = columnName ?: name.camelToSnake(),
        javaType = typeQualifiedName ?: "java.lang.String",
        comment = comment?.extractFirstLine() ?: name,
        nullable = nullable && !isPrimaryKey,
        primaryKey = isPrimaryKey,
        autoIncrement = isPrimaryKey && (typeQualifiedName?.contains("Long") == true || typeQualifiedName?.contains("Integer") == true)
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

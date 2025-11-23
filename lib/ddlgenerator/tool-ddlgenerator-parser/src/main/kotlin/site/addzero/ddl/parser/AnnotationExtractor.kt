package site.addzero.ddl.parser

import site.addzero.ddl.core.model.TableDefinition
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.clazz.guessTableName
import site.addzero.util.lsi.field.LsiField

/**
 * 注解提取器
 *
 * 从LSI字段中提取JPA、Swagger、Excel等注解信息
 */
class AnnotationExtractor {

    /**
     * 提取表信息
     */
    fun extractTableInfo(lsiClass: LsiClass): TableDefinition {
        // 使用 LSI 提供的 guessTableName
        val tableName = lsiClass.guessTableName.ifBlank {
            lsiClass.name ?: "unknown_table"
        }

        // 尝试从注释或Swagger注解获取表注释
        val comment = extractComment(lsiClass)

        // 提取所有字段的列定义
        val columns = lsiClass.fields
            .filter { !it.isStatic } // 过滤静态字段
            .filter { field -> field.type?.isCollectionType != true } // 过滤集合类型
            .filter { !it.annotations.any { anno -> anno.qualifiedName?.endsWith(".Transient") == true } } // 过滤 @Transient
            .map { field ->
                site.addzero.ddl.core.model.ColumnDefinition(
                    name = extractColumnName(field),
                    javaType = field.type?.qualifiedName ?: "java.lang.String",
                    comment = extractFieldComment(field),
                    nullable = !isPrimaryKey(field) && isNullable(field), // 主键不可空
                    primaryKey = isPrimaryKey(field),
                    autoIncrement = isAutoIncrement(field),
                    length = extractLength(field),
                    precision = extractPrecision(field),
                    scale = extractScale(field)
                )
            }

        // 查找主键列名
        val primaryKey = columns.find { it.primaryKey }?.name

        return TableDefinition(
            name = tableName,
            comment = comment,
            columns = columns,
            primaryKey = primaryKey
        )
    }


    /**
     * 提取列名
     */
    private fun extractColumnName(field: LsiField): String {
        // 使用 LSI 提供的 columnName，如果没有则使用字段名
        return field.columnName ?: field.name ?: "unknown_column"
    }

    /**
     * 提取字段注释
     */
    private fun extractFieldComment(field: LsiField): String {
        // 1. 尝试从文档注释获取
        field.comment?.let {
            if (it.isNotBlank()) return it
        }

        // 2. 尝试从 Swagger 注解获取
        val swaggerAnno = field.annotations.find {
            it.qualifiedName?.contains("ApiModelProperty") == true ||
            it.qualifiedName?.contains("Schema") == true
        }
        swaggerAnno?.let { anno ->
            val value = anno.attributes["value"]?.toString()?.trim('"')
            if (!value.isNullOrBlank()) return value
        }

        // 3. 尝试从 Excel 注解获取
        val excelAnno = field.annotations.find {
            it.qualifiedName?.contains("ExcelProperty") == true
        }
        excelAnno?.let { anno ->
            val value = anno.attributes["value"]?.toString()?.trim('"', '[', ']')
            if (!value.isNullOrBlank()) return value
        }

        // 4. 默认返回字段名
        return field.name ?: "unknown"
    }

    /**
     * 提取类注释
     */
    private fun extractComment(lsiClass: LsiClass): String {
        // 1. 尝试从文档注释获取
        lsiClass.comment?.let {
            if (it.isNotBlank()) return it
        }

        // 2. 尝试从 Swagger 注解获取
        val swaggerAnno = lsiClass.annotations.find {
            it.qualifiedName?.contains("Api") == true ||
            it.qualifiedName?.contains("Tag") == true
        }
        swaggerAnno?.let { anno ->
            anno.attributes["tags"]?.let {
                val comment = it.toString().trim('"', '[', ']')
                if (comment.isNotBlank()) return comment
            }
            anno.attributes["value"]?.let {
                val comment = it.toString().trim('"')
                if (comment.isNotBlank()) return comment
            }
        }

        // 3. 默认返回类名
        return lsiClass.name ?: "unknown"
    }

    /**
     * 判断是否为主键
     */
    private fun isPrimaryKey(field: LsiField): Boolean {
        return field.annotations.any {
            it.qualifiedName?.endsWith(".Id") == true
        } || field.name?.equals("id", ignoreCase = true) == true
    }

    /**
     * 判断是否可空
     */
    private fun isNullable(field: LsiField): Boolean {
        // Kotlin的可空类型
        if (field.type?.qualifiedName?.endsWith("?") == true) {
            return true
        }

        // 检查 @Column(nullable = false)
        val columnAnno = field.annotations.find {
            it.qualifiedName?.endsWith(".Column") == true
        }
        columnAnno?.attributes?.get("nullable")?.let {
            if (it.toString() == "false") return false
        }

        // 检查 @NotNull 注解
        val hasNotNull = field.annotations.any {
            it.qualifiedName?.endsWith(".NotNull") == true
        }
        if (hasNotNull) return false

        // 默认可空
        return true
    }

    /**
     * 判断是否自增
     */
    private fun isAutoIncrement(field: LsiField): Boolean {
        val generatedValue = field.annotations.find {
            it.qualifiedName?.endsWith(".GeneratedValue") == true
        }

        generatedValue?.attributes?.get("strategy")?.let {
            val strategy = it.toString()
            return strategy.contains("IDENTITY") || strategy.contains("AUTO")
        }

        return false
    }

    /**
     * 提取长度
     */
    private fun extractLength(field: LsiField): Int {
        val columnAnno = field.annotations.find {
            it.qualifiedName?.endsWith(".Column") == true
        }
        columnAnno?.attributes?.get("length")?.let {
            return it.toString().toIntOrNull() ?: -1
        }
        return -1
    }
    /**
     * 提取精度（用于数值类型）
     */
    private fun extractPrecision(field: LsiField): Int {
        val columnAnno = field.annotations.find {
            it.qualifiedName?.endsWith(".Column") == true
        }

        columnAnno?.attributes?.get("precision")?.let {
            return it.toString().toIntOrNull() ?: -1
        }

        return -1
    }

    /**
     * 提取小数位数（用于数值类型）
     */
    private fun extractScale(field: LsiField): Int {
        val columnAnno = field.annotations.find {
            it.qualifiedName?.endsWith(".Column") == true
        }
        columnAnno?.attributes?.get("scale")?.let {
            return it.toString().toIntOrNull() ?: -1
        }
        return -1
    }
}

package site.addzero.util.ddlgenerator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import site.addzero.util.db.DatabaseType
import site.addzero.util.ddlgenerator.extension.toCreateTableDDL

/**
 * 长文本字段DDL生成测试
 */
class LongTextDdlGenerationTest {

    @Test
    fun `test MySQL TEXT for medium length string`() {
        // Given: 字段长度为2000（超过1000）
        val article = mockClass(
            name = "Article",
            qualifiedName = "com.example.Article",
            tableName = "article",
            fields = listOf(
                mockField(
                    name = "id",
                    typeName = "Long",
                    annotations = listOf(mockAnnotation("Id", "Id")),
                    isNullable = false,
                    isPrimitive = true
                ),
                mockField(
                    name = "content",
                    typeName = "String",
                    comment = "内容",
                    annotations = listOf(
                        mockAnnotation("Length", "Length", mapOf("value" to "2000"))
                    ),
                    isNullable = false
                )
            )
        )

        // When: 生成MySQL DDL
        val ddl = article.toCreateTableDDL(DatabaseType.MYSQL)

        // Then: 应该使用TEXT类型
        assertTrue(ddl.contains("`content` TEXT"), 
            "长度2000应该使用TEXT类型")
        assertFalse(ddl.contains("VARCHAR"), 
            "不应该使用VARCHAR")
        
        println("=== MySQL TEXT DDL ===")
        println(ddl)
    }

    @Test
    fun `test MySQL MEDIUMTEXT for large string`() {
        // Given: 字段长度为100000（超过65535）
        val article = mockClass(
            name = "Article",
            qualifiedName = "com.example.Article",
            tableName = "article",
            fields = listOf(
                mockField(
                    name = "id",
                    typeName = "Long",
                    annotations = listOf(mockAnnotation("Id", "Id")),
                    isNullable = false
                ),
                mockField(
                    name = "content",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("Length", "Length", mapOf("value" to "100000"))
                    ),
                    isNullable = false
                )
            )
        )

        // When: 生成MySQL DDL
        val ddl = article.toCreateTableDDL(DatabaseType.MYSQL)

        // Then: 应该使用MEDIUMTEXT类型
        assertTrue(ddl.contains("`content` MEDIUMTEXT"), 
            "长度100000应该使用MEDIUMTEXT类型")
        
        println("=== MySQL MEDIUMTEXT DDL ===")
        println(ddl)
    }

    @Test
    fun `test MySQL LONGTEXT for very large string`() {
        // Given: 字段长度为20000000（超过16777215）
        val article = mockClass(
            name = "Article",
            qualifiedName = "com.example.Article",
            tableName = "article",
            fields = listOf(
                mockField(
                    name = "id",
                    typeName = "Long",
                    annotations = listOf(mockAnnotation("Id", "Id")),
                    isNullable = false
                ),
                mockField(
                    name = "fullText",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("Length", "Length", mapOf("value" to "20000000"))
                    ),
                    isNullable = false
                )
            )
        )

        // When: 生成MySQL DDL
        val ddl = article.toCreateTableDDL(DatabaseType.MYSQL)

        // Then: 应该使用LONGTEXT类型
        assertTrue(ddl.contains("`fullText` LONGTEXT") || ddl.contains("`full_text` LONGTEXT"), 
            "长度20000000应该使用LONGTEXT类型")
        
        println("=== MySQL LONGTEXT DDL ===")
        println(ddl)
    }

    @Test
    fun `test MySQL VARCHAR for short string`() {
        // Given: 字段长度为255（未超过1000）
        val article = mockClass(
            name = "Article",
            qualifiedName = "com.example.Article",
            tableName = "article",
            fields = listOf(
                mockField(
                    name = "id",
                    typeName = "Long",
                    annotations = listOf(mockAnnotation("Id", "Id")),
                    isNullable = false
                ),
                mockField(
                    name = "title",
                    typeName = "String",
                    comment = "标题",
                    annotations = listOf(
                        mockAnnotation("Length", "Length", mapOf("value" to "255"))
                    ),
                    isNullable = false
                )
            )
        )

        // When: 生成MySQL DDL
        val ddl = article.toCreateTableDDL(DatabaseType.MYSQL)

        // Then: 应该使用VARCHAR类型
        assertTrue(ddl.contains("`title` VARCHAR"), 
            "长度255应该使用VARCHAR类型")
        assertFalse(ddl.contains("TEXT"), 
            "短字符串不应该使用TEXT")
        
        println("=== MySQL VARCHAR DDL ===")
        println(ddl)
    }

    @Test
    fun `test PostgreSQL TEXT for all text fields`() {
        // Given: 不同长度的文本字段
        val article = mockClass(
            name = "Article",
            qualifiedName = "com.example.Article",
            tableName = "article",
            fields = listOf(
                mockField(
                    name = "id",
                    typeName = "Long",
                    annotations = listOf(mockAnnotation("Id", "Id")),
                    isNullable = false
                ),
                mockField(
                    name = "title",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("Length", "Length", mapOf("value" to "255"))
                    ),
                    isNullable = false
                ),
                mockField(
                    name = "content",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("Length", "Length", mapOf("value" to "5000"))
                    ),
                    isNullable = false
                ),
                mockField(
                    name = "fullText",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("Length", "Length", mapOf("value" to "20000000"))
                    ),
                    isNullable = false
                )
            )
        )

        // When: 生成PostgreSQL DDL
        val ddl = article.toCreateTableDDL(DatabaseType.POSTGRESQL)

        // Then: PostgreSQL统一使用TEXT
        assertTrue(ddl.contains("\"title\" VARCHAR"), 
            "短字符串仍使用VARCHAR")
        assertTrue(ddl.contains("\"content\" TEXT"), 
            "长文本使用TEXT")
        assertTrue(ddl.contains("\"fullText\" TEXT") || ddl.contains("\"full_text\" TEXT"), 
            "超长文本也使用TEXT")
        assertFalse(ddl.contains("LONGTEXT"), 
            "PostgreSQL不使用LONGTEXT")
        assertFalse(ddl.contains("MEDIUMTEXT"), 
            "PostgreSQL不使用MEDIUMTEXT")
        
        println("=== PostgreSQL TEXT DDL ===")
        println(ddl)
    }

    @Test
    fun `test Lob annotation generates TEXT`() {
        // Given: 使用@Lob注解的字段
        val article = mockClass(
            name = "Article",
            qualifiedName = "com.example.Article",
            tableName = "article",
            fields = listOf(
                mockField(
                    name = "id",
                    typeName = "Long",
                    annotations = listOf(mockAnnotation("Id", "Id")),
                    isNullable = false
                ),
                mockField(
                    name = "richContent",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("Lob", "Lob", emptyMap())
                    ),
                    isNullable = false
                )
            )
        )

        // When: 生成MySQL DDL
        val mysqlDdl = article.toCreateTableDDL(DatabaseType.MYSQL)
        
        // Then: 应该生成TEXT类型
        assertTrue(mysqlDdl.contains("TEXT"), 
            "@Lob应该生成TEXT类型")
        
        // When: 生成PostgreSQL DDL
        val pgDdl = article.toCreateTableDDL(DatabaseType.POSTGRESQL)
        
        // Then: PostgreSQL也应该生成TEXT
        assertTrue(pgDdl.contains("TEXT"), 
            "@Lob在PostgreSQL也应该生成TEXT")
        
        println("=== @Lob MySQL DDL ===")
        println(mysqlDdl)
        println("\n=== @Lob PostgreSQL DDL ===")
        println(pgDdl)
    }

    @Test
    fun `test Column columnDefinition TEXT`() {
        // Given: 使用@Column(columnDefinition="TEXT")的字段
        val article = mockClass(
            name = "Article",
            qualifiedName = "com.example.Article",
            tableName = "article",
            fields = listOf(
                mockField(
                    name = "id",
                    typeName = "Long",
                    annotations = listOf(mockAnnotation("Id", "Id")),
                    isNullable = false
                ),
                mockField(
                    name = "description",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("Column", "Column", mapOf(
                            "columnDefinition" to "TEXT"
                        ))
                    ),
                    isNullable = false
                )
            )
        )

        // When: 生成DDL
        val ddl = article.toCreateTableDDL(DatabaseType.MYSQL)

        // Then: 应该使用TEXT类型
        assertTrue(ddl.contains("TEXT"), 
            "@Column(columnDefinition='TEXT')应该生成TEXT类型")
        
        println("=== Column columnDefinition DDL ===")
        println(ddl)
    }

    @Test
    fun `test mixed field types in same table`() {
        // Given: 表中混合短字符串和长文本
        val blog = mockClass(
            name = "BlogPost",
            qualifiedName = "com.example.BlogPost",
            tableName = "blog_post",
            fields = listOf(
                mockField(
                    name = "id",
                    typeName = "Long",
                    annotations = listOf(mockAnnotation("Id", "Id")),
                    isNullable = false
                ),
                mockField(
                    name = "title",
                    typeName = "String",
                    comment = "标题",
                    annotations = listOf(
                        mockAnnotation("Length", "Length", mapOf("value" to "100"))
                    ),
                    isNullable = false
                ),
                mockField(
                    name = "summary",
                    typeName = "String",
                    comment = "摘要",
                    annotations = listOf(
                        mockAnnotation("Length", "Length", mapOf("value" to "500"))
                    ),
                    isNullable = true
                ),
                mockField(
                    name = "content",
                    typeName = "String",
                    comment = "正文",
                    annotations = listOf(
                        mockAnnotation("Length", "Length", mapOf("value" to "10000"))
                    ),
                    isNullable = false
                ),
                mockField(
                    name = "richContent",
                    typeName = "String",
                    comment = "富文本",
                    annotations = listOf(
                        mockAnnotation("Lob", "Lob", emptyMap())
                    ),
                    isNullable = true
                )
            )
        )

        // When: 生成MySQL DDL
        val ddl = blog.toCreateTableDDL(DatabaseType.MYSQL)

        // Then: 应该正确区分
        assertTrue(ddl.contains("`title` VARCHAR(100)"), 
            "title应该是VARCHAR(100)")
        assertTrue(ddl.contains("`summary` VARCHAR(500)"), 
            "summary应该是VARCHAR(500)")
        assertTrue(ddl.contains("`content` TEXT"), 
            "content应该是TEXT")
        assertTrue(ddl.contains("`richContent` TEXT") || ddl.contains("`rich_content` TEXT"), 
            "richContent应该是TEXT")
        
        println("=== Mixed Field Types DDL ===")
        println(ddl)
    }
}

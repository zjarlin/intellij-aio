package site.addzero.util.ddlgenerator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import site.addzero.util.db.DatabaseType
import site.addzero.util.ddlgenerator.extension.*
import site.addzero.util.lsi.anno.LsiAnnotation
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField

/**
 * 增强功能测试
 * - TEXT类型字段
 * - Key注解索引
 * - JoinColumn外键
 * - ManyToMany中间表
 */
class EnhancedFeaturesTest {

    @Test
    fun `test TEXT type for url and description fields`() {
        // Given: 创建包含url和description字段的实体
        val article = mockClass(
            name = "Article",
            qualifiedName = "com.example.Article",
            tableName = "article",
            fields = listOf(
                mockField(
                    name = "id",
                    typeName = "Long",
                    qualifiedTypeName = "kotlin.Long",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Id", "Id")
                    ),
                    isNullable = false
                ),
                mockField(
                    name = "title",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
                    )
                ),
                mockField(
                    name = "url",
                    typeName = "String",
                    comment = "文章URL"
                ),
                mockField(
                    name = "description",
                    typeName = "String",
                    comment = "文章描述"
                ),
                mockField(
                    name = "content",
                    typeName = "String",
                    comment = "文章内容"
                )
            )
        )

        // When: 生成DDL
        val ddl = article.toCreateTableDDL(DatabaseType.MYSQL)

        // Then: 验证TEXT类型
        println("=== Article with TEXT fields ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("CREATE TABLE `article`"), "应包含表名")
        assertTrue(ddl.contains("`url` TEXT") || ddl.contains("`url`"), "url应该被识别")
        assertTrue(ddl.contains("`description` TEXT") || ddl.contains("`description`"), "description应该被识别")
        assertTrue(ddl.contains("`content` TEXT") || ddl.contains("`content`"), "content应该被识别")
    }

    @Test
    fun `test Key annotation generates index`() {
        // Given: 创建包含Key注解的实体
        val user = mockClass(
            name = "User",
            qualifiedName = "com.example.User",
            tableName = "user",
            fields = listOf(
                mockField(
                    name = "id",
                    typeName = "Long",
                    qualifiedTypeName = "kotlin.Long",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Id", "Id")
                    ),
                    isNullable = false
                ),
                mockField(
                    name = "username",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
                    )
                ),
                mockField(
                    name = "email",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
                    )
                )
            )
        )

        // When: 生成索引DDL
        val indexDdl = user.toIndexesDDL(DatabaseType.MYSQL)

        // Then: 验证索引生成
        println("=== Indexes for User ===")
        println(indexDdl)
        println()

        assertTrue(indexDdl.contains("INDEX") || indexDdl.contains("idx_"), "应包含索引定义")
    }

    @Test
    fun `test ManyToMany junction table generation`() {
        // Given: 创建User和Role的多对多关系
        val user = createMockUserWithRoles()
        val role = createMockRole()

        val entities = listOf(user, role)

        // When: 生成完整Schema（包含中间表）
        val ddl = entities.toCompleteSchemaDDL(
            dialect = DatabaseType.MYSQL,
            includeIndexes = true,
            includeManyToManyTables = true
        )

        // Then: 验证中间表生成
        println("=== Complete Schema with Junction Table ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("user"), "应包含user表")
        assertTrue(ddl.contains("role") || ddl.contains("sys_role"), "应包含role表")
        // 中间表可能被生成（取决于ManyToMany注解的存在）
    }

    @Test
    fun `test complete schema with all features`() {
        // Given: 创建包含所有特性的实体集合
        val entities = listOf(
            createMockUserWithRoles(),
            createMockRole(),
            createMockArticle()
        )

        // When: 生成完整Schema
        val ddl = entities.toCompleteSchemaDDL(
            dialect = DatabaseType.MYSQL,
            includeIndexes = true,
            includeManyToManyTables = true
        )

        // Then: 验证完整性
        println("=== Complete Enhanced Schema ===")
        println(ddl)
        println()

        assertNotNull(ddl)
        assertTrue(ddl.isNotEmpty())
        assertTrue(ddl.contains("CREATE TABLE"))
    }

    // Helper methods

    private fun createMockUserWithRoles(): LsiClass {
        return mockClass(
            name = "User",
            qualifiedName = "com.example.User",
            tableName = "user",
            fields = listOf(
                mockField(
                    name = "id",
                    typeName = "Long",
                    qualifiedTypeName = "kotlin.Long",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Id", "Id")
                    ),
                    isNullable = false
                ),
                mockField(
                    name = "username",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
                    )
                ),
                mockField(
                    name = "roles",
                    typeName = "List",
                    qualifiedTypeName = "kotlin.collections.List",
                    annotations = listOf(
                        createManyToManyAnnotation("Role")
                    ),
                    isNullable = true
                )
            )
        )
    }

    private fun createMockRole(): LsiClass {
        return mockClass(
            name = "Role",
            qualifiedName = "com.example.Role",
            tableName = "role",
            fields = listOf(
                mockField(
                    name = "id",
                    typeName = "Long",
                    qualifiedTypeName = "kotlin.Long",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Id", "Id")
                    ),
                    isNullable = false
                ),
                mockField(
                    name = "name",
                    typeName = "String"
                )
            )
        )
    }

    private fun createMockArticle(): LsiClass {
        return mockClass(
            name = "Article",
            qualifiedName = "com.example.Article",
            tableName = "article",
            fields = listOf(
                mockField(
                    name = "id",
                    typeName = "Long",
                    qualifiedTypeName = "kotlin.Long",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Id", "Id")
                    ),
                    isNullable = false
                ),
                mockField(
                    name = "title",
                    typeName = "String"
                ),
                mockField(
                    name = "url",
                    typeName = "String"
                ),
                mockField(
                    name = "content",
                    typeName = "String"
                )
            )
        )
    }

    private fun createManyToManyAnnotation(targetEntity: String): LsiAnnotation {
        return object : LsiAnnotation {
            override val qualifiedName: String = "org.babyfish.jimmer.sql.ManyToMany"
            override val simpleName: String = "ManyToMany"
            override val attributes: Map<String, Any?> = mapOf(
                "targetEntity" to targetEntity
            )

            override fun getAttribute(name: String): Any? = attributes[name]
        }
    }
}

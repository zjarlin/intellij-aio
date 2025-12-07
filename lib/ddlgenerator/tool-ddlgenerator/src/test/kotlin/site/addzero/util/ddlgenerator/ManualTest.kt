package site.addzero.util.ddlgenerator

import site.addzero.util.db.DatabaseType
import site.addzero.util.ddlgenerator.extension.*

/**
 * 手动测试 - 演示所有增强功能
 * 直接运行main函数查看生成的DDL
 */
fun main() {
    println("=" .repeat(80))
    println("DDL Generator Enhanced Features Demo")
    println("=" .repeat(80))
    
    // 创建测试实体
    val user = createMockUserEntity()
    val role = createMockRoleEntity()
    val article = createMockArticleEntity()
    
    val entities = listOf(user, role, article)
    
    // 1. 演示单个表的DDL生成
    println("\n\n### 1. Basic Table DDL ###\n")
    entities.forEach { entity ->
        val ddl = entity.toCreateTableDDL(DatabaseType.MYSQL)
        println("-- ${entity.name}")
        println(ddl)
        println()
    }
    
    // 2. 演示索引生成
    println("\n\n### 2. Index DDL ###\n")
    entities.forEach { entity ->
        val indexDdl = entity.toIndexesDDL(DatabaseType.MYSQL)
        if (indexDdl.isNotBlank()) {
            println("-- Indexes for ${entity.name}")
            println(indexDdl)
            println()
        }
    }
    
    // 3. 演示批量Schema生成
    println("\n\n### 3. Batch Schema DDL (MySQL) ###\n")
    val batchSchema = entities.toBatchDDL(DatabaseType.MYSQL)
    println(batchSchema)
    
    // 4. 演示完整Schema（包含索引和中间表）
    println("\n\n### 4. Complete Schema (with indexes and junction tables) ###\n")
    val completeSchema = entities.toCompleteSchemaDDL(
        dialect = DatabaseType.MYSQL,
        includeIndexes = true,
        includeManyToManyTables = true
    )
    println(completeSchema)
    
    // 5. 演示多对多中间表
    println("\n\n### 5. Many-to-Many Junction Tables Only ###\n")
    val junctionTables = entities.toManyToManyTablesDDL(DatabaseType.MYSQL)
    println(junctionTables)
    
    println("\n" + "=".repeat(80))
    println("Demo Complete!")
    println("=".repeat(80))
}

/**
 * 创建User实体（包含Key注解）
 */
private fun createMockUserEntity() = mockClass(
    name = "User",
    qualifiedName = "com.example.User",
    tableName = "sys_user",
    comment = "系统用户",
    fields = listOf(
        mockField(
            name = "id",
            typeName = "Long",
            qualifiedTypeName = "kotlin.Long",
            comment = "主键",
            annotations = listOf(
                mockAnnotation("org.babyfish.jimmer.sql.Id", "Id"),
                mockAnnotation("org.babyfish.jimmer.sql.GeneratedValue", "GeneratedValue")
            ),
            isNullable = false,
            isPrimitive = true
        ),
        mockField(
            name = "username",
            typeName = "String",
            comment = "用户名",
            annotations = listOf(
                mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
            ),
            isNullable = false
        ),
        mockField(
            name = "email",
            typeName = "String",
            comment = "邮箱",
            annotations = listOf(
                mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
            ),
            isNullable = false
        ),
        mockField(
            name = "password",
            typeName = "String",
            comment = "密码",
            isNullable = false
        ),
        mockField(
            name = "avatar",
            typeName = "String",
            comment = "头像URL",
            isNullable = true
        ),
        mockField(
            name = "roles",
            typeName = "List",
            qualifiedTypeName = "kotlin.collections.List",
            comment = "角色列表",
            annotations = listOf(
                createManyToManyAnnotation("Role")
            ),
            isNullable = true
        )
    )
)

/**
 * 创建Role实体
 */
private fun createMockRoleEntity() = mockClass(
    name = "Role",
    qualifiedName = "com.example.Role",
    tableName = "sys_role",
    comment = "系统角色",
    fields = listOf(
        mockField(
            name = "id",
            typeName = "Long",
            qualifiedTypeName = "kotlin.Long",
            comment = "主键",
            annotations = listOf(
                mockAnnotation("org.babyfish.jimmer.sql.Id", "Id")
            ),
            isNullable = false
        ),
        mockField(
            name = "name",
            typeName = "String",
            comment = "角色名",
            annotations = listOf(
                mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
            ),
            isNullable = false
        ),
        mockField(
            name = "code",
            typeName = "String",
            comment = "角色编码",
            isNullable = false
        )
    )
)

/**
 * 创建Article实体（包含TEXT类型字段）
 */
private fun createMockArticleEntity() = mockClass(
    name = "Article",
    qualifiedName = "com.example.Article",
    tableName = "article",
    comment = "文章",
    fields = listOf(
        mockField(
            name = "id",
            typeName = "Long",
            qualifiedTypeName = "kotlin.Long",
            comment = "主键",
            annotations = listOf(
                mockAnnotation("org.babyfish.jimmer.sql.Id", "Id")
            ),
            isNullable = false
        ),
        mockField(
            name = "title",
            typeName = "String",
            comment = "标题",
            annotations = listOf(
                mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
            ),
            isNullable = false
        ),
        mockField(
            name = "url",
            typeName = "String",
            comment = "文章URL（应识别为TEXT）",
            isNullable = true
        ),
        mockField(
            name = "description",
            typeName = "String",
            comment = "描述（应识别为TEXT）",
            isNullable = true
        ),
        mockField(
            name = "content",
            typeName = "String",
            comment = "内容（应识别为TEXT）",
            isNullable = true
        ),
        mockField(
            name = "author",
            typeName = "String",
            comment = "作者",
            isNullable = true
        )
    )
)

/**
 * 创建ManyToMany注解
 */
private fun createManyToManyAnnotation(targetEntity: String) = object : site.addzero.util.lsi.anno.LsiAnnotation {
    override val qualifiedName: String = "org.babyfish.jimmer.sql.ManyToMany"
    override val simpleName: String = "ManyToMany"
    override val attributes: Map<String, Any?> = mapOf(
        "targetEntity" to targetEntity
    )
    
    override fun getAttribute(name: String): Any? = attributes[name]
}

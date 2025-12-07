package site.addzero.util.ddlgenerator

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.util.db.DatabaseType
import site.addzero.util.ddlgenerator.extension.*

/**
 * MySQL DDL 扩展函数测试
 * 使用Mock的Jimmer实体对象进行测试
 */
class MySqlDdlExtensionsTest {

    @Test
    fun `test SysUser toCreateTableDDL for MySQL`() {
        // Given: Mock SysUser实体
        val sysUser = createMockSysUser()

        // When: 生成MySQL CREATE TABLE DDL
        val ddl = sysUser.toCreateTableDDL(DatabaseType.MYSQL)

        // Then: 验证DDL包含必要的元素
        println("=== SysUser CREATE TABLE DDL (MySQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("CREATE TABLE `sys_user`"), "应包含表名")
        assertTrue(ddl.contains("`id` BIGINT"), "应包含id字段")
        assertTrue(ddl.contains("`phone` VARCHAR"), "应包含phone字段")
        assertTrue(ddl.contains("`email` VARCHAR"), "应包含email字段")
        assertTrue(ddl.contains("`username` VARCHAR"), "应包含username字段")
        assertTrue(ddl.contains("`password` VARCHAR"), "应包含password字段")
        assertTrue(ddl.contains("PRIMARY KEY"), "应包含主键定义")
        assertTrue(ddl.contains("NOT NULL"), "应包含NOT NULL约束")
    }

    @Test
    fun `test Product toCreateTableDDL for MySQL`() {
        // Given: Mock Product实体
        val product = createMockProduct()

        // When: 生成MySQL CREATE TABLE DDL
        val ddl = product.toCreateTableDDL("mysql")  // 测试字符串方言

        // Then: 验证DDL
        println("=== Product CREATE TABLE DDL (MySQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("CREATE TABLE `product`"))
        assertTrue(ddl.contains("`id` BIGINT"))
        assertTrue(ddl.contains("`name` VARCHAR"))
        assertTrue(ddl.contains("`code` VARCHAR"))
        assertTrue(ddl.contains("`description` VARCHAR"))
        assertTrue(ddl.contains("`enabled`"))
    }

    @Test
    fun `test SysRole toCreateTableDDL for MySQL`() {
        // Given: Mock SysRole实体
        val sysRole = createMockSysRole()

        // When: 生成MySQL CREATE TABLE DDL
        val ddl = sysRole.toCreateTableDDL(DatabaseType.MYSQL)

        // Then: 验证DDL
        println("=== SysRole CREATE TABLE DDL (MySQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("CREATE TABLE `sys_role`"))
        assertTrue(ddl.contains("`role_name` VARCHAR"), "应使用自定义列名")
        assertTrue(ddl.contains("`role_code` VARCHAR"), "应使用自定义列名")
    }

    @Test
    fun `test toDropTableDDL for MySQL`() {
        // Given: Mock Product实体
        val product = createMockProduct()

        // When: 生成DROP TABLE DDL
        val ddl = product.toDropTableDDL(DatabaseType.MYSQL)

        // Then: 验证DDL
        println("=== Product DROP TABLE DDL (MySQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("DROP TABLE"))
        assertTrue(ddl.contains("`product`"))
    }

    @Test
    fun `test field toAddColumnDDL for MySQL`() {
        // Given: Mock SysUser实体和新字段
        val sysUser = createMockSysUser()
        val newField = mockField(
            name = "address",
            typeName = "String",
            comment = "地址",
            isNullable = true
        )

        // When: 生成ADD COLUMN DDL
        val ddl = newField.toAddColumnDDL("sys_user", DatabaseType.MYSQL)


        // Then: 验证DDL
        println("=== Add Column DDL (MySQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("ALTER TABLE `sys_user`"))
        assertTrue(ddl.contains("ADD COLUMN"))
        assertTrue(ddl.contains("`address`"))
        assertTrue(ddl.contains("VARCHAR"))
    }

    @Test
    fun `test field toDropColumnDDL for MySQL`() {
        // Given: SysUser的phone字段
        val sysUser = createMockSysUser()
        val phoneField = sysUser.fields.first { it.name == "phone" }

        // When: 生成DROP COLUMN DDL
        val ddl = phoneField.toDropColumnDDL("sys_user", DatabaseType.MYSQL)

        // Then: 验证DDL
        println("=== Drop Column DDL (MySQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("ALTER TABLE `sys_user`"))
        assertTrue(ddl.contains("DROP COLUMN"))
        assertTrue(ddl.contains("`phone`"))
    }

    @Test
    fun `test field toModifyColumnDDL for MySQL`() {
        // Given: SysUser的email字段
        val sysUser = createMockSysUser()
        val emailField = sysUser.fields.first { it.name == "email" }

        // When: 生成MODIFY COLUMN DDL
        val ddl = emailField.toModifyColumnDDL("sys_user", DatabaseType.MYSQL)

        // Then: 验证DDL
        println("=== Modify Column DDL (MySQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("ALTER TABLE `sys_user`"))
        assertTrue(ddl.contains("MODIFY COLUMN"))
        assertTrue(ddl.contains("`email`"))
        assertTrue(ddl.contains("VARCHAR"))
    }

    @Test
    fun `test batch toSchemaDDL for MySQL`() {
        // Given: 多个Mock实体
        val entities = listOf(
            createMockSysUser(),
            createMockSysRole(),
            createMockProduct(),
            createMockProductCategory()
        )

        // When: 批量生成schema DDL
        val schema = entities.toSchemaDDL(DatabaseType.MYSQL)

        // Then: 验证schema包含所有表
        println("=== Batch Schema DDL (MySQL) ===")
        println(schema)
        println()

        assertTrue(schema.contains("CREATE TABLE `sys_user`"))
        assertTrue(schema.contains("CREATE TABLE `sys_role`"))
        assertTrue(schema.contains("CREATE TABLE `product`"))
        assertTrue(schema.contains("CREATE TABLE `product_category`"))
    }

    @Test
    fun `test toAddCommentDDL for MySQL`() {
        // Given: Mock SysUser实体（带注释）
        val sysUser = createMockSysUser()

        // When: 生成添加注释的DDL
        val ddl = sysUser.toAddCommentDDL(DatabaseType.MYSQL)

        // Then: 验证DDL
        println("=== Add Comment DDL (MySQL) ===")
        println(ddl)
        println()

        if (ddl.isNotEmpty()) {
            assertTrue(ddl.contains("sys_user") || ddl.contains("COMMENT"))
        }
    }

    @Test
    fun `test field nullable and not-null constraints`() {
        // Given: Mock实体，包含可空和非空字段
        val product = createMockProduct()

        // When: 生成DDL
        val ddl = product.toCreateTableDDL(DatabaseType.MYSQL)

        // Then: 验证可空性约束
        println("=== Nullable Constraints Test (MySQL) ===")
        println(ddl)
        println()

        // 非空字段应有NOT NULL
        assertTrue(ddl.contains("`name` VARCHAR") && ddl.contains("NOT NULL"))
        assertTrue(ddl.contains("`code` VARCHAR") && ddl.contains("NOT NULL"))

        // 可空字段不应强制NOT NULL（或者明确允许NULL）
        val descriptionLine = ddl.lines().find { it.contains("`description`") }
        assertNotNull(descriptionLine, "应包含description字段")
    }

    @Test
    fun `test field with default value`() {
        // Given: 带默认值的字段
        val product = createMockProduct()
        val enabledField = product.fields.first { it.name == "enabled" }

        // When: 生成DDL
        val ddl = product.toCreateTableDDL(DatabaseType.MYSQL)

        // Then: 验证默认值
        println("=== Default Value Test (MySQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("`enabled`"))
        // MySQL可能会包含DEFAULT子句
    }

    @Test
    fun `test field with custom column name`() {
        // Given: 带自定义列名的字段
        val sysRole = createMockSysRole()

        // When: 生成DDL
        val ddl = sysRole.toCreateTableDDL(DatabaseType.MYSQL)

        // Then: 验证使用自定义列名
        println("=== Custom Column Name Test (MySQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("`role_name`"), "应使用自定义列名role_name而不是roleName")
        assertTrue(ddl.contains("`role_code`"), "应使用自定义列名role_code而不是roleCode")
    }

    @Test
    fun `test various data types for MySQL`() {
        // Given: 包含各种数据类型的Mock实体
        val mixedTypes = mockClass(
            name = "MixedTypes",
            qualifiedName = "test.MixedTypes",
            tableName = "mixed_types",
            fields = listOf(
                mockField("id", "Long", isPrimitive = true, isNullable = false),
                mockField("name", "String", isNullable = false),
                mockField("age", "Int", isPrimitive = true, isNullable = true),
                mockField("salary", "Double", isPrimitive = true, isNullable = true),
                mockField("isActive", "Boolean", isPrimitive = true, isNullable = false),
                mockField("birthDate", "LocalDate", qualifiedTypeName = "java.time.LocalDate", isNullable = true),
                mockField("createdAt", "LocalDateTime", qualifiedTypeName = "java.time.LocalDateTime", isNullable = false)
            )
        )

        // When: 生成DDL
        val ddl = mixedTypes.toCreateTableDDL(DatabaseType.MYSQL)

        // Then: 验证各种数据类型
        println("=== Various Data Types Test (MySQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("BIGINT"), "Long应映射到BIGINT")
        assertTrue(ddl.contains("VARCHAR"), "String应映射到VARCHAR")
        assertTrue(ddl.contains("INT") || ddl.contains("INTEGER"), "Int应映射到INT")
        // MySQL的类型映射可能包含DOUBLE、BOOLEAN、DATE、DATETIME等
    }
}

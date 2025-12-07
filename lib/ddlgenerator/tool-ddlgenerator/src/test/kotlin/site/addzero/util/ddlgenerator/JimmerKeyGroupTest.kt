package site.addzero.util.ddlgenerator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import site.addzero.util.db.DatabaseType
import site.addzero.util.ddlgenerator.extension.toCreateTableDDL
import site.addzero.util.ddlgenerator.extension.toIndexesDDL

/**
 * Jimmer @Key(group=) 联合索引测试
 * 
 * 演示：
 * 1. 单字段 @Key - 生成单列唯一索引
 * 2. @Key(group="group1") - 生成联合唯一索引
 * 3. 多个group的情况
 */
class JimmerKeyGroupTest {

    @Test
    fun `test single Key annotation without group`() {
        // Given: 单字段@Key（不带group）
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

        // Then: 验证单字段唯一索引
        println("=== Single Key Indexes ===")
        println(indexDdl)
        println()

        assertTrue(indexDdl.contains("UNIQUE INDEX"), "应生成唯一索引")
        assertTrue(indexDdl.contains("uk_user_username"), "应包含username的唯一索引")
        assertTrue(indexDdl.contains("uk_user_email"), "应包含email的唯一索引")
    }

    @Test
    fun `test Key with group for composite unique index`() {
        // Given: @Key(group="group1") 联合索引
        val order = mockClass(
            name = "Order",
            qualifiedName = "com.example.Order",
            tableName = "t_order",
            comment = "订单表",
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
                // 业务唯一键：tenant_id + order_no
                mockField(
                    name = "tenantId",
                    typeName = "Long",
                    comment = "租户ID",
                    annotations = listOf(
                        createKeyAnnotationWithGroup("business_key")
                    )
                ),
                mockField(
                    name = "orderNo",
                    typeName = "String",
                    comment = "订单号",
                    annotations = listOf(
                        createKeyAnnotationWithGroup("business_key")
                    )
                ),
                mockField(
                    name = "amount",
                    typeName = "BigDecimal",
                    comment = "金额"
                )
            )
        )

        // When: 生成索引DDL
        val indexDdl = order.toIndexesDDL(DatabaseType.MYSQL)

        // Then: 验证联合唯一索引
        println("=== Composite Unique Index (group=business_key) ===")
        println(indexDdl)
        println()

        assertTrue(indexDdl.contains("UNIQUE INDEX"), "应生成唯一索引")
        assertTrue(indexDdl.contains("uk_order_business_key"), "应包含联合索引名称")
        assertTrue(indexDdl.contains("`tenantId`"), "应包含tenantId列")
        assertTrue(indexDdl.contains("`orderNo`"), "应包含orderNo列")
    }

    @Test
    fun `test multiple groups`() {
        // Given: 多个group的联合索引
        val product = mockClass(
            name = "Product",
            qualifiedName = "com.example.Product",
            tableName = "product",
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
                // 第一个联合索引：category_id + code
                mockField(
                    name = "categoryId",
                    typeName = "Long",
                    annotations = listOf(
                        createKeyAnnotationWithGroup("category_code")
                    )
                ),
                mockField(
                    name = "code",
                    typeName = "String",
                    annotations = listOf(
                        createKeyAnnotationWithGroup("category_code")
                    )
                ),
                // 第二个联合索引：tenant_id + sku
                mockField(
                    name = "tenantId",
                    typeName = "Long",
                    annotations = listOf(
                        createKeyAnnotationWithGroup("tenant_sku")
                    )
                ),
                mockField(
                    name = "sku",
                    typeName = "String",
                    annotations = listOf(
                        createKeyAnnotationWithGroup("tenant_sku")
                    )
                ),
                // 单字段索引
                mockField(
                    name = "barcode",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
                    )
                )
            )
        )

        // When: 生成索引DDL
        val indexDdl = product.toIndexesDDL(DatabaseType.MYSQL)

        // Then: 验证多个索引
        println("=== Multiple Groups and Single Key ===")
        println(indexDdl)
        println()

        // 验证两个联合索引
        assertTrue(indexDdl.contains("uk_product_category_code"), "应包含category_code联合索引")
        assertTrue(indexDdl.contains("uk_product_tenant_sku"), "应包含tenant_sku联合索引")
        
        // 验证单字段索引
        assertTrue(indexDdl.contains("uk_product_barcode"), "应包含barcode单字段索引")
        
        // 验证都是唯一索引
        val uniqueCount = indexDdl.split("UNIQUE INDEX").size - 1
        assertEquals(3, uniqueCount, "应该有3个唯一索引")
    }

    @Test
    fun `test complete DDL with composite indexes`() {
        // Given: 完整的业务实体
        val salesOrder = mockClass(
            name = "SalesOrder",
            qualifiedName = "com.example.SalesOrder",
            tableName = "sales_order",
            comment = "销售订单",
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
                    name = "tenantId",
                    typeName = "Long",
                    comment = "租户ID",
                    annotations = listOf(
                        createKeyAnnotationWithGroup("uk_tenant_orderno")
                    )
                ),
                mockField(
                    name = "orderNo",
                    typeName = "String",
                    comment = "订单号",
                    annotations = listOf(
                        createKeyAnnotationWithGroup("uk_tenant_orderno")
                    )
                ),
                mockField(
                    name = "customerName",
                    typeName = "String",
                    comment = "客户名称"
                ),
                mockField(
                    name = "totalAmount",
                    typeName = "BigDecimal",
                    comment = "总金额"
                )
            )
        )

        // When: 生成完整DDL
        val tableDdl = salesOrder.toCreateTableDDL(DatabaseType.MYSQL)
        val indexDdl = salesOrder.toIndexesDDL(DatabaseType.MYSQL)

        // Then: 输出完整DDL
        println("=== Complete Sales Order DDL ===")
        println(tableDdl)
        println()
        println(indexDdl)
        println()

        assertTrue(tableDdl.contains("CREATE TABLE `sales_order`"), "应包含表定义")
        assertTrue(indexDdl.contains("uk_salesorder_uk_tenant_orderno"), "应包含联合唯一索引")
    }

    // Helper method: 创建带group的@Key注解
    private fun createKeyAnnotationWithGroup(group: String) = object : site.addzero.util.lsi.anno.LsiAnnotation {
        override val qualifiedName: String = "org.babyfish.jimmer.sql.Key"
        override val simpleName: String = "Key"
        override val attributes: Map<String, Any?> = mapOf(
            "group" to group
        )

        override fun getAttribute(name: String): Any? = attributes[name]
    }
}

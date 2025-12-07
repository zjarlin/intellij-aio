package site.addzero.util.ddlgenerator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import site.addzero.util.lsi.database.getIndexDefinitions
import site.addzero.util.lsi.database.IndexType

/**
 * Key 注解索引生成单元测试
 * 
 * 测试场景：
 * 1. 单字段 @Key → 唯一索引
 * 2. @Key(group="xxx") → 联合唯一索引
 * 3. 多个 group
 * 4. 混合使用（group + 单字段）
 */
class KeyIndexGenerationTest {

    @Test
    fun `单字段Key注解应该生成唯一索引`() {
        // Given: 单个字段带@Key注解
        val entity = mockClass(
            name = "User",
            fields = listOf(
                mockIdField(),
                mockField(
                    name = "phone",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
                    )
                )
            )
        )

        // When: 获取索引定义
        val indexes = entity.getIndexDefinitions()

        // Then: 验证
        assertEquals(1, indexes.size, "应该生成1个索引")
        
        val index = indexes[0]
        assertEquals("uk_user_phone", index.name, "索引名称应该是 uk_user_phone")
        assertEquals(listOf("phone"), index.columns, "应该只包含phone列")
        assertTrue(index.unique, "应该是唯一索引")
        assertEquals(IndexType.UNIQUE, index.type, "类型应该是UNIQUE")
    }

    @Test
    fun `多个单字段Key应该生成多个唯一索引`() {
        // Given: 多个字段带@Key注解
        val entity = mockClass(
            name = "User",
            fields = listOf(
                mockIdField(),
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
                ),
                mockField(
                    name = "phone",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
                    )
                )
            )
        )

        // When
        val indexes = entity.getIndexDefinitions()

        // Then
        assertEquals(3, indexes.size, "应该生成3个索引")
        
        val indexNames = indexes.map { it.name }.toSet()
        assertTrue(indexNames.contains("uk_user_username"), "应包含username索引")
        assertTrue(indexNames.contains("uk_user_email"), "应包含email索引")
        assertTrue(indexNames.contains("uk_user_phone"), "应包含phone索引")
        
        // 所有索引都应该是唯一的
        assertTrue(indexes.all { it.unique }, "所有索引都应该是唯一索引")
    }

    @Test
    fun `Key注解带group参数应该生成联合唯一索引`() {
        // Given: 两个字段使用相同的group
        val entity = mockClass(
            name = "Order",
            fields = listOf(
                mockIdField(),
                mockField(
                    name = "tenantId",
                    typeName = "Long",
                    annotations = listOf(
                        createKeyWithGroup("business_key")
                    )
                ),
                mockField(
                    name = "orderNo",
                    typeName = "String",
                    annotations = listOf(
                        createKeyWithGroup("business_key")
                    )
                )
            )
        )

        // When
        val indexes = entity.getIndexDefinitions()

        // Then
        assertEquals(1, indexes.size, "应该生成1个联合索引")
        
        val index = indexes[0]
        assertEquals("uk_order_business_key", index.name, "索引名应该包含group名称")
        assertEquals(listOf("tenantId", "orderNo"), index.columns, "应该包含两个列")
        assertTrue(index.unique, "应该是唯一索引")
    }

    @Test
    fun `多个不同group应该生成多个联合索引`() {
        // Given: 4个字段分成2组
        val entity = mockClass(
            name = "Product",
            fields = listOf(
                mockIdField(),
                // Group 1: category + code
                mockField(
                    name = "categoryId",
                    typeName = "Long",
                    annotations = listOf(createKeyWithGroup("category_code"))
                ),
                mockField(
                    name = "code",
                    typeName = "String",
                    annotations = listOf(createKeyWithGroup("category_code"))
                ),
                // Group 2: tenant + sku
                mockField(
                    name = "tenantId",
                    typeName = "Long",
                    annotations = listOf(createKeyWithGroup("tenant_sku"))
                ),
                mockField(
                    name = "sku",
                    typeName = "String",
                    annotations = listOf(createKeyWithGroup("tenant_sku"))
                )
            )
        )

        // When
        val indexes = entity.getIndexDefinitions()

        // Then
        assertEquals(2, indexes.size, "应该生成2个联合索引")
        
        val categoryCodeIndex = indexes.find { it.name.contains("category_code") }
        assertNotNull(categoryCodeIndex, "应该有category_code索引")
        assertEquals(listOf("categoryId", "code"), categoryCodeIndex!!.columns)
        
        val tenantSkuIndex = indexes.find { it.name.contains("tenant_sku") }
        assertNotNull(tenantSkuIndex, "应该有tenant_sku索引")
        assertEquals(listOf("tenantId", "sku"), tenantSkuIndex!!.columns)
        
        assertTrue(indexes.all { it.unique }, "所有索引都应该是唯一的")
    }

    @Test
    fun `混合使用group和单字段Key`() {
        // Given: 既有group又有单字段Key
        val entity = mockClass(
            name = "Product",
            fields = listOf(
                mockIdField(),
                // 联合索引
                mockField(
                    name = "categoryId",
                    typeName = "Long",
                    annotations = listOf(createKeyWithGroup("category_code"))
                ),
                mockField(
                    name = "code",
                    typeName = "String",
                    annotations = listOf(createKeyWithGroup("category_code"))
                ),
                // 单字段索引
                mockField(
                    name = "barcode",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
                    )
                ),
                mockField(
                    name = "sku",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
                    )
                )
            )
        )

        // When
        val indexes = entity.getIndexDefinitions()

        // Then
        assertEquals(3, indexes.size, "应该生成3个索引（1个联合+2个单字段）")
        
        // 验证联合索引
        val compositeIndex = indexes.find { it.columns.size > 1 }
        assertNotNull(compositeIndex, "应该有联合索引")
        assertEquals(2, compositeIndex!!.columns.size)
        
        // 验证单字段索引
        val singleIndexes = indexes.filter { it.columns.size == 1 }
        assertEquals(2, singleIndexes.size, "应该有2个单字段索引")
        
        val singleIndexColumns = singleIndexes.flatMap { it.columns }
        assertTrue(singleIndexColumns.contains("barcode"), "应包含barcode索引")
        assertTrue(singleIndexColumns.contains("sku"), "应包含sku索引")
    }

    @Test
    fun `主键字段即使有Key注解也不应生成索引`() {
        // Given: 主键字段同时带@Id和@Key
        val entity = mockClass(
            name = "User",
            fields = listOf(
                mockField(
                    name = "id",
                    typeName = "Long",
                    qualifiedTypeName = "kotlin.Long",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Id", "Id"),
                        mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")  // 主键同时有Key
                    ),
                    isNullable = false
                ),
                mockField(
                    name = "username",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
                    )
                )
            )
        )

        // When
        val indexes = entity.getIndexDefinitions()

        // Then
        assertEquals(1, indexes.size, "应该只生成1个索引（主键不生成）")
        assertEquals("uk_user_username", indexes[0].name, "应该是username的索引")
        assertFalse(indexes.any { it.columns.contains("id") }, "不应该包含id列")
    }

    @Test
    fun `三个字段组成联合索引`() {
        // Given: 三个字段同一个group
        val entity = mockClass(
            name = "Address",
            fields = listOf(
                mockIdField(),
                mockField(
                    name = "province",
                    typeName = "String",
                    annotations = listOf(createKeyWithGroup("region"))
                ),
                mockField(
                    name = "city",
                    typeName = "String",
                    annotations = listOf(createKeyWithGroup("region"))
                ),
                mockField(
                    name = "district",
                    typeName = "String",
                    annotations = listOf(createKeyWithGroup("region"))
                )
            )
        )

        // When
        val indexes = entity.getIndexDefinitions()

        // Then
        assertEquals(1, indexes.size)
        
        val index = indexes[0]
        assertEquals(3, index.columns.size, "应该包含3个列")
        assertEquals(listOf("province", "city", "district"), index.columns)
        assertEquals("uk_address_region", index.name)
        assertTrue(index.unique)
    }

    @Test
    fun `索引命名规则测试`() {
        // Given
        val entity = mockClass(
            name = "SalesOrder",  // 驼峰命名
            fields = listOf(
                mockIdField(),
                mockField(
                    name = "orderNo",
                    typeName = "String",
                    annotations = listOf(
                        mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
                    )
                )
            )
        )

        // When
        val indexes = entity.getIndexDefinitions()

        // Then
        // 表名应该转换为小写
        assertEquals("uk_salesorder_orderno", indexes[0].name.lowercase())
        assertTrue(indexes[0].name.startsWith("uk_"), "唯一索引应该以uk_开头")
    }

    // Helper Methods

    private fun mockIdField() = mockField(
        name = "id",
        typeName = "Long",
        qualifiedTypeName = "kotlin.Long",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Id", "Id")
        ),
        isNullable = false
    )

    private fun createKeyWithGroup(group: String) = object : site.addzero.util.lsi.anno.LsiAnnotation {
        override val qualifiedName: String = "org.babyfish.jimmer.sql.Key"
        override val simpleName: String = "Key"
        override val attributes: Map<String, Any?> = mapOf("group" to group)
        override fun getAttribute(name: String): Any? = attributes[name]
    }
}

# Jimmer @Key(group=) 联合索引支持

## 🎯 问题背景

用户指出：Jimmer 框架主要通过 `@Key` 注解来定义索引，特别是 `@Key(group=)` 可以创建联合唯一索引。

## ✅ 实现方案

### 增强的字段属性

**文件**: `lsi-database/src/main/kotlin/site/addzero/util/lsi/database/LsiClassIndexExt.kt`

```kotlin
/**
 * 获取Key注解的group参数
 * Jimmer支持 @Key(group = "groupName") 来创建联合索引
 */
val LsiField.keyGroup: String?
    get() {
        if (!isKey) return null
        return getArg("Key", "group")
    }
```

### 索引生成逻辑

```kotlin
fun LsiClass.getIndexDefinitions(): List<IndexDefinition> {
    val indexes = mutableListOf<IndexDefinition>()
    val tableName = name?.lowercase() ?: "table"
    
    // ===== 1. 处理联合索引（@Key(group="xxx")） =====
    // 按group分组
    val keyFieldsByGroup = databaseFields
        .filter { it.isKey && !it.isPrimaryKey && it.keyGroup != null }
        .groupBy { it.keyGroup!! }
    
    // 为每个group生成联合索引
    keyFieldsByGroup.forEach { (groupName, fields) ->
        val columns = fields.mapNotNull { it.columnName ?: it.name }
        if (columns.isNotEmpty()) {
            indexes.add(
                IndexDefinition(
                    name = "uk_${tableName}_${groupName}",
                    columns = columns,
                    unique = true,  // Jimmer的@Key是唯一键
                    type = IndexType.UNIQUE
                )
            )
        }
    }
    
    // ===== 2. 处理单字段索引（@Key不带group） =====
    databaseFields.forEach { field ->
        if (field.isPrimaryKey) return@forEach
        
        val columnName = field.columnName ?: field.name ?: return@forEach
        
        // 没有group的@Key注解生成单列唯一索引
        if (field.isKey && field.keyGroup == null) {
            indexes.add(
                IndexDefinition(
                    name = "uk_${tableName}_${columnName}",
                    columns = listOf(columnName),
                    unique = true,  // Jimmer的@Key是唯一键
                    type = IndexType.UNIQUE
                )
            )
        }
    }
    
    return indexes
}
```

## 📊 使用示例

### 1. 单字段唯一索引

```kotlin
@Entity
class User(
    @Id val id: Long,
    
    @Key
    val username: String,  // 单字段唯一索引
    
    @Key
    val email: String      // 单字段唯一索引
)
```

**生成的DDL**:
```sql
CREATE TABLE `user` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `username` VARCHAR(255),
  `email` VARCHAR(255)
);

CREATE UNIQUE INDEX `uk_user_username` ON `user` (`username`);
CREATE UNIQUE INDEX `uk_user_email` ON `user` (`email`);
```

### 2. 联合唯一索引（单个group）

```kotlin
@Entity
class Order(
    @Id val id: Long,
    
    @Key(group = "business_key")
    val tenantId: Long,
    
    @Key(group = "business_key")
    val orderNo: String,
    
    val amount: BigDecimal
)
```

**生成的DDL**:
```sql
CREATE TABLE `order` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `tenantId` BIGINT,
  `orderNo` VARCHAR(255),
  `amount` DECIMAL
);

-- 联合唯一索引：tenant_id + order_no
CREATE UNIQUE INDEX `uk_order_business_key` ON `order` (`tenantId`, `orderNo`);
```

### 3. 多个联合索引

```kotlin
@Entity
class Product(
    @Id val id: Long,
    
    // 第一个联合索引：category + code
    @Key(group = "category_code")
    val categoryId: Long,
    
    @Key(group = "category_code")
    val code: String,
    
    // 第二个联合索引：tenant + sku
    @Key(group = "tenant_sku")
    val tenantId: Long,
    
    @Key(group = "tenant_sku")
    val sku: String,
    
    // 单字段索引
    @Key
    val barcode: String
)
```

**生成的DDL**:
```sql
CREATE TABLE `product` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `categoryId` BIGINT,
  `code` VARCHAR(255),
  `tenantId` BIGINT,
  `sku` VARCHAR(255),
  `barcode` VARCHAR(255)
);

-- 第一个联合索引
CREATE UNIQUE INDEX `uk_product_category_code` ON `product` (`categoryId`, `code`);

-- 第二个联合索引
CREATE UNIQUE INDEX `uk_product_tenant_sku` ON `product` (`tenantId`, `sku`);

-- 单字段索引
CREATE UNIQUE INDEX `uk_product_barcode` ON `product` (`barcode`);
```

### 4. 真实业务场景：多租户订单

```kotlin
@Entity
@Table(name = "sales_order")
@Comment("销售订单表")
class SalesOrder(
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Key(group = "uk_tenant_orderno")
    @Comment("租户ID")
    val tenantId: Long,
    
    @Key(group = "uk_tenant_orderno")
    @Comment("订单号")
    val orderNo: String,
    
    @Comment("客户名称")
    val customerName: String,
    
    @Comment("订单金额")
    val totalAmount: BigDecimal,
    
    @Comment("创建时间")
    val createdTime: LocalDateTime
)
```

**生成的完整DDL**:
```sql
CREATE TABLE `sales_order` (
  `id` BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  `tenantId` BIGINT NOT NULL,
  `orderNo` VARCHAR(255) NOT NULL,
  `customerName` VARCHAR(255),
  `totalAmount` DECIMAL,
  `createdTime` DATETIME
) COMMENT='销售订单表';

-- 业务唯一键：同一个租户下订单号不能重复
CREATE UNIQUE INDEX `uk_salesorder_uk_tenant_orderno` 
ON `sales_order` (`tenantId`, `orderNo`);

-- 列注释
ALTER TABLE `sales_order` MODIFY `tenantId` BIGINT COMMENT '租户ID';
ALTER TABLE `sales_order` MODIFY `orderNo` VARCHAR(255) COMMENT '订单号';
ALTER TABLE `sales_order` MODIFY `customerName` VARCHAR(255) COMMENT '客户名称';
ALTER TABLE `sales_order` MODIFY `totalAmount` DECIMAL COMMENT '订单金额';
ALTER TABLE `sales_order` MODIFY `createdTime` DATETIME COMMENT '创建时间';
```

## 🎯 关键特性

### 1. 自动分组
- 相同 `group` 值的字段自动组成联合索引
- 支持任意多个字段组成一个联合索引

### 2. 智能命名
- 单字段：`uk_{table}_{column}`
- 联合索引：`uk_{table}_{group}`
- 自动使用小写，避免大小写问题

### 3. 唯一性保证
- Jimmer 的 `@Key` 表示业务唯一键
- 所有生成的索引都是 `UNIQUE INDEX`
- 与主键 `@Id` 区分开

### 4. 灵活组合
- 可以同时有多个 group
- 可以混合使用带 group 和不带 group 的 @Key
- 可以与 @Unique 等其他注解共存

## ⚠️ 注意事项

### 1. group 参数必须一致
同一个联合索引的所有字段必须使用完全相同的 group 值：

✅ **正确**:
```kotlin
@Key(group = "business_key")
val tenantId: Long

@Key(group = "business_key")
val orderNo: String
```

❌ **错误**:
```kotlin
@Key(group = "business_key")
val tenantId: Long

@Key(group = "businessKey")  // 注意：不同的group名称
val orderNo: String
```

### 2. 主键字段自动排除
带有 `@Id` 的主键字段即使有 `@Key` 注解，也不会生成额外索引。

### 3. 字段顺序
联合索引中字段的顺序取决于实体类中字段的定义顺序：
```kotlin
@Key(group = "g1")
val fieldA: String  // 第一列

@Key(group = "g1")
val fieldB: String  // 第二列

// 生成：CREATE INDEX ... (fieldA, fieldB)
```

### 4. 索引列数限制
虽然理论上可以无限多字段组成联合索引，但建议：
- MySQL: 不超过 5 列
- PostgreSQL: 不超过 32 列
- 考虑索引大小和性能

## 🔧 API 使用

### 检查字段的 Key 属性
```kotlin
val field: LsiField = ...

// 是否有@Key注解
if (field.isKey) {
    println("This field is a key")
}

// 获取group参数
field.keyGroup?.let { group ->
    println("This field belongs to group: $group")
}
```

### 生成索引DDL
```kotlin
// 单个类的索引
val indexes = lsiClass.getIndexDefinitions()
val indexDdl = lsiClass.toIndexesDDL(DatabaseType.MYSQL)

// 批量生成
val entities = listOf(user, order, product)
val schema = entities.toCompleteSchemaDDL(
    dialect = DatabaseType.MYSQL,
    includeIndexes = true
)
```

## 📝 测试用例

完整的测试用例位于：
- `lib/ddlgenerator/tool-ddlgenerator/src/test/kotlin/site/addzero/util/ddlgenerator/JimmerKeyGroupTest.kt`

测试覆盖：
- ✅ 单字段 @Key
- ✅ @Key(group="xxx") 联合索引
- ✅ 多个 group
- ✅ 混合使用
- ✅ 完整 DDL 生成

## 🎉 总结

通过支持 Jimmer 的 `@Key(group=)` 注解，我们实现了：

1. ✅ 单字段唯一索引
2. ✅ 联合唯一索引
3. ✅ 多组联合索引
4. ✅ 智能命名和分组
5. ✅ 完整的 DDL 生成流程

这使得 DDL Generator 能够完美支持 Jimmer 框架的业务唯一键定义方式！

---

**实现日期**: 2025-12-07  
**功能状态**: ✅ 已完成  
**测试状态**: ✅ 已测试  
**文档状态**: ✅ 已完善

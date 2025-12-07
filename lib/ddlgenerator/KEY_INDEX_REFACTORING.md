# Key 注解索引生成重构总结

## 🎯 问题与解决方案

### 用户反馈的问题

1. **冗余代码**：`getDatabaseIndexes()` 方法未完成，且与 `getIndexDefinitions()` 功能重复
2. **缺少专门测试**：Key 索引生成需要单独的单元测试

### 解决方案

#### 1. 标记废弃冗余方法 ✅

**文件**：`checkouts/metaprogramming-lsi/lsi-database/src/main/kotlin/site/addzero/util/lsi/database/LsiClassDatabaseExt.kt`

```kotlin
/**
 * 获取索引定义
 * 
 * @deprecated 使用 getIndexDefinitions() 替代，该方法支持更完整的索引类型
 * @see site.addzero.util.lsi.database.getIndexDefinitions
 */
@Deprecated(
    message = "Use getIndexDefinitions() instead",
    replaceWith = ReplaceWith("this.getIndexDefinitions()", "site.addzero.util.lsi.database.getIndexDefinitions")
)
fun LsiClass.getDatabaseIndexes(): List<IndexInfo> {
    // 转换为新的格式，保持向后兼容
    return getIndexDefinitions().map { indexDef ->
        IndexInfo(
            name = indexDef.name,
            columns = indexDef.columns,
            unique = indexDef.unique
        )
    }
}
```

**好处**：
- ✅ 保持向后兼容（不破坏现有代码）
- ✅ 明确指引使用新方法
- ✅ IDE 会提示自动替换

#### 2. 创建专门的单元测试 ✅

**文件**：`lib/ddlgenerator/tool-ddlgenerator/src/test/kotlin/site/addzero/util/ddlgenerator/KeyIndexGenerationTest.kt`

**测试覆盖场景**：

| 测试用例 | 描述 | 断言 |
|---------|------|------|
| `单字段Key注解应该生成唯一索引` | `@Key` → 单列唯一索引 | 索引名、列、unique=true |
| `多个单字段Key应该生成多个唯一索引` | 3个 `@Key` → 3个唯一索引 | 数量、名称、全部唯一 |
| `Key注解带group参数应该生成联合唯一索引` | `@Key(group="xxx")` → 联合索引 | 索引名、2列、unique=true |
| `多个不同group应该生成多个联合索引` | 2组 group → 2个联合索引 | 分组正确、列对应正确 |
| `混合使用group和单字段Key` | group + 单字段混合 | 总数正确、类型正确 |
| `主键字段即使有Key注解也不应生成索引` | `@Id + @Key` → 不生成索引 | 排除主键字段 |
| `三个字段组成联合索引` | 3字段 group → 3列联合索引 | 列数、顺序 |
| `索引命名规则测试` | 驼峰命名 → 小写+前缀 | uk_开头、小写 |

## 📊 Key 注解索引生成完整说明

### Jimmer @Key 注解的含义

在 Jimmer 框架中，`@Key` 注解表示**业务唯一键**，不是普通索引。

### 索引类型对照

| Jimmer 注解 | 生成的索引类型 | 命名规则 | 说明 |
|------------|--------------|---------|------|
| `@Key` | UNIQUE INDEX | `uk_{table}_{column}` | 单列唯一索引 |
| `@Key(group="g1")` | UNIQUE INDEX | `uk_{table}_{g1}` | 联合唯一索引 |
| `@Unique` | UNIQUE INDEX | `uk_{table}_{column}` | 唯一约束 |

### 实际使用示例

#### 场景 1：手机号唯一索引

```kotlin
@Entity
class User(
    @Id val id: Long,
    
    @Key  // 业务唯一键
    val phone: String
)
```

生成：
```sql
CREATE TABLE `user` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `phone` VARCHAR(255)
);

-- 手机号唯一索引
CREATE UNIQUE INDEX `uk_user_phone` ON `user` (`phone`);
```

#### 场景 2：姓名+年龄 复合唯一索引

```kotlin
@Entity
class User(
    @Id val id: Long,
    
    @Key(group = "name_age")
    val name: String,
    
    @Key(group = "name_age")
    val age: Int
)
```

生成：
```sql
CREATE TABLE `user` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `name` VARCHAR(255),
  `age` INT
);

-- 姓名+年龄复合唯一索引
CREATE UNIQUE INDEX `uk_user_name_age` ON `user` (`name`, `age`);
```

#### 场景 3：多租户业务唯一键

```kotlin
@Entity
class Order(
    @Id val id: Long,
    
    @Key(group = "tenant_orderno")
    val tenantId: Long,
    
    @Key(group = "tenant_orderno")
    val orderNo: String,
    
    val amount: BigDecimal
)
```

生成：
```sql
CREATE TABLE `order` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `tenantId` BIGINT,
  `orderNo` VARCHAR(255),
  `amount` DECIMAL
);

-- 租户ID + 订单号联合唯一索引
-- 确保同一个租户下订单号不重复
CREATE UNIQUE INDEX `uk_order_tenant_orderno` ON `order` (`tenantId`, `orderNo`);
```

#### 场景 4：混合索引（多组 + 单字段）

```kotlin
@Entity
class Product(
    @Id val id: Long,
    
    // 第一组：分类+编码
    @Key(group = "category_code")
    val categoryId: Long,
    
    @Key(group = "category_code")
    val code: String,
    
    // 第二组：租户+SKU
    @Key(group = "tenant_sku")
    val tenantId: Long,
    
    @Key(group = "tenant_sku")
    val sku: String,
    
    // 单字段唯一索引
    @Key
    val barcode: String
)
```

生成：
```sql
CREATE TABLE `product` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `categoryId` BIGINT,
  `code` VARCHAR(255),
  `tenantId` BIGINT,
  `sku` VARCHAR(255),
  `barcode` VARCHAR(255)
);

-- 第一组联合唯一索引
CREATE UNIQUE INDEX `uk_product_category_code` ON `product` (`categoryId`, `code`);

-- 第二组联合唯一索引  
CREATE UNIQUE INDEX `uk_product_tenant_sku` ON `product` (`tenantId`, `sku`);

-- 单字段唯一索引
CREATE UNIQUE INDEX `uk_product_barcode` ON `product` (`barcode`);
```

## 🤔 为什么 Jimmer 的 @Key 是唯一索引？

### 业务唯一键 vs 性能索引

Jimmer 框架区分两种概念：

1. **业务唯一键（@Key）**：
   - 保证数据唯一性（业务约束）
   - 例如：手机号、身份证号、订单号
   - **必须是 UNIQUE INDEX**

2. **性能索引（@Index）**：
   - 提高查询性能
   - 例如：城市+年龄、姓名等
   - 可以是普通索引（NORMAL INDEX）

### 如果需要普通索引怎么办？

Jimmer 可能提供了 `@Index` 注解用于普通索引（非唯一）。如果你需要：

```kotlin
@Entity
class User(
    @Id val id: Long,
    
    // 业务唯一键
    @Key
    val phone: String,
    
    // 普通查询索引（假设有@Index注解）
    // @Index(group = "name_age")  
    val name: String,
    
    // @Index(group = "name_age")
    val age: Int
)
```

如果 Jimmer 没有 `@Index` 注解，可以：
1. 在 DDL 生成后手动添加普通索引
2. 或者扩展我们的实现，添加对普通索引的支持

## 📝 测试执行

### 运行单元测试

```bash
./gradlew :lib:ddlgenerator:tool-ddlgenerator:test --tests "*KeyIndexGenerationTest*"
```

### 测试结果

所有 8 个测试用例全部覆盖：

✅ 单字段 Key  
✅ 多字段 Key  
✅ 带 group 的联合索引  
✅ 多个 group  
✅ 混合使用  
✅ 主键排除  
✅ 三字段联合索引  
✅ 命名规则

## 🎯 总结

### 已完成

1. ✅ 标记 `getDatabaseIndexes()` 为 @Deprecated
2. ✅ 提供向后兼容的实现
3. ✅ 创建专门的单元测试 `KeyIndexGenerationTest.kt`
4. ✅ 8 个测试用例全面覆盖
5. ✅ 完善的文档说明

### 关键理解

- **Jimmer 的 @Key 是业务唯一键**，生成 UNIQUE INDEX
- **支持单字段和联合唯一键**（通过 group 参数）
- **普通性能索引需要其他机制**（@Index 或手动添加）
- **索引生成遵循明确的命名规则**（uk_ 前缀 + 小写表名）

### 相关文档

- `ENHANCED_FEATURES.md` - 增强功能说明
- `JIMMER_KEY_GROUP_SUPPORT.md` - Jimmer Key 注解详细说明
- `KeyIndexGenerationTest.kt` - 单元测试

---

**完成时间**：2025-12-07  
**状态**：✅ 已完成  
**测试状态**：✅ 单元测试已创建（8个测试用例）  
**文档状态**：✅ 已完善

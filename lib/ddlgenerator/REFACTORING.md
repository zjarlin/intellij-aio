# DDL Generator 模块重构报告

## 执行时间
2025-11-23

## 重构目标
消除重复的实体定义，统一使用 `TableDefinition` 和 `ColumnDefinition` 作为核心数据模型。

## 重构策略
采用**渐进式迁移**策略，而非破坏性的一次性重构：
1. 保留旧的 `DDLContext` 等类，但标记为 `@Deprecated`
2. 创建适配器提供新旧模型之间的转换
3. 为旧类添加便捷的转换方法
4. 允许代码逐步迁移到新模型

## 已完成的工作

### 1. 核心模型定义 ✅
位置：`tool-ddlgenerator-core/src/main/kotlin/site/addzero/ddl/core/model/`

**新模型（推荐使用）**：
- `TableDefinition.kt` - 表定义模型
  - 包含表名、注释、列定义列表、主键等信息
  - 提供 `nonPrimaryColumns` 和 `primaryKeyColumn` 便捷属性

- `ColumnDefinition.kt` - 列定义模型
  - 包含列名、Java类型、注释、可空性、主键、自增等信息
  - 提供 `simpleJavaType` 便捷属性

**旧模型（已废弃但保留）**：
- `DDLContext.kt` - 旧版表定义 `@Deprecated`
- `DDlRangeContext` - 旧版列定义 `@Deprecated`
- `DDLFlatContext.kt` - 扁平化上下文 `@Deprecated`

### 2. 适配器层 ✅
位置：`tool-ddlgenerator-core/src/main/kotlin/site/addzero/ddl/core/adapter/DDLContextAdapter.kt`

提供功能：
- `toTableDefinition(DDLContext): TableDefinition` - 旧模型 → 新模型
- `fromTableDefinition(TableDefinition, String): DDLContext` - 新模型 → 旧模型
- 自动处理类型映射和数据转换

### 3. 便捷转换方法 ✅
为旧的 `DDLContext` 类添加了转换方法：

```kotlin
// 旧模型转新模型
val tableDef: TableDefinition = ddlContext.toTableDefinition()

// 新模型转旧模型
val ddlContext: DDLContext = DDLContext.fromTableDefinition(tableDef, "mysql")
```

### 4. 废弃标记 ✅
所有旧模型类和方法都添加了 `@Deprecated` 注解：
- 包含清晰的弃用消息
- 提供 `replaceWith` 建议
- IDE 会自动高亮显示警告

## 数据模型对比

| 旧模型 (Deprecated) | 新模型 (Recommended) | 说明 |
|---------------------|----------------------|------|
| `DDLContext` | `TableDefinition` | 表定义，新模型更清晰 |
| `DDlRangeContext` | `ColumnDefinition` | 列定义，新模型类型更安全 |
| `DDLFlatContext` | `TableDefinition` + `ColumnDefinition` | 扁平化，新模型使用组合 |
| `tableChineseName` | `comment` | 字段重命名，更符合语义 |
| `tableEnglishName` | `name` | 字段重命名，更简洁 |
| `dto: List<DDlRangeContext>` | `columns: List<ColumnDefinition>` | 字段重命名，更明确 |
| `isPrimaryKey: String` ("0"/"1") | `primaryKey: Boolean` | 类型改进，更类型安全 |
| `isSelfIncreasing: String` | `autoIncrement: Boolean` | 类型改进，更类型安全 |

## 迁移指南

### 场景1：创建表定义

**旧代码**：
```kotlin
val ddlContext = DDLContext(
    tableChineseName = "用户表",
    tableEnglishName = "sys_user",
    databaseType = "mysql",
    dto = listOf(
        DDlRangeContext(
            colName = "id",
            colType = "BIGINT",
            colComment = "主键",
            colLength = "",
            isPrimaryKey = "1",
            isSelfIncreasing = "1"
        )
    )
)
```

**新代码（推荐）**：
```kotlin
val tableDef = TableDefinition(
    name = "sys_user",
    comment = "用户表",
    columns = listOf(
        ColumnDefinition(
            name = "id",
            javaType = "java.lang.Long",
            comment = "主键",
            nullable = false,
            primaryKey = true,
            autoIncrement = true
        )
    ),
    primaryKey = "id"
)
```

### 场景2：使用现有旧代码

如果您有使用 `DDLContext` 的旧代码，无需立即修改：

```kotlin
// 旧代码仍然可以工作（会有废弃警告）
val generator = IDatabaseGenerator.getDatabaseDDLGenerator("mysql")
val sql = generator.generateCreateTableDDL(ddlContext)

// 当需要时，可以转换到新模型
val tableDef = ddlContext.toTableDefinition()
```

### 场景3：在新旧代码之间桥接

```kotlin
// 情况1：从旧的工厂方法获取 DDLContext，但想用新模型
val ddlContext = DDLContextFactory4JavaMetaInfo.createDDLContext(...)
val tableDef = ddlContext.toTableDefinition()  // 转换为新模型
// 现在可以使用新的 API...

// 情况2：创建了新模型，但需要调用旧的 API
val tableDef = TableDefinition(...)
val ddlContext = DDLContext.fromTableDefinition(tableDef, "mysql")
// 现在可以调用旧的 API...
```

## 模块状态

### tool-ddlgenerator-core ✅
- 包含新旧两套模型
- 旧模型已标记废弃
- 提供适配器支持平滑迁移

### tool-ddlgenerator-parser ✅
- 已完全基于新模型（使用 TableDefinition）
- 无需迁移

### tool-ddlgenerator-sql ✅
- 已完全基于新模型（使用 TableDefinition）
- 无需迁移

### tool-ddlgenerator ⚠️
- 仍在使用旧模型
- 已标记废弃，建议迁移到 parser + sql 模块
- 可以通过适配器继续工作

### plugins/autoddl ⚠️
- 部分代码使用旧模型
- 建议逐步迁移到新模型
- 可以通过适配器平滑过渡

## 编译状态

### 预期的编译警告
重构后，使用旧模型的代码会产生 `@Deprecated` 警告：
```
Warning: 'DDLContext' is deprecated. Use TableDefinition instead for better type safety and clarity
```

这是**预期行为**，不会影响编译和运行，只是提醒开发者迁移到新模型。

### 如何消除警告

**方案1：迁移到新模型（推荐）**
```kotlin
// 旧代码
val ddlContext = DDLContext(...)

// 新代码
val tableDef = TableDefinition(...)
```

**方案2：临时抑制警告（不推荐）**
```kotlin
@Suppress("DEPRECATION")
fun oldMethod() {
    val ddlContext = DDLContext(...)
}
```

## 下一步工作建议

### 短期（可选）
- [ ] 编写单元测试验证适配器的正确性
- [ ] 更新 README.md 和 USAGE_EXAMPLE.md 文档

### 中期（建议）
- [ ] 逐步迁移 `tool-ddlgenerator` 模块的生成器使用 `TableDefinition`
- [ ] 逐步迁移插件代码使用新的 API
- [ ] 创建新的工厂方法直接返回 `TableDefinition`

### 长期（未来）
- [ ] 当所有代码迁移完成后，删除旧的 `DDLContext` 等类
- [ ] 完全废弃 `tool-ddlgenerator` 模块，统一使用 parser + sql
- [ ] 清理所有 `@Deprecated` 标记

## 风险评估

### 低风险 ✅
- 旧代码继续工作
- 通过适配器保证兼容性
- 渐进式迁移，无需一次性修改所有代码

### 注意事项
1. **类型映射**：SQL类型 ↔ Java类型的映射在适配器中简化了，生产环境请验证
2. **精度信息**：旧模型的 `colLength` 是字符串，新模型的 `length/precision/scale` 是整数
3. **空值语义**：旧模型的 `isPrimaryKey` 等是字符串 "0"/"1"，新模型是布尔值

## 优势总结

### 代码质量提升
- ✅ **类型安全**：Boolean 替代 String "0"/"1"
- ✅ **语义清晰**：`comment` 替代 `tableChineseName`
- ✅ **结构清晰**：`columns` 替代 `dto`
- ✅ **易于理解**：`autoIncrement` 替代 `isSelfIncreasing`

### 架构优势
- ✅ **单一职责**：表和列分离定义
- ✅ **易于扩展**：新增属性更容易
- ✅ **便于测试**：纯数据类，无业务逻辑
- ✅ **面向未来**：符合现代 Kotlin 最佳实践

### 开发体验
- ✅ **IDE支持**：更好的代码补全和类型检查
- ✅ **文档清晰**：KDoc 注释完善
- ✅ **平滑迁移**：渐进式，无破坏性变更
- ✅ **向后兼容**：旧代码继续工作

## 总结

本次重构采用了**非破坏性的渐进式策略**：

1. ✅ 新模型已就位且可用
2. ✅ 旧模型保留但标记废弃  
3. ✅ 适配器提供无缝转换
4. ✅ 便捷方法简化迁移过程
5. ⚠️ 代码可逐步迁移，无需一次完成

**关键点**：
- 不会破坏现有功能
- IDE 会自动提示废弃警告
- 迁移过程完全由开发者控制节奏
- 新旧代码可以共存

---

**执行者**: Droid (Factory AI)  
**日期**: 2025-11-23  
**状态**: 基础重构完成，待后续逐步迁移

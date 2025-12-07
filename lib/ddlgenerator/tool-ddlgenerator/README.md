# DDL Generator - Kotlin风格的DDL生成器

## 概述

本模块提供了一个强大且灵活的DDL生成器，完全基于LSI（Language Structure Interface）抽象层，使用**Kotlin扩展函数**提供优雅的API，支持多种数据库方言，并使用Java SPI机制实现策略自动发现。

## 核心特性

### 1. Kotlin风格扩展函数 API ⭐⭐⭐⭐⭐
- 直接在 `LsiClass` 上调用 `.toCreateTableDDL(dialect)`
- 直接在 `LsiField` 上调用 `.toAddColumnDDL(tableName, dialect)`
- 优雅、简洁、符合Kotlin习惯

### 2. 完全面向LSI ✅
- 直接使用 `LsiClass` 和 `LsiField` 接口，无需中间数据类
- 自动从LSI元数据提取表结构信息
- 支持注解驱动的表定义（JPA、MyBatis Plus、Jimmer等）

### 3. SPI策略机制 ✅
- 使用 `ServiceLoader` 自动发现和加载数据库方言策略
- 支持自定义方言扩展，无需修改核心代码
- 策略缓存，提高性能

### 4. 多数据库支持 ✅
- MySQL
- PostgreSQL
- 易于扩展更多数据库（Oracle、SQL Server、H2、SQLite等）

## 快速开始

### 推荐用法：扩展函数API ⭐

```kotlin
import site.addzero.util.ddlgenerator.*
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField

// 1. 从LsiClass生成CREATE TABLE
val createDdl = userLsiClass.toCreateTableDDL(Dialect.MYSQL)
println(createDdl)

// 2. 从LsiClass生成DROP TABLE
val dropDdl = userLsiClass.toDropTableDDL("mysql")  // 也支持字符串方言
println(dropDdl)

// 3. 从LsiField生成ADD COLUMN
val addColumnDdl = emailField.toAddColumnDDL("users", Dialect.MYSQL)
println(addColumnDdl)

// 4. 从LsiField生成DROP COLUMN
val dropColumnDdl = emailField.toDropColumnDDL("users", "mysql")
println(dropColumnDdl)

// 5. 从LsiField生成MODIFY COLUMN
val modifyColumnDdl = emailField.toModifyColumnDDL("users", Dialect.MYSQL)
println(modifyColumnDdl)

// 6. 批量生成完整schema
val lsiClasses = listOf(userLsiClass, orderLsiClass, productLsiClass)
val schema = lsiClasses.toSchemaDDL(Dialect.POSTGRESQL)
println(schema)
```

### 传统用法：工厂模式（仍然支持）

```kotlin
import site.addzero.util.ddlgenerator.*

// 1. 创建DDL生成器（自动通过ServiceLoader加载策略）
val generator = DdlGeneratorFactory.create(Dialect.MYSQL)

// 2. 从LSI类生成DDL
val ddl = generator.createTable(lsiClass)
println(ddl)

// 3. 批量生成（自动处理表依赖关系）
val schema = generator.createSchema(listOf(userClass, orderClass))
println(schema)
```

## 架构设计

### Kotlin扩展函数 + SPI策略模式

```
用户代码
  │
  │ 调用扩展函数
  ↓
┌──────────────────────────────────────┐
│  LsiClass.toCreateTableDDL(dialect)  │  ← Kotlin 扩展函数
│  LsiField.toAddColumnDDL(...)        │
└──────────────────────────────────────┘
  │
  │ 内部调用
  ↓
┌─────────────────────────────────────────────────┐
│         DdlGeneratorFactory                     │
│  (使用ServiceLoader加载策略 - 缓存)              │
└─────────────────────────────────────────────────┘
  │
  │ 查找支持的策略
  ↓
┌─────────────────────────────────────────────────┐
│       DdlGenerationStrategy (SPI接口)           │
│    ┌─────────────────────────────────┐          │
│    │  supports(dialect): Boolean     │          │
│    │  generateCreateTable(lsiClass)  │          │
│    │  generateDropTable(tableName)   │          │
│    │  generateAddColumn(...)         │          │
│    │  generateModifyColumn(...)      │          │
│    └─────────────────────────────────┘          │
└─────────────────────────────────────────────────┘
  ▲
  │ 实现（通过META-INF/services注册）
  │
  ┌───────────┴────────────┐
  │                        │
┌────────────────┐      ┌─────────────────────┐
│  MySql         │      │  PostgreSql         │
│  Strategy      │      │  Strategy           │
└────────────────┘      └─────────────────────┘
```

**关键优势：**
- ✨ 用户只需调用扩展函数，无需关心策略加载
- ✨ 策略自动发现，易于扩展
- ✨ 内部缓存，高性能

### 完全面向LSI + 扩展函数

```
用户实体类 (Java/Kotlin)
      │
      │ LSI抽象层
      ↓
  LsiClass (接口)
      │
      │ 调用扩展函数
      ↓
  .toCreateTableDDL(Dialect.MYSQL)
      │
      │ 自动查找策略并生成
      ↓
CREATE TABLE `users` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `name` VARCHAR(255) NOT NULL,
  ...
);
```

## 扩展新的数据库方言

### 1. 创建策略实现

```kotlin
package com.example.ddl

import site.addzero.util.ddlgenerator.*
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.database.*

class OracleDdlGenerationStrategy : DdlGenerationStrategy {
    
    override fun supports(dialect: Dialect): Boolean {
        return dialect == Dialect.ORACLE
    }
    
    override fun generateCreateTable(lsiClass: LsiClass): String {
        val tableName = lsiClass.guessTableName
        val columns = lsiClass.databaseFields
        
        val columnsSql = columns.joinToString(",\n  ") { field ->
            buildColumnDefinition(field)
        }
        
        return """
            CREATE TABLE "$tableName" (
              $columnsSql
            )
        """.trimIndent()
    }
    
    override fun getColumnTypeName(
        columnType: DatabaseColumnType, 
        precision: Int?, 
        scale: Int?
    ): String {
        return when (columnType) {
            DatabaseColumnType.INT -> "NUMBER(10)"
            DatabaseColumnType.BIGINT -> "NUMBER(19)"
            DatabaseColumnType.VARCHAR -> "VARCHAR2(${precision ?: 255})"
            DatabaseColumnType.DATETIME -> "TIMESTAMP"
            DatabaseColumnType.BOOLEAN -> "NUMBER(1)"
            // ... 其他类型映射
            else -> "VARCHAR2(255)"
        }
    }
    
    // 实现其他必需方法...
}
```

### 2. 注册策略到SPI

创建文件：`META-INF/services/site.addzero.util.ddlgenerator.DdlGenerationStrategy`

```
com.example.ddl.OracleDdlGenerationStrategy
```

### 3. 使用新策略

```kotlin
// 自动通过ServiceLoader发现并加载
val generator = DdlGeneratorFactory.create(Dialect.ORACLE)
val ddl = generator.createTable(lsiClass)
```

## 支持的数据库类型映射

### MySQL

| LSI Type | MySQL Type |
|----------|------------|
| INT | INT |
| BIGINT | BIGINT |
| VARCHAR | VARCHAR(n) |
| TEXT | TEXT |
| DATETIME | DATETIME |
| BOOLEAN | TINYINT(1) |
| DECIMAL | DECIMAL(p,s) |

### PostgreSQL

| LSI Type | PostgreSQL Type |
|----------|-----------------|
| INT | INTEGER |
| BIGINT | BIGINT |
| VARCHAR | VARCHAR(n) |
| TEXT | TEXT |
| DATETIME | TIMESTAMP |
| BOOLEAN | BOOLEAN |
| DECIMAL | DECIMAL(p,s) |
| BLOB | BYTEA |

## 配置选项

### 获取支持的方言

```kotlin
val supportedDialects = DdlGeneratorFactory.getSupportedDialects()
println("Supported dialects: $supportedDialects")
```

## 依赖关系

本模块依赖：
- `lsi-core` - LSI核心接口
- `lsi-database` - 数据库相关的LSI扩展

不依赖：
- ❌ 任何具体的ORM框架
- ❌ 任何特定的数据库驱动
- ❌ 重复的数据类定义

## 最佳实践

### 1. 使用扩展函数API（最推荐）⭐

```kotlin
// ✅ 最佳：直接使用扩展函数，简洁优雅
val ddl = userLsiClass.toCreateTableDDL(Dialect.MYSQL)
val schema = lsiClasses.toSchemaDDL("postgresql")

// ✅ 可以：使用工厂模式（适合需要复用生成器的场景）
val generator = DdlGeneratorFactory.create(Dialect.MYSQL)
val ddl = generator.createTable(userLsiClass)

// ❌ 不推荐：直接实例化策略（失去缓存优势）
val strategy = MySqlDdlGenerationStrategy()
```

### 2. 方言参数灵活使用

```kotlin
// ✅ 使用枚举（类型安全）
val ddl = lsiClass.toCreateTableDDL(Dialect.MYSQL)

// ✅ 使用字符串（便于配置化）
val dialectName = config.getString("database.dialect")  // "mysql"
val ddl = lsiClass.toCreateTableDDL(dialectName)
```

### 3. 批量操作使用列表扩展

```kotlin
// ✅ 推荐：使用列表扩展函数
val allTables = listOf(userClass, orderClass, productClass)
val schema = allTables.toSchemaDDL(Dialect.MYSQL)

// ⚠️ 注意：自动处理表创建顺序和外键约束
```

### 4. 表结构变更示例

```kotlin
// 添加新列
val newColumn = userClass.fields.first { it.name == "email" }
val addColumnDdl = newColumn.toAddColumnDDL("users", Dialect.MYSQL)

// 修改列
val modifyColumnDdl = newColumn.toModifyColumnDDL("users", Dialect.MYSQL)

// 删除列
val dropColumnDdl = newColumn.toDropColumnDDL("users", Dialect.MYSQL)
```

## 故障排除

### ServiceLoader找不到策略

**问题：** `No DDL generation strategy found for dialect: MYSQL`

**解决：**
1. 检查 `META-INF/services/site.addzero.util.ddlgenerator.DdlGenerationStrategy` 文件是否存在
2. 确认文件中的类全限定名正确
3. 确保策略类在classpath中

### 生成的SQL语法错误

**问题：** 生成的DDL在目标数据库中执行失败

**解决：**
1. 检查使用的方言是否正确
2. 查看策略实现的类型映射是否准确
3. 考虑自定义策略以满足特殊需求

## 更新日志

### v2.0.0 (2025-12-07)
- ✅ 完全重构为面向LSI的设计
- ✅ 实现SPI策略机制（ServiceLoader）
- ✅ 移除所有重复的数据类定义
- ✅ 支持MySQL和PostgreSQL
- ✅ 改进的依赖解析机制

### v1.0.0
- 初始版本（已废弃）

# DDL Generator 模块重构

## 概述

DDL Generator 模块已经重构为清晰的三层架构，实现了关注点分离和面向 LSI 体系的设计。

## 模块架构

```
lib/ddlgenerator/
├── tool-ddlgenerator-core/       # 核心数据模型（无外部依赖）
├── tool-ddlgenerator-parser/     # LSI → DDLContext 解析器
├── tool-ddlgenerator-sql/        # DDLContext → SQL 生成器
└── tool-ddlgenerator/            # 旧实现（待废弃）
```

### 数据流

```
┌─────────┐      ┌──────────┐      ┌─────────┐      ┌─────────┐
│ LsiClass│ ───▶ │  Parser  │ ───▶ │ Context │ ───▶ │   SQL   │
└─────────┘      └──────────┘      └─────────┘      └─────────┘
   (LSI层)      (解析层)         (核心模型)      (SQL生成层)
```

## 模块详情

### 1. tool-ddlgenerator-core

**职责**：定义纯数据模型，不依赖任何外部库

**核心类型**：
- `TableDefinition` - 表定义
- `ColumnDefinition` - 列定义  
- `DatabaseType` - 数据库类型枚举
- `DDLGenerator` - SQL生成器接口

**特点**：
- ✅ 零外部依赖
- ✅ 纯 Kotlin 数据类
- ✅ 可独立测试

### 2. tool-ddlgenerator-parser

**职责**：基于 LSI 抽象层解析类结构为表定义

**核心组件**：
- `LsiDDLParser` - 主解析器
- `AnnotationExtractor` - 注解提取器（支持 JPA, Jimmer, Swagger, Excel等）
- `FieldTypeMapper` - Java类型映射器

**支持的注解**：
- JPA/Jakarta: `@Entity`, `@Table`, `@Column`, `@Id`, `@GeneratedValue`
- Jimmer: `@org.babyfish.jimmer.sql.*`
- Swagger: `@ApiModelProperty`, `@Schema`
- Excel: `@ExcelProperty`
- Validation: `@NotNull`

**特点**：
- ✅ 完全基于 LSI 体系
- ✅ 语言无关（支持 Java 和 Kotlin）
- ✅ 自动提取表名、列名、注释
- ✅ 智能处理主键、自增、可空等属性

### 3. tool-ddlgenerator-sql

**职责**：基于表定义生成特定数据库的 DDL 语句

**方言实现**：
- `MysqlDialect` - MySQL
- `PostgresqlDialect` - PostgreSQL
- `OracleDialect` - Oracle
- `DmDialect` - 达梦数据库
- `H2Dialect` - H2
- `TdengineDialect` - TDengine 时序数据库

**核心接口**：
```kotlin
interface SqlDialect {
    val name: String
    fun mapJavaType(column:LsiField): String
    fun formatColumnDefinition(column:LsiField): String
    fun formatCreateTable(table:LsiClass): String
    fun formatAlterTable(table:LsiClass): List<String>
}
```

**特点**：
- ✅ 方言模式，易于扩展
- ✅ 自动注册机制
- ✅ 支持 CREATE TABLE 和 ALTER TABLE

## 使用示例

### 基本用法

```kotlin
import site.addzero.ddl.parser.LsiDDLParser
import site.addzero.ddl.sql.SqlDDLGenerator
import site.addzero.util.lsi.clazz.LsiClass

// 1. 获取 LSI 类（通过 PSI、Kotlin 或反射）
val lsiClass: LsiClass = // ... 获取方式

// 2. 解析为表定义
val parser = LsiDDLParser()
val tableDef = parser.parse(lsiClass, "mysql")

// 3. 生成 SQL
val generator = SqlDDLGenerator.forDatabase("mysql")
val createTableSql = generator.generateCreateTable(tableDef)
val alterTableSqls = generator.generateAlterTableAddColumn(tableDef)

println(createTableSql)
```

### 添加新数据库支持

```kotlin
// 1. 实现方言
class MyDatabaseDialect : SqlDialect {
    override val name = "mydb"
    
    override fun mapJavaType(column:LsiField): String {
        // 实现类型映射
    }
    
    override fun formatColumnDefinition(column:LsiField): String {
        // 实现列定义格式化
    }
    
    override fun formatCreateTable(table:LsiClass): String {
        // 实现 CREATE TABLE 格式化
    }
    
    override fun formatAlterTable(table:LsiClass): List<String> {
        // 实现 ALTER TABLE 格式化
    }
}

// 2. 注册方言
SqlDialectRegistry.register(MyDatabaseDialect())

// 3. 使用
val generator = SqlDDLGenerator.forDatabase("mydb")
```

## 类型映射

### Java → SQL 类型映射示例（MySQL）

| Java 类型 | MySQL 类型 |
|-----------|-----------|
| `int`, `Integer` | `INT` |
| `long`, `Long` | `BIGINT` |
| `String` | `VARCHAR(255)` 或 `TEXT` |
| `BigDecimal` | `DECIMAL(10, 2)` |
| `boolean`, `Boolean` | `TINYINT(1)` |
| `LocalDateTime`, `Date` | `DATETIME` |
| `LocalDate` | `DATE` |
| `LocalTime` | `TIME` |

> 不同数据库的映射规则不同，详见各方言实现。

## 特性

### 智能字段过滤

自动排除：
- 静态字段
- 集合类型（List, Set, Map）
- 带 `@Transient` 注解的字段

### 智能注释提取

优先级：
1. 文档注释（JavaDoc / KDoc）
2. Swagger 注解
3. Excel 注解  
4. 字段名

### 智能表名/列名推断

- 使用 LSI 提供的 `guessTableName` 和 `columnName`
- 自动处理驼峰 ↔ 下划线转换
- 支持 `@Table`, `@Column` 注解覆盖

## 测试

```bash
# 构建所有模块
./gradlew :lib:ddlgenerator:tool-ddlgenerator-core:build
./gradlew :lib:ddlgenerator:tool-ddlgenerator-parser:build
./gradlew :lib:ddlgenerator:tool-ddlgenerator-sql:build

# 或一次性构建所有
./gradlew :lib:ddlgenerator:build
```

## 迁移指南

### 从旧实现迁移

**旧代码**：
```kotlin
val generator = IDatabaseGenerator.getDatabaseDDLGenerator("mysql")
val ddlContext = DDLContextFactory4JavaMetaInfo.createDDLContext4KtClass(ktClass, "mysql")
val sql = generator.generateCreateTableDDL(ddlContext)
```

**新代码**：
```kotlin
val parser = LsiDDLParser()
val tableDef = parser.parse(lsiClass, "mysql")
val generator = SqlDDLGenerator.forDatabase("mysql")
val sql = generator.generateCreateTable(tableDef)
```

### 优势对比

| 方面 | 旧实现 | 新实现 |
|------|--------|--------|
| 架构 | 耦合严重 | 清晰分层 |
| 抽象层 | 直接使用 PSI | 基于 LSI |
| 可测试性 | 困难 | 容易 |
| 扩展性 | 修改困难 | 易于扩展 |
| 依赖管理 | 混乱 | 清晰 |
| 语言支持 | Java/Kotlin 分别处理 | 统一处理 |

## 设计原则

1. **关注点分离**：模型、解析、生成三层独立
2. **面向接口**：LSI 抽象 + 方言模式
3. **零依赖核心**：core 模块完全独立
4. **易于测试**：每层都可独立测试
5. **易于扩展**：添加新数据库只需实现方言

## 后续计划

- [ ] 迁移现有调用代码到新架构
- [ ] 添加单元测试
- [ ] 支持更多注解（MyBatis Plus, Spring Data JPA等）
- [ ] 支持索引、外键等高级特性
- [ ] 生成迁移脚本（Flyway, Liquibase）

## 贡献

欢迎贡献新的数据库方言实现或功能增强！

---

**作者**: Droid (Factory AI)  
**日期**: 2025-11-23

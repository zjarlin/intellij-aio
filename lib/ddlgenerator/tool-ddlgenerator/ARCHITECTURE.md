# DDL Generator 架构文档

## 📁 目录结构

```
ddlgenerator/
├── api/                                # 核心 API 和接口层
│   ├── DdlGenerationStrategy.kt        # DDL 生成策略接口（SPI）
│   └── DdlGeneratorFactory.kt          # 工厂类，使用 ServiceLoader 加载策略
│
├── strategy/                           # 策略实现层（按数据库分类）
│   ├── MySqlDdlStrategy.kt             # MySQL DDL 生成策略
│   └── PostgreSqlDdlStrategy.kt        # PostgreSQL DDL 生成策略
│
├── extension/                          # 扩展函数层
│   └── LsiDdlExtensions.kt             # LSI 类的便捷扩展方法
│
├── diff/                               # 差异比对功能
│   ├── model/                          # 差异模型
│   │   └── TableDiff.kt                # 表差异数据模型
│   ├── matcher/                        # 匹配器
│   │   └── ColumnMatcher.kt            # 列类型匹配和比对
│   └── comparator/                     # 比对器
│       └── TableComparator.kt          # 表结构比对器
│
└── delta/                              # 差量 SQL 生成
    ├── DeltaDdlGenerator.kt            # 差量 DDL 生成器
    ├── DeltaDdlExtensions.kt           # 差量 DDL 扩展函数
    └── example/                        # 使用示例
        └── DeltaDdlUsageExample.kt     # 详细的使用示例代码
```

## 🏗️ 架构设计

### 1. **API 层** (`api/`)
- **职责**: 定义核心接口和工厂类
- **设计模式**: 策略模式 + 工厂模式 + SPI (Service Provider Interface)
- **特点**: 
  - 使用 Java ServiceLoader 实现插件化
  - 通过 SPI 自动发现和加载策略实现
  - 支持运行时动态添加新数据库支持

### 2. **策略层** (`strategy/`)
- **职责**: 实现具体数据库的 DDL 生成逻辑
- **设计模式**: 策略模式
- **扩展方式**: 
  1. 创建新的策略类实现 `DdlGenerationStrategy`
  2. 在 `META-INF/services/site.addzero.util.ddlgenerator.api.DdlGenerationStrategy` 中注册
  3. 自动被工厂类发现和加载

### 3. **扩展层** (`extension/`)
- **职责**: 提供便捷的扩展函数
- **特点**: Kotlin 扩展函数风格，更符合 Kotlin 惯用法

### 4. **差异比对** (`diff/`)
分为三个子模块：
- **model**: 数据模型（TableDiff, SchemaDiff, ColumnModification 等）
- **matcher**: 匹配算法（类型映射、列属性比对）
- **comparator**: 比对逻辑（表级比对、Schema 级比对）

### 5. **差量生成** (`delta/`)
- **职责**: 基于差异模型生成增量 SQL
- **特点**: 
  - 支持配置化（允许/禁止 DROP 语句）
  - 智能类型转换
  - 安全的差异应用

## 🔌 SPI 配置

位置: `src/main/resources/META-INF/services/site.addzero.util.ddlgenerator.api.DdlGenerationStrategy`

内容:
```
site.addzero.util.ddlgenerator.strategy.MySqlDdlStrategy
site.addzero.util.ddlgenerator.strategy.PostgreSqlDdlStrategy
```

## 🚀 使用方式

### 方式一：扩展函数（推荐）
```kotlin
val ddl = lsiClass.toCreateTableDDL(DatabaseType.MYSQL)
```

### 方式二：通过工厂
```kotlin
val strategy = DdlGeneratorFactory.getStrategy(DatabaseType.MYSQL)
val ddl = strategy.generateCreateTable(lsiClass)
```

### 方式三：差量 DDL
```kotlin
val deltaSql = lsiClasses.generateDeltaDdl(
    dbTables = dbMetadata,
    databaseType = DatabaseType.MYSQL
)
```

## 🎯 设计原则

1. **单一职责**: 每个类/包只负责一个功能
2. **开闭原则**: 对扩展开放（添加新数据库），对修改封闭
3. **依赖倒置**: 依赖抽象（DdlGenerationStrategy），不依赖具体实现
4. **接口隔离**: 清晰的模块划分，职责明确
5. **命名规范**: 
   - Strategy 类以 `XxxDdlStrategy` 命名
   - 扩展函数文件以 `XxxExtensions` 命名
   - 示例代码放在 `example/` 子包

## 📦 模块依赖

```
api (核心接口)
  ↑
  ├─ strategy (策略实现)
  ├─ extension (扩展函数)
  └─ delta (差量生成)
       ↑
       └─ diff (差异比对)
            ├─ model (数据模型)
            ├─ matcher (匹配器)
            └─ comparator (比对器)
```

## 🔄 扩展新数据库

1. 在 `strategy/` 下创建新策略类:
```kotlin
package site.addzero.util.ddlgenerator.strategy

class OracleDdlStrategy : DdlGenerationStrategy {
    override fun supports(dialect: DatabaseType) = dialect == DatabaseType.ORACLE
    // 实现其他方法...
}
```

2. 注册到 SPI 配置文件:
```
site.addzero.util.ddlgenerator.strategy.OracleDdlStrategy
```

3. 无需修改任何现有代码，自动生效！

## ⚙️ 配置和定制

- **差异比对配置**: `DiffConfig` 类
- **生成策略定制**: 实现 `DdlGenerationStrategy` 接口
- **类型映射定制**: 在策略实现中覆盖 `getColumnTypeName()` 方法

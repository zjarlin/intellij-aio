# DDL Generator 增强功能实现总结

## ✅ 已实现的增强功能

### 1. TEXT 类型智能判断

#### 实现位置
- `lsi-database/src/main/kotlin/site/addzero/util/lsi/database/LsiFieldDatabaseExt.kt`
- `lsi-database/src/main/kotlin/site/addzero/util/lsi/database/LsiFieldTypeDatabaseExt.kt`

#### 功能说明
字段会被识别为 TEXT 类型，如果满足以下任一条件：

1. **字段名包含特定关键词**（通过 `isTextType()` 方法）:
   - `url`
   - `base64`
   - `text`
   - `path`
   - `introduction`
   - `content`
   - `description`

2. **字段有 @Lob 注解**

3. **@Column(columnDefinition) 包含 TEXT/CLOB 关键字**

4. **字段长度超过 1000**（从 @Length 或 @Column(length) 获取）

#### 代码示例
```kotlin
val LsiField.isText: Boolean
    get() {
        val typeName = this.typeName ?: return false
        
        // 必须是字符串类型
        if (!TypeChecker.isStringType(typeName)) {
            return false
        }
        
        // 检查 @Lob 注解
        if (hasAnnotationIgnoreCase("Lob")) {
            return true
        }
        
        // 检查 @Column(columnDefinition)
        val columnDef = getArg("Column", "columnDefinition")
        if (columnDef != null && columnDef.containsAnyIgnoreCase("TEXT", "CLOB")) {
            return true
        }
        
        // 检查长度
        if (length > 1000) {
            return true
        }
        
        return false
    }

fun LsiField.getDatabaseColumnType(): DatabaseColumnType {
    val typeName = this.type?.qualifiedName ?: this.typeName ?: "String"
    val baseType = mapTypeToDatabaseColumnType(typeName)
    
    // 如果是VARCHAR，检查是否应该使用TEXT
    if (baseType == DatabaseColumnType.VARCHAR && (isText || isTextType())) {
        return DatabaseColumnType.TEXT
    }
    
    return baseType
}
```

### 2. Key 注解索引生成

#### 实现位置
- `lsi-database/src/main/kotlin/site/addzero/util/lsi/database/LsiClassIndexExt.kt`

#### 功能说明
- 自动识别 `@Key` 注解字段并生成索引
- 识别 `@Unique` 注解或 `@Column(unique=true)` 生成唯一索引
- 提供索引定义数据模型

#### 数据模型
```kotlin
data class IndexDefinition(
    val name: String,              // 索引名
    val columns: List<String>,     // 列名列表
    val unique: Boolean = false,   // 是否唯一索引
    val type: IndexType = IndexType.NORMAL
)

enum class IndexType {
    NORMAL,     // 普通索引
    UNIQUE,     // 唯一索引
    FULLTEXT    // 全文索引
}
```

#### 使用示例
```kotlin
// 检查字段是否为索引
val LsiField.isKey: Boolean
val LsiField.isUnique: Boolean

// 获取类的所有索引定义
val indexes = lsiClass.getIndexDefinitions()

// 生成索引DDL
val indexDdl = lsiClass.toIndexesDDL(DatabaseType.MYSQL)
```

#### 生成的SQL示例
```sql
CREATE INDEX `idx_user_username` ON `user` (`username`);
CREATE UNIQUE INDEX `uk_user_email` ON `user` (`email`);
```

### 3. JoinColumn 外键生成（增强）

#### 实现位置
- `lsi-database/src/main/kotlin/site/addzero/util/lsi/database/LsiFieldDatabaseExt.kt`

#### 功能说明
- 从 `@ManyToOne`, `@OneToOne`, `@JoinColumn` 注解提取外键信息
- 支持自定义外键名称
- 支持指定引用表和引用列

#### 数据模型
```kotlin
data class ForeignKeyInfo(
    val name: String,              // 外键名称
    val columnName: String,        // 本表列名
    val referencedTable: String,   // 引用表名
    val referencedColumn: String   // 引用列名
)
```

#### 使用示例
```kotlin
// 获取单个字段的外键信息
val foreignKey = field.getForeignKeyInfo()

// 获取类的所有外键
val foreignKeys = lsiClass.getDatabaseForeignKeys()
```

#### 生成的SQL示例
```sql
ALTER TABLE `order` 
ADD CONSTRAINT `fk_order_user_id` 
FOREIGN KEY (`user_id`) 
REFERENCES `user` (`id`);
```

### 4. ManyToMany 中间表自动生成

#### 实现位置
- `lsi-database/src/main/kotlin/site/addzero/util/lsi/database/ManyToManyTableScanner.kt`

#### 功能说明
这是最复杂的功能，支持：

1. **自动扫描所有类**，发现多对多关系
2. **识别两种情况**：
   - `mappedBy` 侧（被维护侧，跳过）
   - 拥有侧（维护关系的一侧，生成中间表）
3. **智能命名**：
   - 默认格式：`{left_table}_{right_table}_mapping`
   - 按字母顺序排序：`role_user_mapping`（不是 `user_role_mapping`）
   - 支持从 `@JoinTable` 注解获取自定义表名
4. **去重**：同一个关系只生成一次中间表

#### 数据模型
```kotlin
data class ManyToManyTable(
    val tableName: String,          // 中间表名
    val leftTableName: String,      // 左表名
    val leftColumnName: String,     // 左表ID列名
    val rightTableName: String,     // 右表名
    val rightColumnName: String,    // 右表ID列名
    val leftEntity: LsiClass,       // 左实体引用
    val rightEntity: LsiClass,      // 右实体引用
    val field: LsiField             // 关联字段引用
)
```

#### 扫描逻辑
```kotlin
object ManyToManyTableScanner {
    fun scanManyToManyTables(classes: List<LsiClass>): List<ManyToManyTable>
}

// 扩展函数
fun List<LsiClass>.scanManyToManyTables(): List<ManyToManyTable>
```

#### 支持的注解
- `javax.persistence.ManyToMany`
- `jakarta.persistence.ManyToMany`
- `org.babyfish.jimmer.sql.ManyToMany`

#### 生成的SQL示例
```sql
-- User <-> Role 多对多关系
CREATE TABLE `role_user_mapping` (
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`),
  FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
);
```

### 5. 完整 Schema 生成（一站式）

#### 实现位置
- `lib/ddlgenerator/tool-ddlgenerator/src/main/kotlin/site/addzero/util/ddlgenerator/extension/EnhancedDdlExtensions.kt`

#### 功能说明
提供便捷的扩展函数，一次性生成包含所有功能的完整 Schema：

```kotlin
fun List<LsiClass>.toCompleteSchemaDDL(
    dialect: DatabaseType,
    includeIndexes: Boolean = true,
    includeManyToManyTables: Boolean = true
): String
```

#### 生成顺序
1. 实体表（CREATE TABLE）
2. 索引（CREATE INDEX）
3. 多对多中间表（CREATE TABLE with FK）
4. 注释（ALTER TABLE COMMENT）

#### 使用示例
```kotlin
val entities = listOf(userClass, roleClass, articleClass)

// 生成完整Schema
val fullSchema = entities.toCompleteSchemaDDL(
    dialect = DatabaseType.MYSQL,
    includeIndexes = true,
    includeManyToManyTables = true
)

// 仅生成索引
val indexes = entities.flatMap { it.toIndexesDDL(DatabaseType.MYSQL) }

// 仅生成中间表
val junctionTables = entities.toManyToManyTablesDDL(DatabaseType.MYSQL)
```

## 🔑 外键约束处理（重要！）

### 为什么外键必须最后添加？

外键约束依赖于被引用的表存在，如果在 `CREATE TABLE` 时就定义外键，可能会因为被引用的表还没创建而失败。

### 正确的DDL生成顺序

```
Phase 1: 创建所有表（不含外键）
  ├─ 实体表 (User, Role, Article...)
  └─ 中间表 (role_user_mapping...)

Phase 2: 创建索引
  └─ 所有 @Key, @Unique 索引

Phase 3: 添加外键约束 ⚠️ 关键步骤
  ├─ 实体表外键 (@JoinColumn, @ManyToOne)
  └─ 中间表外键 (多对多关系)

Phase 4: 添加注释
  └─ 表注释和列注释
```

### 实现细节

**中间表生成分两步**：

1. `generateManyToManyTable()` - 创建表结构（不含外键）
```sql
CREATE TABLE `role_user_mapping` (
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`)
);
```

2. `generateManyToManyTableForeignKeys()` - 添加外键
```sql
ALTER TABLE `role_user_mapping` 
ADD CONSTRAINT `fk_role_user_mapping_user_id` 
FOREIGN KEY (`user_id`) REFERENCES `user` (`id`);

ALTER TABLE `role_user_mapping` 
ADD CONSTRAINT `fk_role_user_mapping_role_id` 
FOREIGN KEY (`role_id`) REFERENCES `role` (`id`);
```

## 📊 实际效果示例

### 示例1：Article 实体（TEXT类型）

```kotlin
@Entity
@Table(name = "article")
class Article(
    @Id val id: Long,
    
    @Key val title: String,        // 生成索引
    
    val url: String,                // 自动识别为TEXT
    val description: String,        // 自动识别为TEXT
    val content: String             // 自动识别为TEXT
)
```

生成的DDL：
```sql
-- 表定义
CREATE TABLE `article` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `title` VARCHAR(255),
  `url` TEXT,
  `description` TEXT,
  `content` TEXT
);

-- 索引
CREATE INDEX `idx_article_title` ON `article` (`title`);
```

### 示例2：User-Role 多对多关系

```kotlin
@Entity
class User(
    @Id val id: Long,
    @Key val username: String,
    
    @ManyToMany
    val roles: List<Role>
)

@Entity
class Role(
    @Id val id: Long,
    val name: String
)
```

生成的DDL：
```sql
-- User表
CREATE TABLE `user` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `username` VARCHAR(255)
);

-- Role表
CREATE TABLE `role` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `name` VARCHAR(255)
);

-- 中间表（自动生成）
CREATE TABLE `role_user_mapping` (
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`),
  FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
);

-- 索引
CREATE INDEX `idx_user_username` ON `user` (`username`);
```

## 🧪 测试覆盖

创建了完整的测试文件：
- `lib/ddlgenerator/tool-ddlgenerator/src/test/kotlin/site/addzero/util/ddlgenerator/EnhancedFeaturesTest.kt`

测试用例包括：
1. ✅ TEXT类型字段测试
2. ✅ Key注解索引生成测试
3. ✅ 多对多中间表生成测试
4. ✅ 完整Schema生成测试

## 🎯 使用建议

### 推荐用法
```kotlin
// 1. 扫描所有实体
val entities = scanAllEntities()

// 2. 生成完整Schema（推荐）
val schema = entities.toCompleteSchemaDDL(
    dialect = DatabaseType.MYSQL,
    includeIndexes = true,
    includeManyToManyTables = true
)

// 3. 输出到文件
File("schema.sql").writeText(schema)
```

### 高级用法
```kotlin
// 单独处理各个部分
val tables = entities.map { it.toCreateTableDDL(DatabaseType.MYSQL) }
val indexes = entities.flatMap { it.getIndexDefinitions() }
val junctionTables = entities.scanManyToManyTables()

// 自定义生成顺序
val customSchema = buildString {
    appendLine("-- Tables")
    tables.forEach { appendLine(it) }
    
    appendLine("-- Junction Tables")
    junctionTables.forEach { table ->
        appendLine(strategy.generateManyToManyTable(table))
    }
    
    appendLine("-- Indexes")
    indexes.forEach { index ->
        appendLine(strategy.generateCreateIndex(tableName, index))
    }
}
```

## 🔧 配置和扩展

### 添加自定义TEXT关键词
修改 `LsiFieldTypeDatabaseExt.kt`：
```kotlin
fun LsiField.isTextType(): Boolean {
    val textKeywords = listOf(
        "url", "base64", "text", "path", 
        "introduction", "content", "description",
        "your_custom_keyword"  // 添加自定义关键词
    )
    return textKeywords.any { name?.contains(it, ignoreCase = true) ?: false }
}
```

### 自定义索引命名策略
修改 `LsiClassIndexExt.kt` 的 `getIndexDefinitions()` 方法。

### 自定义中间表命名
修改 `ManyToManyTableScanner.kt` 的 `generateTableName()` 方法。

## ⚠️ 注意事项

1. **TEXT vs VARCHAR**
   - TEXT类型不支持默认值（MySQL限制）
   - TEXT类型不能作为主键或唯一键的一部分
   - 如果需要索引，考虑使用 `VARCHAR(n)` 并指定前缀长度

2. **多对多扫描**
   - 只在拥有侧生成中间表（非mappedBy侧）
   - 中间表名会自动按字母顺序生成，确保一致性
   - 需要目标实体类在扫描列表中

3. **索引生成**
   - 主键字段不会生成额外索引
   - 唯一索引会覆盖普通索引（不会重复生成）

4. **性能考虑**
   - 大型项目扫描多对多关系可能需要时间
   - 建议按需使用 `includeManyToManyTables` 参数

## 📚 相关文档

- [架构文档](ARCHITECTURE.md)
- [重构总结](REFACTORING_SUMMARY.md)
- [Clean Code 总结](CLEAN_CODE_SUMMARY.md)

# 长文本字段支持

## 概述

DDL 生成器现在完全支持长文本字段的自动识别和相应 DDL 类型的生成。

## LsiField.isText 属性

在 `lsi-database` 模块中新增了 `LsiField.isText` 扩展属性，用于检测长文本字段。

### 检测规则

字段被识别为长文本需满足以下条件之一：

1. **字段类型为 String** + **长度超过阈值**
   - `@Length(value=2000)` 或 `@Length(max=2000)`
   - `@Column(length=2000)`  
   - 阈值：**>1000** 字符

2. **有 @Lob 注解**
   ```java
   @Lob
   private String content;
   ```

3. **@Column(columnDefinition) 显式声明**
   ```java
   @Column(columnDefinition = "TEXT")
   private String description;
   
   @Column(columnDefinition = "LONGTEXT")
   private String largeContent;
   ```

## DDL 类型映射

### MySQL

根据字段长度智能选择文本类型：

| 长度范围 | SQL类型 | 最大容量 |
|---------|---------|----------|
| ≤ 1000 | VARCHAR(n) | 正常字符串 |
| 1001 - 65,535 | TEXT | 64 KB |
| 65,536 - 16,777,215 | MEDIUMTEXT | 16 MB |
| > 16,777,215 | LONGTEXT | 4 GB |

### PostgreSQL

PostgreSQL 的 TEXT 类型没有长度限制，统一使用 `TEXT`。

## 使用示例

### 示例 1: 使用 @Length 注解

```java
@Entity
@Table(name = "article")
public class Article {
    @Id
    private Long id;
    
    // 短字符串 - 生成 VARCHAR(255)
    @Length(max = 255)
    private String title;
    
    // 长文本 - 生成 TEXT
    @Length(max = 5000)
    private String content;
    
    // 超长文本 - 生成 LONGTEXT (MySQL)
    @Length(max = 20000000)
    private String fullText;
}
```

**生成的 MySQL DDL:**

```sql
CREATE TABLE `article` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `title` VARCHAR(255),
    `content` TEXT,
    `full_text` LONGTEXT
);
```

**生成的 PostgreSQL DDL:**

```sql
CREATE TABLE "article" (
    "id" BIGINT NOT NULL PRIMARY KEY,
    "title" VARCHAR(255),
    "content" TEXT,
    "full_text" TEXT
);
```

### 示例 2: 使用 @Lob 注解

```java
@Entity
public class Document {
    @Id
    private Long id;
    
    @Lob
    private String content;  // 自动识别为长文本
}
```

**生成的 DDL:**

```sql
-- MySQL
CREATE TABLE `document` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `content` TEXT
);

-- PostgreSQL
CREATE TABLE "document" (
    "id" BIGINT NOT NULL PRIMARY KEY,
    "content" TEXT
);
```

### 示例 3: 显式指定类型

```java
@Entity
public class BlogPost {
    @Id
    private Long id;
    
    @Column(columnDefinition = "MEDIUMTEXT")
    private String body;
}
```

## 代码实现

### lsi-database 模块

`LsiFieldDatabaseExt.kt`中的核心实现：

```kotlin
/**
 * 是否为长文本字段
 * 满足以下条件之一即为长文本：
 * 1. 字段类型为 String 且 @Length 或 @Column(length) 的值 > 1000
 * 2. 有 @Lob 注解
 * 3. @Column(columnDefinition) 包含 TEXT/CLOB 关键字
 */
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
        if (columnDef != null && 
            columnDef.containsAnyIgnoreCase("TEXT", "CLOB", "LONGTEXT", "MEDIUMTEXT")) {
            return true
        }
        
        // 检查长度是否超过阈值 (1000)
        val fieldLength = length
        if (fieldLength > 1000) {
            return true
        }
        
        return false
    }
```

### DDL 生成策略

**MySqlDdlStrategy.kt:**

```kotlin
private fun buildColumnDefinition(field: LsiField): String {
    // ...
    val columnTypeName = if (field.isText) {
        val length = field.length
        when {
            length > 16777215 -> "LONGTEXT"
            length > 65535 -> "MEDIUMTEXT"
            else -> "TEXT"
        }
    } else {
        // 正常类型映射
    }
    // ...
}
```

**PostgreSqlDdlStrategy.kt:**

```kotlin
private fun buildColumnDefinition(field: LsiField): String {
    // ...
    val columnTypeName = if (field.isText) {
        "TEXT"  // PostgreSQL TEXT 没有长度限制
    } else {
        // 正常类型映射
    }
    // ...
}
```

## API 使用

使用扩展函数生成 DDL：

```kotlin
import site.addzero.util.ddlgenerator.toCreateTableDDL
import site.addzero.util.db.DatabaseType

// 生成 MySQL DDL
val mysqlDdl = articleLsiClass.toCreateTableDDL(DatabaseType.MYSQL)

// 生成 PostgreSQL DDL
val postgresqlDdl = articleLsiClass.toCreateTableDDL(DatabaseType.POSTGRESQL)
```

## 优势

1. **自动识别**：根据注解和长度自动判断是否需要使用长文本类型
2. **数据库适配**：MySQL 和 PostgreSQL 使用各自最优的文本类型
3. **精确控制**：支持显式指定 columnDefinition 来覆盖默认行为
4. **向后兼容**：不影响现有的 VARCHAR 字段

## 最佳实践

1. **使用 @Length 注解**明确字段最大长度，便于自动选择合适的数据库类型
2. **对于富文本、长描述等场景**，使用 `@Lob` 注解标记
3. **谨慎使用 LONGTEXT**，它会占用更多存储空间和影响性能
4. **PostgreSQL 场景下**，TEXT 类型足够应对大部分场景

## 注意事项

- ❗ TEXT 类型字段不能作为主键
- ❗ TEXT 字段不能设置默认值
- ❗ LONGTEXT 字段会影响查询性能，慎用
- ✅ 对于用户输入的长文本（如文章、评论），使用 TEXT 即可
- ✅ 对于短字符串（如姓名、标题），继续使用 VARCHAR

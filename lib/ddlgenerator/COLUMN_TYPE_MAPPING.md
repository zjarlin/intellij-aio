# 列类型映射架构

## 问题背景

原有设计使用固定的`DatabaseColumnType`枚举作为中间层：
```
Java/Kotlin类型 → DatabaseColumnType → 数据库SQL类型
```

**局限性：**
1. ❌ 无法表示数据库特有类型（如MySQL的MEDIUMTEXT、PostgreSQL的JSONB）
2. ❌ 类型映射逻辑分散在两处（LsiFieldDatabaseExt和Strategy）
3. ❌ 扩展性差，添加新类型需要修改枚举

## 新架构设计

### 核心思想

**让每个数据库策略拥有自己的类型映射表**，直接从Java/Kotlin类型映射到SQL类型：

```
Java/Kotlin类型 → [策略的ColumnTypeMapper] → 数据库SQL类型
```

### 架构组件

#### 1. ColumnTypeMapper接口

```kotlin
interface ColumnTypeMapper {
    /**
     * 将字段映射到数据库列类型
     */
    fun mapFieldToColumnType(field: LsiField): String
    
    /**
     * 获取数据库特有类型（可选）
     */
    fun getDatabaseSpecificType(field: LsiField): String? = null
}
```

#### 2. AbstractColumnTypeMapper基类

提供通用的映射逻辑：

```kotlin
abstract class AbstractColumnTypeMapper : ColumnTypeMapper {
    // 完全限定类型名映射
    protected abstract val typeMappings: Map<String, (LsiField) -> String>
    
    // 简单类型名映射
    protected abstract val simpleTypeMappings: Map<String, (LsiField) -> String>
    
    override fun mapFieldToColumnType(field: LsiField): String {
        // 1. 检查数据库特有类型
        // 2. 检查完全限定类型
        // 3. 检查简单类型
        // 4. 返回默认类型
    }
}
```

#### 3. 具体实现：MySqlColumnTypeMapper

```kotlin
class MySqlColumnTypeMapper : AbstractColumnTypeMapper() {
    override val typeMappings = mapOf(
        "java.lang.String" to { field -> mapStringType(field) },
        "java.lang.Long" to { "BIGINT" },
        "java.lang.Boolean" to { "TINYINT(1)" },
        "java.util.UUID" to { "CHAR(36)" },
        "java.time.Year" to { "YEAR" },  // MySQL特有
        // ... 更多映射
    )
    
    private fun mapStringType(field: LsiField): String {
        if (field.isText) {
            return when {
                field.length > 16_777_215 -> "LONGTEXT"  // MySQL特有
                field.length > 65_535 -> "MEDIUMTEXT"    // MySQL特有
                else -> "TEXT"
            }
        }
        return "VARCHAR(${field.length})"
    }
}
```

#### 4. 具体实现：PostgreSqlColumnTypeMapper

```kotlin
class PostgreSqlColumnTypeMapper : AbstractColumnTypeMapper() {
    override val typeMappings = mapOf(
        "java.lang.String" to { field -> 
            if (field.isText) "TEXT" else "VARCHAR(${field.length})"
        },
        "java.lang.Long" to { "BIGINT" },
        "java.lang.Boolean" to { "BOOLEAN" },
        "java.util.UUID" to { "UUID" },          // PostgreSQL原生UUID
        "byte[]" to { "BYTEA" },                 // PostgreSQL特有
        "java.time.ZonedDateTime" to { "TIMESTAMP WITH TIME ZONE" },
        "java.lang.String[]" to { "TEXT[]" },    // PostgreSQL数组
        // ... 更多映射
    )
    
    override fun getDatabaseSpecificType(field: LsiField): String? {
        // 检查@CaseInsensitive注解
        if (field.hasAnnotation("CaseInsensitive")) {
            return "CITEXT"  // PostgreSQL特有
        }
        return null
    }
}
```

## 支持的数据库特有类型

### MySQL特有类型

| Java/Kotlin类型 | MySQL类型 | 说明 |
|-----------------|-----------|------|
| String (长文本) | MEDIUMTEXT | 64KB-16MB |
| String (超长文本) | LONGTEXT | >16MB |
| Boolean | TINYINT(1) | MySQL布尔类型 |
| Year | YEAR | 年份类型 |
| JsonNode | JSON | MySQL 5.7+ |
| enum | ENUM('a','b') | 需要显式指定 |

### PostgreSQL特有类型

| Java/Kotlin类型 | PostgreSQL类型 | 说明 |
|-----------------|----------------|------|
| UUID | UUID | 原生UUID类型 |
| byte[] | BYTEA | 二进制数据 |
| JsonNode | JSONB | 二进制JSON |
| String[] | TEXT[] | 数组类型 |
| ZonedDateTime | TIMESTAMP WITH TIME ZONE | 带时区 |
| Duration | INTERVAL | 时间间隔 |
| @CaseInsensitive String | CITEXT | 大小写不敏感 |

### Oracle特有类型（未来支持）

| Java/Kotlin类型 | Oracle类型 | 说明 |
|-----------------|------------|------|
| String (长文本) | CLOB | 字符大对象 |
| byte[] | BLOB | 二进制大对象 |
| String | VARCHAR2 | Oracle的字符串 |
| BigDecimal | NUMBER | 数值类型 |

## 使用示例

### 场景1：使用PostgreSQL的UUID原生类型

```java
@Entity
public interface User {
    @Id
    @GeneratedValue(generatorType = UUIDIdGenerator.class)
    UUID id();  // PostgreSQL: UUID, MySQL: CHAR(36)
}
```

**生成的DDL：**
```sql
-- PostgreSQL: 使用原生UUID类型
CREATE TABLE "user" (
    "id" UUID NOT NULL PRIMARY KEY
);

-- MySQL: 使用CHAR(36)
CREATE TABLE `user` (
    `id` CHAR(36) NOT NULL PRIMARY KEY
);
```

### 场景2：PostgreSQL的数组类型

```java
@Entity
public interface Article {
    @Id
    Long id();
    
    String[] tags();  // PostgreSQL: TEXT[], MySQL: 不支持（需要JSON）
}
```

**生成的DDL：**
```sql
-- PostgreSQL: 原生数组支持
CREATE TABLE "article" (
    "id" BIGINT NOT NULL PRIMARY KEY,
    "tags" TEXT[]
);

-- MySQL: 需要使用JSON或单独的标签表
CREATE TABLE `article` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `tags` JSON
);
```

### 场景3：大小写不敏感字符串（PostgreSQL CITEXT）

```java
@Entity
public interface User {
    @Id
    Long id();
    
    @CaseInsensitive
    String email();  // PostgreSQL: CITEXT
}
```

**生成的DDL：**
```sql
-- PostgreSQL: 使用CITEXT扩展
CREATE TABLE "user" (
    "id" BIGINT NOT NULL PRIMARY KEY,
    "email" CITEXT
);
```

### 场景4：显式指定数据库类型

```java
@Entity
public interface Product {
    @Id
    Long id();
    
    @Column(columnDefinition = "MEDIUMTEXT")
    String description();  // MySQL特有类型
}
```

**生成的DDL：**
```sql
-- 使用显式指定的类型
CREATE TABLE `product` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `description` MEDIUMTEXT
);
```

## 类型映射优先级

策略按以下顺序查找类型映射：

1. **数据库特有类型** (`getDatabaseSpecificType`)
   - 检查`@Column(columnDefinition)`
   - 检查特殊注解（如`@CaseInsensitive`）

2. **完全限定类型名** (`typeMappings`)
   - 如：`java.lang.String`, `java.util.UUID`

3. **简单类型名** (`simpleTypeMappings`)
   - 如：`String`, `Long`, `Int`（Kotlin类型）

4. **默认类型** (`getDefaultMapping`)
   - 通常返回`VARCHAR(255)`

## 扩展策略

### 添加新的数据库支持（如Oracle）

```kotlin
class OracleColumnTypeMapper : AbstractColumnTypeMapper() {
    override val typeMappings = mapOf(
        "java.lang.String" to { field ->
            if (field.isText) "CLOB" else "VARCHAR2(${field.length})"
        },
        "java.lang.Long" to { "NUMBER(19)" },
        "java.lang.Boolean" to { "NUMBER(1)" },
        "byte[]" to { "BLOB" },
        "java.sql.Timestamp" to { "TIMESTAMP" },
        // ... Oracle特有映射
    )
    
    override fun getDatabaseSpecificType(field: LsiField): String? {
        // Oracle特有类型检测
        return null
    }
}

class OracleDdlStrategy : DdlGenerationStrategy {
    private val columnTypeMapper = OracleColumnTypeMapper()
    
    override fun getColumnTypeMapper() = columnTypeMapper
    
    // ... 其他方法实现
}
```

### 自定义类型映射

如果内置映射不满足需求，可以通过`@Column(columnDefinition)`显式指定：

```java
@Entity
public interface CustomEntity {
    @Column(columnDefinition = "TSVECTOR")  // PostgreSQL全文搜索
    String searchable();
    
    @Column(columnDefinition = "GEOMETRY")  // 地理空间类型
    String location();
    
    @Column(columnDefinition = "ENUM('small','medium','large')")
    String size();
}
```

## 向后兼容

旧的`DatabaseColumnType`枚举和`getColumnTypeName()`方法仍然保留，但标记为：

```kotlin
@Deprecated(
    message = "Use getColumnTypeMapper() instead for better database-specific type support",
    replaceWith = ReplaceWith("getColumnTypeMapper().mapFieldToColumnType(field)")
)
fun getColumnTypeName(columnType: DatabaseColumnType, precision: Int?, scale: Int?): String
```

现有代码继续工作，新代码推荐使用`ColumnTypeMapper`。

## 优势总结

✅ **灵活性** - 每个数据库可以定义自己的类型映射  
✅ **可扩展性** - 轻松添加数据库特有类型  
✅ **类型安全** - 编译时检查类型映射  
✅ **可维护性** - 类型映射集中在Mapper中  
✅ **向后兼容** - 不破坏现有API  
✅ **性能** - Map查找O(1)时间复杂度  

## 未来扩展

1. **地理空间类型**
   - PostGIS的GEOMETRY/GEOGRAPHY
   - MySQL的POINT/POLYGON

2. **全文搜索**
   - PostgreSQL的TSVECTOR
   - MySQL的FULLTEXT索引

3. **自定义类型**
   - PostgreSQL的CREATE TYPE
   - 枚举类型的自动生成

4. **类型转换函数**
   - 自动生成类型转换逻辑
   - JPA Converter集成

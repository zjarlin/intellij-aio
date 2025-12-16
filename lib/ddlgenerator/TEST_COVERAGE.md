# DDL生成器测试覆盖文档

## 测试概览

为DDL生成器和LSI数据库扩展创建了全面的单元测试，覆盖所有新增功能。

## 测试文件列表

### 1. lsi-database模块测试

#### JimmerIdGenerationTest.kt
**测试Jimmer ID生成策略检测**

覆盖的场景：
- ✅ `@GeneratedValue` 不指定strategy（Jimmer默认为IDENTITY）
- ✅ `@GeneratedValue(strategy = IDENTITY)` 显式指定
- ✅ `@GeneratedValue(strategy = AUTO)` - 等同于IDENTITY
- ✅ `@GeneratedValue(strategy = SEQUENCE, generatorName = "xxx")` - 有序列名
- ✅ `@GeneratedValue(strategy = SEQUENCE)` - 无序列名
- ✅ `@GeneratedValue(generatorType = UUIDIdGenerator.class)` - UUID生成器
- ✅ `@GeneratedValue(generatorType = CustomIdGenerator.class)` - 自定义生成器
- ✅ 没有`@GeneratedValue`注解（手动赋值）
- ✅ 注解名称大小写不敏感
- ✅ JPA的GenerationType枚举值支持

**测试方法数：** 10个

**核心验证：**
```kotlin
@Test
fun `test IDENTITY without strategy - Jimmer default`() {
    val field = createMockIdField(
        annotations = listOf(
            createAnnotation("GeneratedValue", emptyMap())
        )
    )
    
    assertTrue(field.isAutoIncrement)
    assertFalse(field.isSequence)
    assertFalse(field.isUUID)
}

@Test
fun `test SEQUENCE with generator name`() {
    val field = createMockIdField(
        annotations = listOf(
            createAnnotation("GeneratedValue", mapOf(
                "strategy" to "SEQUENCE",
                "generatorName" to "book_id_seq"
            ))
        )
    )
    
    assertTrue(field.isSequence)
    assertEquals("book_id_seq", field.sequenceName)
}
```

#### LongTextDetectionTest.kt
**测试长文本字段检测**

覆盖的场景：
- ✅ `@Length(value > 1000)` - 超过阈值
- ✅ `@Length(max > 1000)` - 使用max参数
- ✅ `@Column(length > 1000)` - 从Column注解获取
- ✅ `@Length(value <= 1000)` - 未超过阈值（不是长文本）
- ✅ `@Lob` 注解 - 明确标记为大对象
- ✅ `@Column(columnDefinition = "TEXT")` - 显式指定TEXT
- ✅ `@Column(columnDefinition = "LONGTEXT")` - 显式指定LONGTEXT
- ✅ `@Column(columnDefinition = "CLOB")` - Oracle的CLOB
- ✅ `@Column(columnDefinition = "MEDIUMTEXT")` - MySQL的MEDIUMTEXT
- ✅ 非String类型字段（不应识别为长文本）
- ✅ 无长度注解的字段
- ✅ 注解优先级：`@Length` > `@Column(length)`
- ✅ 参数优先级：`value` > `max`
- ✅ 阈值边界测试：1000（不是）vs 1001（是）
- ✅ columnDefinition不区分大小写

**测试方法数：** 16个

**关键边界测试：**
```kotlin
@Test
fun `test threshold boundary - exactly 1000`() {
    val field = createMockStringField(
        annotations = listOf(
            createAnnotation("Length", mapOf("value" to "1000"))
        )
    )
    
    assertFalse(field.isText, "长度恰好1000不应该是长文本")
}

@Test
fun `test threshold boundary - 1001`() {
    val field = createMockStringField(
        annotations = listOf(
            createAnnotation("Length", mapOf("value" to "1001"))
        )
    )
    
    assertTrue(field.isText, "长度1001应该是长文本")
}
```

### 2. tool-ddlgenerator模块测试

#### SequenceDdlGenerationTest.kt
**测试序列DDL生成**

覆盖的场景：
- ✅ PostgreSQL SEQUENCE语法 - `DEFAULT nextval('seq_name')`
- ✅ Schema生成时序列在表之前创建
- ✅ 多个序列的批量创建
- ✅ IDENTITY生成 - 无序列
- ✅ MySQL AUTO_INCREMENT - 不支持序列
- ✅ 混合使用IDENTITY和SEQUENCE的Schema
- ✅ SEQUENCE未指定generatorName - 使用默认名称
- ✅ 多个实体共享序列 - 不重复创建

**测试方法数：** 8个

**核心验证：**
```kotlin
@Test
fun `test schema generation creates sequence first`() {
    val book = createBookWithSequence(...)
    val author = createAuthorWithSequence(...)
    
    val schema = listOf(book, author).toSchemaDDL(DatabaseType.POSTGRESQL)
    
    val sequenceIndex = schema.indexOf("CREATE SEQUENCE")
    val tableIndex = schema.indexOf("CREATE TABLE")
    
    assertTrue(sequenceIndex >= 0)
    assertTrue(tableIndex > sequenceIndex, "序列应该在表之前")
    assertTrue(schema.contains("\"book_id_seq\""))
    assertTrue(schema.contains("\"author_id_seq\""))
}

@Test
fun `test no duplicate sequences in schema`() {
    // 两个实体使用相同的序列
    val book1 = createBookWithSequence(generatorName = "common_id_seq")
    val book2 = createBookWithSequence(generatorName = "common_id_seq")
    
    val schema = listOf(book1, book2).toSchemaDDL(DatabaseType.POSTGRESQL)
    
    val sequenceCount = schema.split("CREATE SEQUENCE").size - 1
    assertEquals(1, sequenceCount, "相同的序列应该只创建一次")
}
```

#### LongTextDdlGenerationTest.kt
**测试长文本DDL生成**

覆盖的场景：
- ✅ MySQL TEXT - 中等长度（1K-64K）
- ✅ MySQL MEDIUMTEXT - 大长度（64K-16M）
- ✅ MySQL LONGTEXT - 超大长度（>16M）
- ✅ MySQL VARCHAR - 短长度（<1K）
- ✅ PostgreSQL TEXT - 统一使用TEXT（无论长度）
- ✅ `@Lob`注解生成TEXT
- ✅ `@Column(columnDefinition="TEXT")`生成TEXT
- ✅ 同一表中混合短字符串和长文本

**测试方法数：** 8个

**MySQL类型选择验证：**
```kotlin
@Test
fun `test MySQL TEXT for medium length string`() {
    // 长度2000 -> TEXT
    val ddl = article.toCreateTableDDL(DatabaseType.MYSQL)
    assertTrue(ddl.contains("`content` TEXT"))
}

@Test
fun `test MySQL MEDIUMTEXT for large string`() {
    // 长度100000 -> MEDIUMTEXT
    val ddl = article.toCreateTableDDL(DatabaseType.MYSQL)
    assertTrue(ddl.contains("`content` MEDIUMTEXT"))
}

@Test
fun `test MySQL LONGTEXT for very large string`() {
    // 长度20000000 -> LONGTEXT
    val ddl = article.toCreateTableDDL(DatabaseType.MYSQL)
    assertTrue(ddl.contains("`fullText` LONGTEXT"))
}
```

**PostgreSQL统一处理验证：**
```kotlin
@Test
fun `test PostgreSQL TEXT for all text fields`() {
    // 不同长度的文本字段
    val article = mockClass(
        fields = listOf(
            // title: 255 -> VARCHAR
            // content: 5000 -> TEXT
            // fullText: 20000000 -> TEXT（不是LONGTEXT）
        )
    )
    
    val ddl = article.toCreateTableDDL(DatabaseType.POSTGRESQL)
    
    assertTrue(ddl.contains("\"title\" VARCHAR"))
    assertTrue(ddl.contains("\"content\" TEXT"))
    assertTrue(ddl.contains("\"fullText\" TEXT"))
    assertFalse(ddl.contains("LONGTEXT"), "PostgreSQL不使用LONGTEXT")
}
```

## 测试统计

| 模块 | 测试类 | 测试方法 | 覆盖功能 |
|------|--------|---------|---------|
| lsi-database | JimmerIdGenerationTest | 10 | ID生成策略检测 |
| lsi-database | LongTextDetectionTest | 16 | 长文本字段检测 |
| tool-ddlgenerator | SequenceDdlGenerationTest | 8 | 序列DDL生成 |
| tool-ddlgenerator | LongTextDdlGenerationTest | 8 | 长文本DDL生成 |
| **总计** | **4个类** | **42个测试** | **4大功能** |

## 测试覆盖的功能点

### ID生成策略（10个测试）
1. ✅ IDENTITY默认行为
2. ✅ IDENTITY显式指定
3. ✅ AUTO策略
4. ✅ SEQUENCE有名称
5. ✅ SEQUENCE无名称
6. ✅ UUID生成器
7. ✅ 自定义生成器
8. ✅ 无生成策略
9. ✅ 大小写不敏感
10. ✅ JPA枚举值

### 长文本检测（16个测试）
1. ✅ @Length超过阈值
2. ✅ @Length max参数
3. ✅ @Column length参数
4. ✅ 短长度不识别
5. ✅ @Lob注解
6. ✅ columnDefinition TEXT
7. ✅ columnDefinition LONGTEXT
8. ✅ columnDefinition CLOB
9. ✅ columnDefinition MEDIUMTEXT
10. ✅ 非String类型
11. ✅ 无注解
12. ✅ 注解优先级
13. ✅ 参数优先级
14. ✅ 阈值边界1000
15. ✅ 阈值边界1001
16. ✅ 大小写不敏感

### 序列DDL生成（8个测试）
1. ✅ PostgreSQL序列语法
2. ✅ 序列在表之前
3. ✅ 批量序列创建
4. ✅ IDENTITY无序列
5. ✅ MySQL AUTO_INCREMENT
6. ✅ 混合ID策略
7. ✅ 默认序列名
8. ✅ 共享序列不重复

### 长文本DDL生成（8个测试）
1. ✅ MySQL TEXT
2. ✅ MySQL MEDIUMTEXT
3. ✅ MySQL LONGTEXT
4. ✅ MySQL VARCHAR
5. ✅ PostgreSQL TEXT
6. ✅ @Lob生成TEXT
7. ✅ columnDefinition处理
8. ✅ 混合字段类型

## 测试质量特征

### 1. 全面性
- ✅ 覆盖所有正常场景
- ✅ 覆盖边界条件（如1000 vs 1001）
- ✅ 覆盖异常场景（如非String类型、无注解）
- ✅ 覆盖多数据库（MySQL vs PostgreSQL）

### 2. 可读性
- ✅ 使用反引号命名法：`` `test description with spaces` ``
- ✅ 清晰的Given-When-Then结构
- ✅ 有意义的断言消息
- ✅ 包含println输出便于调试

### 3. 独立性
- ✅ 每个测试独立运行
- ✅ 使用Mock对象工厂
- ✅ 不依赖外部数据库
- ✅ 不依赖其他测试

### 4. 可维护性
- ✅ 统一的Mock对象创建
- ✅ 复用的辅助方法
- ✅ 清晰的测试分组
- ✅ 完善的注释说明

## 示例：典型测试结构

```kotlin
@Test
fun `test SEQUENCE with generator name`() {
    // Given: 定义测试数据
    val field = createMockIdField(
        annotations = listOf(
            createAnnotation("GeneratedValue", mapOf(
                "strategy" to "SEQUENCE",
                "generatorName" to "book_id_seq"
            ))
        )
    )

    // When: 执行测试（隐式，通过属性访问）
    
    // Then: 验证结果
    assertFalse(field.isAutoIncrement)
    assertTrue(field.isSequence, "应该被识别为序列")
    assertEquals("book_id_seq", field.sequenceName)
    assertFalse(field.isUUID)
    assertFalse(field.hasCustomIdGenerator)
}
```

## 运行测试

### 运行所有测试
```bash
./gradlew test
```

### 运行特定测试类
```bash
./gradlew :lib-git:metaprogramming-lsi:lsi-database:test --tests "JimmerIdGenerationTest"
./gradlew :lib-git:metaprogramming-lsi:lsi-database:test --tests "LongTextDetectionTest"
./gradlew :lib:ddlgenerator:tool-ddlgenerator:test --tests "SequenceDdlGenerationTest"
./gradlew :lib:ddlgenerator:tool-ddlgenerator:test --tests "LongTextDdlGenerationTest"
```

### 运行特定测试方法
```bash
./gradlew test --tests "JimmerIdGenerationTest.test IDENTITY without strategy*"
./gradlew test --tests "SequenceDdlGenerationTest.test schema generation*"
```

## Mock对象示例

### 创建带注解的字段
```kotlin
private fun createMockIdField(
    name: String = "id",
    typeName: String = "Long",
    annotations: List<LsiAnnotation> = emptyList()
): LsiField {
    return object : LsiField {
        override val name: String = name
        override val typeName: String = typeName
        override val annotations: List<LsiAnnotation> = annotations
        // ... 其他属性
    }
}
```

### 创建注解
```kotlin
private fun createAnnotation(
    simpleName: String,
    attributes: Map<String, String>
): LsiAnnotation {
    return object : LsiAnnotation {
        override val qualifiedName: String = "org.babyfish.jimmer.sql.$simpleName"
        override val simpleName: String = simpleName
        override val attributes: Map<String, Any?> = attributes
        override fun getAttribute(name: String): Any? = attributes[name]
        override fun hasAttribute(name: String): Boolean = attributes.containsKey(name)
    }
}
```

## 未来改进方向

1. **性能测试**
   - 大量实体的批量DDL生成性能
   - 复杂关联的处理性能

2. **集成测试**
   - 真实数据库的DDL执行验证
   - 生成的DDL语法正确性验证

3. **参数化测试**
   - 使用JUnit 5的`@ParameterizedTest`
   - 减少重复代码

4. **更多数据库**
   - Oracle DDL生成测试
   - SQL Server DDL生成测试
   - H2/SQLite DDL生成测试

5. **边界情况**
   - 极长的字段名处理
   - 特殊字符的转义
   - 保留关键字的处理

## 测试最佳实践

本测试套件遵循以下最佳实践：

1. ✅ **AAA模式**（Arrange-Act-Assert）
2. ✅ **一个测试一个概念**
3. ✅ **有意义的测试名称**
4. ✅ **独立的测试用例**
5. ✅ **可重复的测试**
6. ✅ **快速的测试执行**
7. ✅ **清晰的失败消息**
8. ✅ **避免测试逻辑**

## 总结

创建了**42个全面的单元测试**，覆盖了：
- ✅ Jimmer ID生成策略的完整支持
- ✅ 长文本字段的智能检测
- ✅ PostgreSQL序列的自动创建
- ✅ MySQL多种TEXT类型的智能选择

所有测试都具备高可读性、高独立性和高可维护性。

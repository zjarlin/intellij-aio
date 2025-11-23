# DDL Generator 使用示例

## 示例1：从 Jimmer 实体生成 MySQL 建表语句

```kotlin
import site.addzero.ddl.parser.LsiDDLParser
import site.addzero.ddl.sql.SqlDDLGenerator
import site.addzero.util.lsi.clazz.LsiClass

// 假设有这样一个 Jimmer 实体
/**
 * 用户表
 */
@Entity
@Table(name = "sys_user")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @ApiModelProperty("用户名")
    @Column(length = 50, nullable = false)
    val username: String,
    
    @ApiModelProperty("邮箱")
    @Column(length = 100)
    val email: String?,
    
    @ApiModelProperty("创建时间")
    val createTime: LocalDateTime = LocalDateTime.now()
)

// 使用方式
fun generateDDL(lsiClass: LsiClass) {
    // 1. 创建解析器
    val parser = LsiDDLParser()
    
    // 2. 解析为表定义
    val tableDef = parser.parse(lsiClass, "mysql")
    
    // 3. 创建 SQL 生成器
    val generator = SqlDDLGenerator.forDatabase("mysql")
    
    // 4. 生成 CREATE TABLE 语句
    val createTableSql = generator.generateCreateTable(tableDef)
    println(createTableSql)
}
```

**生成的 SQL**：
```sql
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `email` VARCHAR(100) NULL COMMENT '邮箱',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '用户表';
```

## 示例2：生成 ALTER TABLE 语句

```kotlin
fun generateAlterTable(lsiClass: LsiClass) {
    val parser = LsiDDLParser()
    val tableDef = parser.parse(lsiClass, "mysql")
    val generator = SqlDDLGenerator.forDatabase("mysql")
    
    // 生成添加列的 ALTER TABLE 语句
    val alterStatements = generator.generateAlterTableAddColumn(tableDef)
    alterStatements.forEach { println(it) }
}
```

**生成的 SQL**：
```sql
ALTER TABLE `sys_user` ADD COLUMN `username` VARCHAR(50) NOT NULL COMMENT '用户名';
ALTER TABLE `sys_user` ADD COLUMN `email` VARCHAR(100) NULL COMMENT '邮箱';
ALTER TABLE `sys_user` ADD COLUMN `create_time` DATETIME NOT NULL COMMENT '创建时间';
```

## 示例3：支持多种数据库

```kotlin
// MySQL
val mysqlGenerator = SqlDDLGenerator.forDatabase("mysql")
val mysqlSql = mysqlGenerator.generateCreateTable(tableDef)

// PostgreSQL
val pgGenerator = SqlDDLGenerator.forDatabase("pg")
val pgSql = pgGenerator.generateCreateTable(tableDef)

// Oracle
val oracleGenerator = SqlDDLGenerator.forDatabase("oracle")
val oracleSql = oracleGenerator.generateCreateTable(tableDef)

// 达梦数据库
val dmGenerator = SqlDDLGenerator.forDatabase("dm")
val dmSql = dmGenerator.generateCreateTable(tableDef)

// H2
val h2Generator = SqlDDLGenerator.forDatabase("h2")
val h2Sql = h2Generator.generateCreateTable(tableDef)

// TDengine
val tdGenerator = SqlDDLGenerator.forDatabase("tdengine")
val tdSql = tdGenerator.generateCreateTable(tableDef)
```

## 示例4：在 IntelliJ 插件中使用

```kotlin
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.psi.KtClass
import site.addzero.util.lsi_impl.impl.psi.clazz.toLsiClass
import site.addzero.util.lsi_impl.impl.kt.clazz.toLsiClass

// 从 Java PsiClass 生成 DDL
fun generateFromJava(psiClass: PsiClass) {
    val lsiClass = psiClass.toLsiClass()
    val parser = LsiDDLParser()
    val tableDef = parser.parse(lsiClass, "mysql")
    val generator = SqlDDLGenerator.forDatabase("mysql")
    val sql = generator.generateCreateTable(tableDef)
    
    // 保存到文件或显示在编辑器
    println(sql)
}

// 从 Kotlin KtClass 生成 DDL
fun generateFromKotlin(ktClass: KtClass) {
    val lsiClass = ktClass.toLsiClass()
    val parser = LsiDDLParser()
    val tableDef = parser.parse(lsiClass, "mysql")
    val generator = SqlDDLGenerator.forDatabase("mysql")
    val sql = generator.generateCreateTable(tableDef)
    
    println(sql)
}
```

## 示例5：自定义注解提取

```kotlin
import site.addzero.ddl.parser.LsiDDLParser
import site.addzero.ddl.parser.AnnotationExtractor

// 可以自定义注解提取器
class MyAnnotationExtractor : AnnotationExtractor() {
    // 覆盖方法以支持自定义注解
}

val parser = LsiDDLParser(
    annotationExtractor = MyAnnotationExtractor()
)
```

## 示例6：直接使用核心模型

```kotlin
import site.addzero.ddl.core.model.TableDefinition
import site.addzero.ddl.core.model.ColumnDefinition
import site.addzero.ddl.sql.SqlDDLGenerator

// 手动构建表定义
val tableDef = TableDefinition(
    name = "custom_table",
    comment = "自定义表",
    columns = listOf(
        ColumnDefinition(
            name = "id",
            javaType = "java.lang.Long",
            comment = "主键",
            primaryKey = true,
            autoIncrement = true,
            nullable = false
        ),
        ColumnDefinition(
            name = "name",
            javaType = "java.lang.String",
            comment = "名称",
            length = 100,
            nullable = false
        )
    ),
    primaryKey = "id"
)

// 生成 SQL
val generator = SqlDDLGenerator.forDatabase("mysql")
val sql = generator.generateCreateTable(tableDef)
println(sql)
```

## 示例7：批量处理多个实体

```kotlin
fun batchGenerate(lsiClasses: List<LsiClass>, databaseType: String): List<String> {
    val parser = LsiDDLParser()
    val generator = SqlDDLGenerator.forDatabase(databaseType)
    
    return lsiClasses.map { lsiClass ->
        val tableDef = parser.parse(lsiClass, databaseType)
        generator.generateCreateTable(tableDef)
    }
}

// 使用
val entities = listOf(userClass, orderClass, productClass)
val sqlStatements = batchGenerate(entities, "mysql")
sqlStatements.forEach { println(it) }
```

## 示例8：检测支持的数据库类型

```kotlin
import site.addzero.ddl.core.model.DatabaseType
import site.addzero.ddl.sql.SqlDialectRegistry

// 获取所有支持的数据库类型
val supportedTypes = DatabaseType.codes
println("支持的数据库: $supportedTypes")

// 检查是否支持某个数据库
val isSupported = SqlDialectRegistry.isSupported("mysql")
println("是否支持 MySQL: $isSupported")

// 获取所有已注册的方言
val allDialects = SqlDialectRegistry.getAll()
allDialects.forEach { (name, dialect) ->
    println("数据库: $name, 方言: ${dialect::class.simpleName}")
}
```

## 示例9：处理特殊字段

```kotlin
/**
 * 商品表
 */
@Entity
data class Product(
    @Id val id: Long,
    
    @ApiModelProperty("商品名称")
    val name: String,
    
    @ApiModelProperty("商品价格")
    val price: BigDecimal,  // 自动映射为 DECIMAL(10,2)
    
    @ApiModelProperty("是否上架")
    val enabled: Boolean,  // 自动映射为 TINYINT(1)
    
    @ApiModelProperty("商品详情")
    val description: String,  // 字段名包含 'description' 自动使用 TEXT 类型
    
    @ApiModelProperty("商品图片URL")
    val imageUrl: String,  // 字段名包含 'url' 自动使用 TEXT 类型
    
    @Transient
    val tempData: String  // 标记为 @Transient，不会生成到 DDL 中
)
```

## 示例10：添加自定义数据库支持

```kotlin
import site.addzero.ddl.sql.SqlDialect
import site.addzero.ddl.sql.SqlDialectRegistry
import site.addzero.ddl.core.model.ColumnDefinition
import site.addzero.ddl.core.model.TableDefinition

// 1. 实现自定义方言
class ClickHouseDialect : SqlDialect {
    override val name = "clickhouse"
    
    override fun mapJavaType(column: ColumnDefinition): String {
        return when (column.javaType) {
            "java.lang.Integer", "int" -> "Int32"
            "java.lang.Long", "long" -> "Int64"
            "java.lang.String" -> "String"
            "java.time.LocalDateTime" -> "DateTime"
            "java.math.BigDecimal" -> "Decimal(10, 2)"
            else -> "String"
        }
    }
    
    override fun formatColumnDefinition(column: ColumnDefinition): String {
        val parts = mutableListOf<String>()
        parts.add(column.name)
        parts.add(mapJavaType(column))
        if (column.comment.isNotBlank()) {
            parts.add("COMMENT '${escapeString(column.comment)}'")
        }
        return parts.joinToString(" ")
    }
    
    override fun formatCreateTable(table: TableDefinition): String {
        val lines = mutableListOf<String>()
        lines.add("CREATE TABLE IF NOT EXISTS ${table.name} (")
        val columnDefs = table.columns.map { "    ${formatColumnDefinition(it)}" }
        lines.addAll(columnDefs)
        lines.add(") ENGINE = MergeTree()")
        lines.add("ORDER BY (${table.primaryKey ?: "id"});")
        return lines.joinToString("\n")
    }
    
    override fun formatAlterTable(table: TableDefinition): List<String> {
        return table.columns.map { column ->
            "ALTER TABLE ${table.name} ADD COLUMN ${formatColumnDefinition(column)};"
        }
    }
}

// 2. 注册方言
SqlDialectRegistry.register(ClickHouseDialect())

// 3. 使用
val generator = SqlDDLGenerator.forDatabase("clickhouse")
val sql = generator.generateCreateTable(tableDef)
```

## 常见问题

### Q1: 如何自定义类型映射？

A: 实现自己的 `SqlDialect` 并覆盖 `mapJavaType` 方法。

### Q2: 支持哪些注解？

A: 目前支持：
- JPA/Jakarta: @Entity, @Table, @Column, @Id, @GeneratedValue
- Jimmer: @org.babyfish.jimmer.sql.*
- Swagger: @ApiModelProperty, @Schema
- Excel: @ExcelProperty
- Validation: @NotNull

### Q3: 如何排除某些字段？

A: 使用 `@Transient` 注解或确保字段是静态/集合类型。

### Q4: 如何自定义表名和列名？

A: 使用 `@Table(name = "custom_name")` 和 `@Column(name = "custom_col")` 注解。

---

更多示例请参考 [README.md](README.md)

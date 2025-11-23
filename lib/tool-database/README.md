# tool-database

IntelliJ Database 插件工具类封装模块，用于获取用户在 Database 插件中配置的数据库连接，并执行 SQL 语句。

## 功能特性

- 获取项目中配置的所有数据库连接
- 按名称或 ID 查找特定数据库连接
- 执行任意 SQL 语句（SELECT、INSERT、UPDATE、DELETE 等）
- 支持批量执行 SQL
- 异步执行，支持超时控制
- 返回结构化的执行结果

## 主要类

### 1. IntellijDataSourceManager

数据源管理器，用于获取用户配置的数据库连接。

```kotlin
// 获取所有数据源
val dataSources = IntellijDataSourceManager.getAllDataSources(project)

// 按名称获取数据源
val dataSource = IntellijDataSourceManager.getDataSourceByName(project, "MySQL - localhost")

// 按ID获取数据源
val dataSource = IntellijDataSourceManager.getDataSourceById(project, "unique-id")

// 获取第一个数据源
val dataSource = IntellijDataSourceManager.getFirstDataSource(project)

// 获取所有数据源名称
val names = IntellijDataSourceManager.getDataSourceNames(project)

// 检查是否有数据源
val hasDataSources = IntellijDataSourceManager.hasDataSources(project)
```

### 2. IntellijDataBaseSqlExecutor

SQL 执行器，用于执行 SQL 语句。

#### 创建执行器

```kotlin
// 方式1: 通过数据源名称创建
val executor = IntellijDataBaseSqlExecutor.create(project, "MySQL - localhost")

// 方式2: 通过数据源ID创建
val executor = IntellijDataBaseSqlExecutor.createById(project, "unique-id")

// 方式3: 使用第一个数据源创建
val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)

// 方式4: 手动创建
val dataSource = IntellijDataSourceManager.getDataSourceByName(project, "MySQL - localhost")
if (dataSource != null) {
    val executor = IntellijDataBaseSqlExecutor(project, dataSource)
}
```

#### 执行 SQL

```kotlin
// 执行任意SQL（自动判断是查询还是更新）
val result = executor.executeSql("SELECT * FROM users")

// 执行查询
val result = executor.executeQuery("SELECT * FROM users WHERE id = 1")

// 执行更新
val result = executor.executeUpdate("UPDATE users SET name = 'John' WHERE id = 1")

// 批量执行
val sqlList = listOf(
    "INSERT INTO users (name) VALUES ('Alice')",
    "INSERT INTO users (name) VALUES ('Bob')"
)
val results = executor.executeBatch(sqlList)

// 设置超时时间（秒）
val result = executor.executeSql("SELECT * FROM large_table", timeoutSeconds = 60)
```

### 3. SqlExecutionResult

SQL 执行结果封装类。

```kotlin
data class SqlExecutionResult(
    val success: Boolean,           // 是否执行成功
    val rowsAffected: Int = 0,      // 影响的行数
    val resultData: List<Map<String, Any?>> = emptyList(), // 查询结果数据
    val errorMessage: String? = null,  // 错误信息
    val executionTimeMs: Long = 0   // 执行时间（毫秒）
)
```

#### 使用示例

```kotlin
val result = executor.executeQuery("SELECT id, name, email FROM users")

if (result.success) {
    println("查询成功，耗时: ${result.executionTimeMs}ms")
    println("返回 ${result.rowsAffected} 条记录")
    
    // 遍历结果
    result.resultData.forEach { row ->
        val id = row["id"]
        val name = row["name"]
        val email = row["email"]
        println("ID: $id, Name: $name, Email: $email")
    }
} else {
    println("查询失败: ${result.errorMessage}")
}
```

## 完整使用示例

```kotlin
import com.addzero.util.database.IntellijDataBaseSqlExecutor
import com.addzero.util.database.IntellijDataSourceManager
import com.intellij.openapi.project.Project

class DatabaseService {
    
    fun queryUsers(project: Project) {
        // 检查是否有数据源
        if (!IntellijDataSourceManager.hasDataSources(project)) {
            println("项目中没有配置数据源")
            return
        }
        
        // 创建执行器
        val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)
        if (executor == null) {
            println("无法创建SQL执行器")
            return
        }
        
        // 执行查询
        val result = executor.executeQuery("""
            SELECT id, name, email 
            FROM users 
            WHERE status = 'active'
            ORDER BY created_at DESC
            LIMIT 10
        """.trimIndent())
        
        // 处理结果
        if (result.success) {
            println("查询成功，共 ${result.rowsAffected} 条记录，耗时 ${result.executionTimeMs}ms")
            result.resultData.forEach { row ->
                println("User: ${row["name"]} (${row["email"]})")
            }
        } else {
            println("查询失败: ${result.errorMessage}")
        }
    }
    
    fun updateUser(project: Project, userId: Int, newName: String) {
        val executor = IntellijDataBaseSqlExecutor.createWithFirst(project) ?: return
        
        val result = executor.executeUpdate(
            "UPDATE users SET name = '$newName' WHERE id = $userId"
        )
        
        if (result.success) {
            println("更新成功，影响 ${result.rowsAffected} 行")
        } else {
            println("更新失败: ${result.errorMessage}")
        }
    }
    
    fun batchInsert(project: Project, names: List<String>) {
        val executor = IntellijDataBaseSqlExecutor.createWithFirst(project) ?: return
        
        val sqlList = names.map { name ->
            "INSERT INTO users (name) VALUES ('$name')"
        }
        
        val results = executor.executeBatch(sqlList)
        
        val successCount = results.count { it.success }
        val failCount = results.count { !it.success }
        
        println("批量插入完成: 成功 $successCount 条，失败 $failCount 条")
    }
}
```

## 注意事项

1. **线程安全**: SQL 执行在后台线程池中异步执行，不会阻塞 UI 线程
2. **超时控制**: 默认超时时间为 30 秒，可以通过参数调整
3. **连接管理**: 连接由 IntelliJ Database 插件管理，无需手动关闭
4. **SQL 注入**: 使用时需注意 SQL 注入风险，建议使用参数化查询或对输入进行验证
5. **错误处理**: 所有异常都会被捕获并封装在 SqlExecutionResult 中，不会抛出
6. **依赖要求**: 需要用户在 IntelliJ IDEA 中安装并配置 Database 插件

## 依赖

本模块使用项目的标准 IntelliJ 插件构建配置：

```kotlin
plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    
    intellijPlatform {
        bundledPlugin("com.intellij.database")
    }
}
```

### 在其他模块中使用

在你的模块的 `build.gradle.kts` 中添加依赖：

```kotlin
dependencies {
    implementation(project(":lib:tool-database"))
}
```

## 许可证

与项目主许可证一致

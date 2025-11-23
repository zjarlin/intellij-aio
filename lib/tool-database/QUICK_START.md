# tool-database 快速开始

## 1. 添加依赖

在你的模块的 `build.gradle.kts` 中添加依赖：

```kotlin
dependencies {
    implementation(project(":lib:tool-database"))
}
```

## 2. 基本用法

### 获取数据源

```kotlin
import com.addzero.util.database.IntellijDataSourceManager

// 获取所有数据源
val dataSources = IntellijDataSourceManager.getAllDataSources(project)

// 按名称获取
val dataSource = IntellijDataSourceManager.getDataSourceByName(project, "MySQL - localhost")

// 获取第一个数据源
val firstDataSource = IntellijDataSourceManager.getFirstDataSource(project)

// 检查是否有数据源
if (IntellijDataSourceManager.hasDataSources(project)) {
    // ...
}
```

### 创建 SQL 执行器

```kotlin
import com.addzero.util.database.IntellijDataBaseSqlExecutor

// 方式1: 使用第一个数据源
val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)

// 方式2: 按名称创建
val executor = IntellijDataBaseSqlExecutor.create(project, "MySQL - localhost")

// 方式3: 按ID创建
val executor = IntellijDataBaseSqlExecutor.createById(project, "dataSourceId")
```

### 执行 SQL

```kotlin
// 执行查询
val result = executor?.executeQuery("SELECT * FROM users LIMIT 10")

// 执行更新
val result = executor?.executeUpdate("UPDATE users SET status = 'active' WHERE id = 1")

// 执行任意 SQL（自动判断类型）
val result = executor?.executeSql("INSERT INTO users (name) VALUES ('John')")

// 批量执行
val sqlList = listOf(
    "INSERT INTO users (name) VALUES ('Alice')",
    "INSERT INTO users (name) VALUES ('Bob')"
)
val results = executor?.executeBatch(sqlList)
```

### 处理结果

```kotlin
val result = executor?.executeQuery("SELECT * FROM users")

if (result?.success == true) {
    println("查询成功!")
    println("耗时: ${result.executionTimeMs}ms")
    println("返回记录数: ${result.rowsAffected}")
    
    // 遍历结果
    result.resultData.forEach { row ->
        val id = row["id"]
        val name = row["name"]
        val email = row["email"]
        println("ID: $id, Name: $name, Email: $email")
    }
} else {
    println("查询失败: ${result?.errorMessage}")
}
```

## 3. 完整示例

```kotlin
import com.addzero.util.database.IntellijDataBaseSqlExecutor
import com.addzero.util.database.IntellijDataSourceManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class MyDatabaseAction : AnAction("My Database Action") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 1. 检查数据源
        if (!IntellijDataSourceManager.hasDataSources(project)) {
            Messages.showWarningDialog(
                project,
                "请先配置数据源",
                "未找到数据源"
            )
            return
        }
        
        // 2. 创建执行器
        val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)
        if (executor == null) {
            Messages.showErrorDialog(project, "无法创建SQL执行器", "错误")
            return
        }
        
        // 3. 执行SQL
        val result = executor.executeQuery("SELECT COUNT(*) as count FROM users")
        
        // 4. 显示结果
        if (result.success) {
            val count = result.resultData.firstOrNull()?.get("count")
            Messages.showInfoMessage(
                project,
                "用户总数: $count\n耗时: ${result.executionTimeMs}ms",
                "查询结果"
            )
        } else {
            Messages.showErrorDialog(
                project,
                result.errorMessage ?: "未知错误",
                "查询失败"
            )
        }
    }
}
```

## 4. API 参考

### IntellijDataSourceManager

| 方法 | 说明 |
|------|------|
| `getAllDataSources(project)` | 获取所有数据源 |
| `getDataSourceByName(project, name)` | 按名称获取数据源 |
| `getDataSourceById(project, id)` | 按ID获取数据源 |
| `getFirstDataSource(project)` | 获取第一个数据源 |
| `getDataSourceNames(project)` | 获取所有数据源名称 |
| `hasDataSources(project)` | 检查是否有数据源 |

### IntellijDataBaseSqlExecutor

| 方法 | 说明 |
|------|------|
| `executeSql(sql, timeout)` | 执行任意SQL（自动判断） |
| `executeQuery(sql, timeout)` | 执行查询SQL |
| `executeUpdate(sql, timeout)` | 执行更新SQL |
| `executeBatch(sqlList, timeout)` | 批量执行SQL |

### SqlExecutionResult

| 属性 | 类型 | 说明 |
|------|------|------|
| `success` | Boolean | 是否执行成功 |
| `rowsAffected` | Int | 影响的行数 |
| `resultData` | List<Map<String, Any?>> | 查询结果数据 |
| `errorMessage` | String? | 错误信息 |
| `executionTimeMs` | Long | 执行时间（毫秒） |

## 5. 注意事项

1. **必须安装 Database 插件**: 用户需要在 IntelliJ IDEA 中安装并配置 Database 插件
2. **异步执行**: SQL 在后台线程中异步执行，默认超时 30 秒
3. **错误处理**: 所有异常都会被捕获并封装在 `SqlExecutionResult` 中
4. **SQL 注入**: 注意防范 SQL 注入，建议对用户输入进行验证
5. **连接管理**: 连接由 IntelliJ Database 插件管理，无需手动关闭

## 6. 故障排除

**问题**: 找不到数据源
- 确保用户已在 Database 工具窗口中配置了数据源
- 使用 `IntellijDataSourceManager.hasDataSources(project)` 检查

**问题**: SQL 执行超时
- 增加超时时间：`executor.executeSql(sql, timeoutSeconds = 60)`
- 检查 SQL 语句是否过于复杂
- 检查数据库连接是否正常

**问题**: 无法创建执行器
- 确保数据源存在且可用
- 检查数据源连接配置是否正确
- 查看 IntelliJ IDEA 日志获取详细错误信息

## 7. 更多示例

查看 `USAGE_EXAMPLE.md` 获取更多实际使用示例。

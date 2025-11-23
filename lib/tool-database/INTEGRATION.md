# tool-database 模块集成指南

## 1. 模块依赖

### 在其他模块中引入此模块

在你的模块的 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation(project(":lib:tool-database"))
}
```

### 示例：在 autoddl 插件中集成

`plugins/autoddl/build.gradle.kts`:

```kotlin
plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

dependencies {
    // ... 其他依赖
    
    // 添加 tool-database 依赖
    implementation(project(":lib:tool-database"))
}
```

## 2. 在 Action 中使用

### 示例: 创建一个执行 SQL 的 Action

```kotlin
package site.addzero.addl.action.database

import com.addzero.util.database.IntellijDataBaseSqlExecutor
import com.addzero.util.database.IntellijDataSourceManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class ExecuteSqlAction : AnAction("执行 SQL") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 检查数据源
        if (!IntellijDataSourceManager.hasDataSources(project)) {
            Messages.showWarningDialog(
                project,
                "请先在 Database 工具窗口中配置数据源",
                "未找到数据源"
            )
            return
        }
        
        // 获取用户输入
        val sql = Messages.showInputDialog(
            project,
            "请输入 SQL 语句:",
            "执行 SQL",
            null
        ) ?: return
        
        // 创建执行器并执行
        val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)
        if (executor == null) {
            Messages.showErrorDialog(project, "无法创建 SQL 执行器", "错误")
            return
        }
        
        val result = executor.executeSql(sql)
        
        // 显示结果
        if (result.success) {
            val message = buildString {
                appendLine("执行成功!")
                appendLine("耗时: ${result.executionTimeMs}ms")
                if (result.resultData.isNotEmpty()) {
                    appendLine("返回记录数: ${result.rowsAffected}")
                } else {
                    appendLine("影响行数: ${result.rowsAffected}")
                }
            }
            Messages.showInfoMessage(project, message, "执行结果")
        } else {
            Messages.showErrorDialog(
                project,
                result.errorMessage ?: "未知错误",
                "执行失败"
            )
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && 
            IntellijDataSourceManager.hasDataSources(project)
    }
}
```

### 在 plugin.xml 中注册 Action

```xml
<actions>
    <action id="ExecuteSqlAction"
            class="site.addzero.addl.action.database.ExecuteSqlAction"
            text="执行 SQL"
            description="在选中的数据源上执行 SQL 语句">
        <add-to-group group-id="DatabaseViewPopupMenu" anchor="last"/>
    </action>
</actions>
```

## 3. 在工具窗口中使用

### 示例: 创建数据源管理工具窗口

```kotlin
package site.addzero.addl.toolwindow

import com.addzero.util.database.IntellijDataSourceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import javax.swing.DefaultListModel

class DatabaseToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = createDataSourcePanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
    
    private fun createDataSourcePanel(project: Project): JBScrollPane {
        val listModel = DefaultListModel<String>()
        
        // 加载数据源列表
        val dataSourceNames = IntellijDataSourceManager.getDataSourceNames(project)
        dataSourceNames.forEach { name ->
            listModel.addElement(name)
        }
        
        val list = JBList(listModel)
        return JBScrollPane(list)
    }
}
```

### 在 plugin.xml 中注册工具窗口

```xml
<extensions defaultExtensionNs="com.intellij">
    <toolWindow id="Database Manager"
                anchor="right"
                factoryClass="site.addzero.addl.toolwindow.DatabaseToolWindowFactory"
                icon="AllIcons.Providers.Mysql"
                secondary="true"/>
</extensions>
```

## 4. 在服务中使用

### 示例: 创建数据库操作服务

```kotlin
package site.addzero.addl.service

import com.addzero.util.database.IntellijDataBaseSqlExecutor
import com.addzero.util.database.SqlExecutionResult
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class DatabaseOperationService(private val project: Project) {
    
    fun executeQuery(sql: String): SqlExecutionResult? {
        val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)
        return executor?.executeQuery(sql)
    }
    
    fun executeUpdate(sql: String): SqlExecutionResult? {
        val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)
        return executor?.executeUpdate(sql)
    }
    
    fun getTableNames(): List<String> {
        val sql = "SHOW TABLES"
        val result = executeQuery(sql) ?: return emptyList()
        
        return if (result.success) {
            result.resultData.mapNotNull { row ->
                row.values.firstOrNull()?.toString()
            }
        } else {
            emptyList()
        }
    }
    
    companion object {
        fun getInstance(project: Project): DatabaseOperationService {
            return project.getService(DatabaseOperationService::class.java)
        }
    }
}
```

### 在 plugin.xml 中注册服务

```xml
<extensions defaultExtensionNs="com.intellij">
    <projectService 
        serviceImplementation="site.addzero.addl.service.DatabaseOperationService"/>
</extensions>
```

### 使用服务

```kotlin
val service = DatabaseOperationService.getInstance(project)
val tableNames = service.getTableNames()
```

## 5. 错误处理最佳实践

```kotlin
fun executeSqlSafely(project: Project, sql: String): SqlExecutionResult {
    // 检查数据源
    if (!IntellijDataSourceManager.hasDataSources(project)) {
        return SqlExecutionResult.failure("未配置数据源")
    }
    
    // 创建执行器
    val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)
        ?: return SqlExecutionResult.failure("无法创建 SQL 执行器")
    
    // 执行 SQL
    val result = executor.executeSql(sql)
    
    // 记录日志
    if (!result.success) {
        logger.error("SQL 执行失败: ${result.errorMessage}")
        logger.debug("失败的 SQL: $sql")
    }
    
    return result
}
```

## 6. 异步执行示例

```kotlin
import com.intellij.openapi.application.ApplicationManager

fun executeSqlAsync(
    project: Project, 
    sql: String,
    onSuccess: (SqlExecutionResult) -> Unit,
    onError: (String) -> Unit
) {
    ApplicationManager.getApplication().executeOnPooledThread {
        val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)
        
        if (executor == null) {
            ApplicationManager.getApplication().invokeLater {
                onError("无法创建 SQL 执行器")
            }
            return@executeOnPooledThread
        }
        
        val result = executor.executeSql(sql)
        
        ApplicationManager.getApplication().invokeLater {
            if (result.success) {
                onSuccess(result)
            } else {
                onError(result.errorMessage ?: "未知错误")
            }
        }
    }
}

// 使用
executeSqlAsync(
    project,
    "SELECT * FROM users",
    onSuccess = { result ->
        Messages.showInfoMessage(
            project,
            "查询成功，返回 ${result.rowsAffected} 条记录",
            "成功"
        )
    },
    onError = { error ->
        Messages.showErrorDialog(project, error, "失败")
    }
)
```

## 7. 单元测试

### 测试数据源管理器

```kotlin
class IntellijDataSourceManagerTest {
    
    @Test
    fun testGetDataSources() {
        val project = mockProject()
        val dataSources = IntellijDataSourceManager.getAllDataSources(project)
        
        assertNotNull(dataSources)
        assertTrue(dataSources.isNotEmpty())
    }
}
```

### 测试 SQL 执行器

```kotlin
class IntellijDataBaseSqlExecutorTest {
    
    @Test
    fun testExecuteQuery() {
        val project = mockProject()
        val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)
        
        assertNotNull(executor)
        
        val result = executor!!.executeQuery("SELECT 1 as value")
        
        assertTrue(result.success)
        assertEquals(1, result.rowsAffected)
        assertEquals(1, result.resultData.first()["value"])
    }
}
```

## 8. 性能优化建议

1. **缓存执行器实例**
   ```kotlin
   private var cachedExecutor: IntellijDataBaseSqlExecutor? = null
   
   fun getExecutor(project: Project): IntellijDataBaseSqlExecutor? {
       if (cachedExecutor == null) {
           cachedExecutor = IntellijDataBaseSqlExecutor.createWithFirst(project)
       }
       return cachedExecutor
   }
   ```

2. **使用连接池**（已由 IntelliJ Database 插件管理）

3. **批量操作优化**
   ```kotlin
   // 不推荐：逐条执行
   users.forEach { user ->
       executor.executeSql("INSERT INTO users ...")
   }
   
   // 推荐：批量执行
   val sqlList = users.map { user ->
       "INSERT INTO users ..."
   }
   executor.executeBatch(sqlList)
   ```

4. **结果集限制**
   ```kotlin
   // 添加 LIMIT 避免返回过多数据
   val result = executor.executeQuery("SELECT * FROM large_table LIMIT 1000")
   ```

## 9. 常见问题

**Q: 如何处理不同类型的数据库？**
A: Database 插件会自动识别数据库类型，SQL 执行器透明处理。

**Q: 如何获取当前选中的数据源？**
A: 目前需要用户指定数据源名称或使用第一个数据源。

**Q: 是否支持事务？**
A: 可以通过批量执行 SQL 实现：`["START TRANSACTION", ..., "COMMIT"]`

**Q: 如何处理大结果集？**
A: 建议在 SQL 中添加 LIMIT，或者分批查询。

## 10. 更多资源

- [README.md](README.md) - 完整功能说明
- [QUICK_START.md](QUICK_START.md) - 快速开始指南
- [USAGE_EXAMPLE.md](USAGE_EXAMPLE.md) - 详细使用示例
- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/) - 官方文档

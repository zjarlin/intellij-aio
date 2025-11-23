# tool-database 使用示例

## 示例 1: 查询数据库中的用户列表

```kotlin
import com.addzero.util.database.IntellijDataBaseSqlExecutor
import com.addzero.util.database.IntellijDataSourceManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class QueryUsersAction : AnAction("Query Users") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 检查是否有数据源
        if (!IntellijDataSourceManager.hasDataSources(project)) {
            Messages.showWarningDialog(
                project,
                "请先在 Database 工具窗口中配置数据源",
                "未找到数据源"
            )
            return
        }
        
        // 创建 SQL 执行器
        val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)
        if (executor == null) {
            Messages.showErrorDialog(project, "无法创建 SQL 执行器", "错误")
            return
        }
        
        // 执行查询
        val sql = """
            SELECT id, username, email, created_at 
            FROM users 
            WHERE status = 'active'
            ORDER BY created_at DESC
            LIMIT 10
        """.trimIndent()
        
        val result = executor.executeQuery(sql)
        
        // 处理结果
        if (result.success) {
            val message = buildString {
                appendLine("查询成功!")
                appendLine("执行时间: ${result.executionTimeMs}ms")
                appendLine("返回记录数: ${result.rowsAffected}")
                appendLine()
                
                result.resultData.forEach { row ->
                    appendLine("用户: ${row["username"]} (${row["email"]})")
                }
            }
            
            Messages.showInfoMessage(project, message, "查询结果")
        } else {
            Messages.showErrorDialog(
                project,
                "查询失败: ${result.errorMessage}",
                "错误"
            )
        }
    }
}
```

## 示例 2: 更新用户状态

```kotlin
import com.addzero.util.database.IntellijDataBaseSqlExecutor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

class UserService {
    
    fun deactivateUser(project: Project, userId: Int): Boolean {
        val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)
            ?: return false
        
        val sql = "UPDATE users SET status = 'inactive', updated_at = NOW() WHERE id = $userId"
        val result = executor.executeUpdate(sql)
        
        if (result.success) {
            Messages.showInfoMessage(
                project,
                "成功更新 ${result.rowsAffected} 条记录",
                "更新成功"
            )
            return true
        } else {
            Messages.showErrorDialog(
                project,
                "更新失败: ${result.errorMessage}",
                "错误"
            )
            return false
        }
    }
}
```

## 示例 3: 批量插入数据

```kotlin
import com.addzero.util.database.IntellijDataBaseSqlExecutor
import com.intellij.openapi.project.Project

class BatchInsertService {
    
    fun insertUsers(project: Project, users: List<Pair<String, String>>): Int {
        val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)
            ?: return 0
        
        // 构建批量插入 SQL
        val sqlList = users.map { (username, email) ->
            "INSERT INTO users (username, email, status, created_at) " +
            "VALUES ('$username', '$email', 'active', NOW())"
        }
        
        // 执行批量插入
        val results = executor.executeBatch(sqlList, timeoutSeconds = 60)
        
        // 统计结果
        val successCount = results.count { it.success }
        val failCount = results.count { !it.success }
        
        println("批量插入完成: 成功 $successCount 条, 失败 $failCount 条")
        
        // 打印失败的记录
        results.filter { !it.success }.forEach { result ->
            println("失败原因: ${result.errorMessage}")
        }
        
        return successCount
    }
}
```

## 示例 4: 在工具窗口中显示数据源列表

```kotlin
import com.addzero.util.database.IntellijDataSourceManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import javax.swing.DefaultListModel
import javax.swing.JPanel

class DataSourceListPanel(private val project: Project) {
    
    fun createPanel(): JPanel {
        val listModel = DefaultListModel<String>()
        
        // 获取所有数据源名称
        val dataSourceNames = IntellijDataSourceManager.getDataSourceNames(project)
        dataSourceNames.forEach { name ->
            listModel.addElement(name)
        }
        
        val list = JBList(listModel)
        val scrollPane = JBScrollPane(list)
        
        return JPanel().apply {
            add(scrollPane)
        }
    }
}
```

## 示例 5: 选择特定数据源执行查询

```kotlin
import com.addzero.util.database.IntellijDataBaseSqlExecutor
import com.addzero.util.database.IntellijDataSourceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JScrollPane

class SelectDataSourceDialog(private val project: Project) : DialogWrapper(project) {
    
    private val dataSourceNames = IntellijDataSourceManager.getDataSourceNames(project)
    private val listModel = DefaultListModel<String>().apply {
        dataSourceNames.forEach { addElement(it) }
    }
    private val list = JBList(listModel)
    
    init {
        title = "选择数据源"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        return JScrollPane(list)
    }
    
    fun executeQuery(sql: String) {
        if (showAndGet()) {
            val selectedDataSourceName = list.selectedValue
            if (selectedDataSourceName != null) {
                val executor = IntellijDataBaseSqlExecutor.create(
                    project, 
                    selectedDataSourceName
                )
                
                if (executor != null) {
                    val result = executor.executeQuery(sql)
                    
                    if (result.success) {
                        val message = "查询成功!\n" +
                                     "耗时: ${result.executionTimeMs}ms\n" +
                                     "返回记录数: ${result.rowsAffected}"
                        Messages.showInfoMessage(project, message, "查询结果")
                    } else {
                        Messages.showErrorDialog(
                            project,
                            result.errorMessage ?: "未知错误",
                            "查询失败"
                        )
                    }
                }
            }
        }
    }
}

// 使用示例
fun main() {
    val dialog = SelectDataSourceDialog(project)
    dialog.executeQuery("SELECT * FROM users LIMIT 10")
}
```

## 示例 6: 事务操作（通过批量执行）

```kotlin
import com.addzero.util.database.IntellijDataBaseSqlExecutor
import com.intellij.openapi.project.Project

class TransactionService {
    
    fun transferMoney(
        project: Project,
        fromUserId: Int,
        toUserId: Int,
        amount: Double
    ): Boolean {
        val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)
            ?: return false
        
        // 构建事务 SQL 语句
        val sqlList = listOf(
            "START TRANSACTION",
            "UPDATE accounts SET balance = balance - $amount WHERE user_id = $fromUserId",
            "UPDATE accounts SET balance = balance + $amount WHERE user_id = $toUserId",
            "INSERT INTO transactions (from_user_id, to_user_id, amount, created_at) " +
            "VALUES ($fromUserId, $toUserId, $amount, NOW())",
            "COMMIT"
        )
        
        val results = executor.executeBatch(sqlList)
        
        // 检查是否所有操作都成功
        val allSuccess = results.all { it.success }
        
        if (!allSuccess) {
            // 如果有失败，执行回滚
            executor.executeSql("ROLLBACK")
            return false
        }
        
        return true
    }
}
```

## 示例 7: 导出查询结果为 CSV

```kotlin
import com.addzero.util.database.IntellijDataBaseSqlExecutor
import com.intellij.openapi.project.Project
import java.io.File

class ExportService {
    
    fun exportToCSV(project: Project, sql: String, outputFile: File): Boolean {
        val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)
            ?: return false
        
        val result = executor.executeQuery(sql)
        
        if (!result.success) {
            println("查询失败: ${result.errorMessage}")
            return false
        }
        
        // 写入 CSV
        outputFile.bufferedWriter().use { writer ->
            // 写入表头
            if (result.resultData.isNotEmpty()) {
                val headers = result.resultData.first().keys.joinToString(",")
                writer.write(headers)
                writer.newLine()
                
                // 写入数据行
                result.resultData.forEach { row ->
                    val values = row.values.joinToString(",") { value ->
                        "\"${value?.toString()?.replace("\"", "\"\""") ?: ""}\""
                    }
                    writer.write(values)
                    writer.newLine()
                }
            }
        }
        
        println("导出成功: ${result.rowsAffected} 条记录")
        return true
    }
}
```

## 示例 8: 动态表结构查询

```kotlin
import com.addzero.util.database.IntellijDataBaseSqlExecutor
import com.intellij.openapi.project.Project

class TableInspector {
    
    fun getTableStructure(project: Project, tableName: String): List<ColumnInfo> {
        val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)
            ?: return emptyList()
        
        val sql = """
            SELECT 
                COLUMN_NAME as name,
                DATA_TYPE as type,
                IS_NULLABLE as nullable,
                COLUMN_KEY as key,
                COLUMN_DEFAULT as default_value,
                COLUMN_COMMENT as comment
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = '$tableName'
            ORDER BY ORDINAL_POSITION
        """.trimIndent()
        
        val result = executor.executeQuery(sql)
        
        if (!result.success) {
            println("查询失败: ${result.errorMessage}")
            return emptyList()
        }
        
        return result.resultData.map { row ->
            ColumnInfo(
                name = row["name"] as? String ?: "",
                type = row["type"] as? String ?: "",
                nullable = (row["nullable"] as? String) == "YES",
                isPrimaryKey = (row["key"] as? String) == "PRI",
                defaultValue = row["default_value"]?.toString(),
                comment = row["comment"] as? String
            )
        }
    }
    
    data class ColumnInfo(
        val name: String,
        val type: String,
        val nullable: Boolean,
        val isPrimaryKey: Boolean,
        val defaultValue: String?,
        val comment: String?
    )
}
```

## 注意事项

1. **SQL 注入防护**: 上述示例中使用了字符串拼接，实际使用时应考虑参数化查询或输入验证
2. **错误处理**: 建议对所有数据库操作进行适当的错误处理和日志记录
3. **性能考虑**: 对于大量数据的操作，应考虑分页或流式处理
4. **线程安全**: SQL 执行器在后台线程中异步执行，UI 操作请在 EDT 线程中进行
5. **连接管理**: 连接由 IntelliJ Database 插件管理，无需手动关闭

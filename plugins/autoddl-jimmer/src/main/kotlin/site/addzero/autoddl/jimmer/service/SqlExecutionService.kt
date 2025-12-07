package site.addzero.autoddl.jimmer.service

import com.intellij.openapi.project.Project
import site.addzero.autoddl.jimmer.settings.JimmerDdlSettings
import java.io.File
import java.sql.DriverManager

// 注意：由于 SqlExecutor 可能不可用，暂时使用 JDBC 直接执行

/**
 * SQL 执行服务
 * 使用 Database 插件的连接信息和 SqlExecutor 工具类
 */
class SqlExecutionService(private val project: Project) {
    
    private val settings = JimmerDdlSettings.getInstance(project)
    
    /**
     * 执行 SQL 文件
     */
    fun executeSqlFile(sqlFile: File): ExecutionResult {
        // 1. 获取数据源配置（从 Database 插件）
        val connectionInfo = try {
            getDataSourceFromDatabasePlugin()
        } catch (e: Exception) {
            return ExecutionResult(
                success = false,
                message = "获取数据源失败：${e.message}，请在 Database 插件中配置数据源"
            )
        }
        
        if (connectionInfo == null) {
            return ExecutionResult(
                success = false,
                message = "未找到数据源：${settings.dataSourceName}，请检查配置"
            )
        }
        
        // 2. 读取 SQL 内容
        val sqlContent = sqlFile.readText()
        val sqlStatements = parseSqlStatements(sqlContent)
        
        if (sqlStatements.isEmpty()) {
            return ExecutionResult(
                success = false,
                message = "SQL 文件为空或无有效语句"
            )
        }
        
        // 3. 执行 SQL
        return try {
            executeSqlWithJdbc(connectionInfo, sqlStatements)
        } catch (e: Exception) {
            ExecutionResult(
                success = false,
                message = "执行失败：${e.message}",
                details = e.stackTraceToString()
            )
        }
    }
    
    /**
     * 使用 JDBC 执行 SQL
     */
    private fun executeSqlWithJdbc(connectionInfo: ConnectionInfo, sqlStatements: List<String>): ExecutionResult {
        val results = mutableListOf<String>()
        var successCount = 0
        var failedCount = 0
        
        DriverManager.getConnection(
            connectionInfo.url,
            connectionInfo.username,
            connectionInfo.password
        ).use { connection ->
            connection.autoCommit = false
            
            try {
                sqlStatements.forEach { sql ->
                    try {
                        connection.createStatement().use { statement ->
                            statement.execute(sql)
                        }
                        successCount++
                        results.add("✓ ${sql.take(50)}...")
                    } catch (e: Exception) {
                        failedCount++
                        results.add("✗ ${sql.take(50)}... - Error: ${e.message}")
                    }
                }
                
                if (failedCount == 0) {
                    connection.commit()
                } else {
                    connection.rollback()
                }
            } catch (e: Exception) {
                connection.rollback()
                throw e
            }
        }
        
        return ExecutionResult(
            success = failedCount == 0,
            message = "执行完成：成功 $successCount 条，失败 $failedCount 条",
            details = results.joinToString("\n"),
            successCount = successCount,
            failedCount = failedCount
        )
    }
    
    /**
     * 从 Database 插件获取数据源
     */
    private fun getDataSourceFromDatabasePlugin(): ConnectionInfo? {
        val dataSourceName = settings.dataSourceName
        if (dataSourceName.isBlank()) {
            return null
        }
        
        return try {
            // 使用反射访问 Database 插件 API（避免编译时依赖）
            val connectionManagerClass = Class.forName("com.intellij.database.dataSource.DatabaseConnectionManager")
            val getInstance = connectionManagerClass.getMethod("getInstance")
            val connectionManager = getInstance.invoke(null)
            
            val getDataSources = connectionManagerClass.getMethod("getDataSources", com.intellij.openapi.project.Project::class.java)
            val dataSources = getDataSources.invoke(connectionManager, project) as? Collection<*>
            
            val dataSource = dataSources?.firstOrNull { ds ->
                val getName = ds?.javaClass?.getMethod("getName")
                val name = getName?.invoke(ds) as? String
                name == dataSourceName
            }
            
            if (dataSource != null) {
                val getUrl = dataSource.javaClass.getMethod("getUrl")
                val getUsername = dataSource.javaClass.getMethod("getUsername")
                val getPassword = dataSource.javaClass.getMethod("getPassword")
                
                val url = getUrl.invoke(dataSource) as? String ?: throw IllegalStateException("数据源 URL 为空")
                val username = getUsername.invoke(dataSource) as? String ?: ""
                val password = getPassword.invoke(dataSource) as? String ?: ""
                
                ConnectionInfo(url, username, password)
            } else {
                null
            }
        } catch (e: ClassNotFoundException) {
            // Database 插件未安装
            null
        } catch (e: Exception) {
            throw IllegalStateException("获取数据源失败：${e.message}", e)
        }
    }
    
    /**
     * 解析 SQL 语句
     * 按分号分割，忽略注释
     */
    private fun parseSqlStatements(sqlContent: String): List<String> {
        return sqlContent
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { !it.startsWith("--") }
            .filter { !it.startsWith("/*") }
    }
}

/**
 * 连接信息
 */
data class ConnectionInfo(
    val url: String,
    val username: String,
    val password: String
)

/**
 * 执行结果
 */
data class ExecutionResult(
    val success: Boolean,
    val message: String,
    val details: String = "",
    val successCount: Int = 0,
    val failedCount: Int = 0
)

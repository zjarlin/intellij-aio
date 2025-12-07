package site.addzero.autoddl.jimmer.service

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import site.addzero.autoddl.jimmer.datasource.SpringDataSourceResolver
import site.addzero.autoddl.jimmer.settings.JimmerDdlSettings
import java.io.File
import java.sql.DriverManager

/**
 * SQL 执行服务
 * 从 Spring 配置文件自动解析 JDBC 连接信息
 */
class SqlExecutionService(private val project: Project) {

    private val log = Logger.getInstance(SqlExecutionService::class.java)
    private val settings = JimmerDdlSettings.getInstance(project)
    private val dataSourceResolver = SpringDataSourceResolver(project)

    /**
     * 执行 SQL 文件
     */
    fun executeSqlFile(sqlFile: File): ExecutionResult {
        // 1. 获取数据源配置
        val dataSourceInfo = dataSourceResolver.getDefaultDataSource()
        
        if (dataSourceInfo == null) {
            log.warn("No datasource found. Please configure in settings.")
            return ExecutionResult(
                success = false,
                message = "未找到数据源配置，请在 Settings -> AutoDDL Jimmer 中配置 JDBC 连接信息"
            )
        }
        
        log.info("Using datasource: ${dataSourceInfo.name} (${dataSourceInfo.url})")
        
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
            executeSqlWithJdbc(dataSourceInfo, sqlStatements)
        } catch (e: Exception) {
            log.error("SQL execution failed", e)
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
    private fun executeSqlWithJdbc(
        dataSourceInfo: SpringDataSourceResolver.DataSourceInfo,
        sqlStatements: List<String>
    ): ExecutionResult {
        val results = mutableListOf<String>()
        var successCount = 0
        var failedCount = 0

        DriverManager.getConnection(
            dataSourceInfo.url,
            dataSourceInfo.username,
            dataSourceInfo.password
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
                    log.info("SQL execution committed: $successCount statements")
                } else {
                    connection.rollback()
                    log.warn("SQL execution rolled back due to $failedCount failures")
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
     * 解析 SQL 语句
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
 * 执行结果
 */
data class ExecutionResult(
    val success: Boolean,
    val message: String,
    val details: String = "",
    val successCount: Int = 0,
    val failedCount: Int = 0
)

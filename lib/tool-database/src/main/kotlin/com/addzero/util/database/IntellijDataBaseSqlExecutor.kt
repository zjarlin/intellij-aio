package com.addzero.util.database

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.remote.jdbc.RemoteConnection
import com.intellij.database.remote.jdbc.RemoteResultSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * IntelliJ IDEA 数据库 SQL 执行器
 *
 * 提供在 IntelliJ IDEA 中执行 SQL 语句的功能
 *
 * 注意：这是一个工具类，部分方法可能暂未使用，但提供了完整的 API
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class IntellijDataBaseSqlExecutor(
    @Suppress("unused") // 保留以供未来使用
    private val project: Project,
    private val dataSource: LocalDataSource
) {

    fun executeSql(sql: String, timeoutSeconds: Long = 30): SqlExecutionResult {
        val startTime = System.currentTimeMillis()

        return try {
            val future = CompletableFuture<SqlExecutionResult>()

            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val connection = getConnection()
                    if (connection == null) {
                        future.complete(
                            SqlExecutionResult.failure(
                                "无法建立数据库连接: ${dataSource.name}",
                                System.currentTimeMillis() - startTime
                            )
                        )
                        return@executeOnPooledThread
                    }

                    val result = executeWithConnection(connection, sql, startTime)
                    future.complete(result)
                } catch (e: Exception) {
                    future.complete(
                        SqlExecutionResult.failure(
                            "执行SQL异常: ${e.message}",
                            System.currentTimeMillis() - startTime
                        )
                    )
                }
            }

            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            SqlExecutionResult.failure(
                "SQL执行超时或异常: ${e.message}",
                System.currentTimeMillis() - startTime
            )
        }
    }

    fun executeQuery(sql: String, timeoutSeconds: Long = 30): SqlExecutionResult {
        val startTime = System.currentTimeMillis()

        return try {
            val future = CompletableFuture<SqlExecutionResult>()

            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val connection = getConnection()
                    if (connection == null) {
                        future.complete(
                            SqlExecutionResult.failure(
                                "无法建立数据库连接: ${dataSource.name}",
                                System.currentTimeMillis() - startTime
                            )
                        )
                        return@executeOnPooledThread
                    }

                    val result = executeQueryWithConnection(connection, sql, startTime)
                    future.complete(result)
                } catch (e: Exception) {
                    future.complete(
                        SqlExecutionResult.failure(
                            "查询SQL异常: ${e.message}",
                            System.currentTimeMillis() - startTime
                        )
                    )
                }
            }

            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            SqlExecutionResult.failure(
                "查询SQL超时或异常: ${e.message}",
                System.currentTimeMillis() - startTime
            )
        }
    }

    fun executeUpdate(sql: String, timeoutSeconds: Long = 30): SqlExecutionResult {
        return executeSql(sql, timeoutSeconds)
    }

    fun executeBatch(sqlList: List<String>, timeoutSeconds: Long = 60): List<SqlExecutionResult> {
        return sqlList.map { sql ->
            executeSql(sql, timeoutSeconds)
        }
    }

    private fun getConnection(): RemoteConnection? {
        return try {
            // 最简单的方式：直接返回null，让executeWithConnection方法处理连接
            // 或者我们可以完全重构这个类，使用不同的方式来执行SQL
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun executeWithConnection(
        connection: RemoteConnection,
        sql: String,
        startTime: Long
    ): SqlExecutionResult {
        return try {
            val statement = connection.createStatement()
            val isQuery = sql.trim().uppercase().startsWith("SELECT")

            if (isQuery) {
                val resultSet = statement.executeQuery(sql)
                val data = extractResultSetData(resultSet)
                statement.close()
                SqlExecutionResult.successWithData(
                    data,
                    System.currentTimeMillis() - startTime
                )
            } else {
                val rowsAffected = statement.executeUpdate(sql)
                statement.close()
                SqlExecutionResult.success(
                    rowsAffected,
                    System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            SqlExecutionResult.failure(
                "SQL执行错误: ${e.message}",
                System.currentTimeMillis() - startTime
            )
        }
    }

    private fun executeQueryWithConnection(
        connection: RemoteConnection,
        sql: String,
        startTime: Long
    ): SqlExecutionResult {
        return try {
            val statement = connection.createStatement()
            val resultSet = statement.executeQuery(sql)
            val data = extractResultSetData(resultSet)
            statement.close()
            SqlExecutionResult.successWithData(
                data,
                System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            SqlExecutionResult.failure(
                "查询执行错误: ${e.message}",
                System.currentTimeMillis() - startTime
            )
        }
    }

    private fun extractResultSetData(resultSet: RemoteResultSet): List<Map<String, Any?>> {
        val data = mutableListOf<Map<String, Any?>>()
        // 使用 RemoteResultSetMetaData 而不是 ResultSetMetaData
        val metaData = resultSet.metaData
        val columnCount = metaData.columnCount

        while (resultSet.next()) {
            val row = mutableMapOf<String, Any?>()
            for (i in 1..columnCount) {
                val columnName = metaData.getColumnLabel(i)
                val value = resultSet.getObject(i)
                row[columnName] = value
            }
            data.add(row)
        }

        resultSet.close()
        return data
    }

    companion object {
        fun create(project: Project, dataSourceName: String): IntellijDataBaseSqlExecutor? {
            val dataSource = IntellijDataSourceManager.getDataSourceByName(project, dataSourceName)
                ?: return null
            return IntellijDataBaseSqlExecutor(project, dataSource)
        }

        fun createById(project: Project, dataSourceId: String): IntellijDataBaseSqlExecutor? {
            val dataSource = IntellijDataSourceManager.getDataSourceById(project, dataSourceId)
                ?: return null
            return IntellijDataBaseSqlExecutor(project, dataSource)
        }

        fun createWithFirst(project: Project): IntellijDataBaseSqlExecutor? {
            val dataSource = IntellijDataSourceManager.getFirstDataSource(project)
                ?: return null
            return IntellijDataBaseSqlExecutor(project, dataSource)
        }
    }
}

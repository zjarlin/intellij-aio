//package com.addzero.addl.util
//
//import com.intellij.database.dataSource.DatabaseConnectionCore
//import com.intellij.database.dataSource.DatabaseConnectionManager
//import com.intellij.database.psi.DbDataSource
//import com.intellij.database.remote.jdbc.RemoteResultSet
//import com.intellij.database.util.DbImplUtil
//
///**
// * 数据库操作工具类
// */
//object DatabaseUtil {
//    /**
//     * 执行查询并处理结果
//     */
//    fun <T> executeQuery(
//        dataSource: DbDataSource,
//        sql: String,
//        handler: (rs: RemoteResultSet) -> T,
//    ): T {
//        val localDataSource = DbImplUtil.getMaybeLocalDataSource(dataSource)
//        val build = localDataSource?.let { DatabaseConnectionManager.getInstance().build(dataSource.project, it) }
//        val create = build?.setAskPassword(false)?.create()
//        return create.use { connectionRef ->
//            val connection = connectionRef?.get()
//            DbImplUtil.executeAndGetResult(connection, sql, handler)
//        }
//    }
//
//    fun <T> executeQueries(
//        dataSource: DbDataSource,
//        queries: List<QueryHandler<T>>,
//    ): List<T>? {
//        val localDataSource = DbImplUtil.getMaybeLocalDataSource(dataSource)
//
//        val create = localDataSource?.let { DatabaseConnectionManager.getInstance().build(dataSource.project, it).setAskPassword(false).create() }
//        val use = create?.use { connectionRef ->
//            val connection = connectionRef.get()
//            queries.map { query ->
//                DbImplUtil.executeAndGetResult(
//                    connection, query.sqlBuilder.build(), query.handler
//                )
//            }
//        }
//        return use
//    }
//
//    /**
//     * 在连接上执行操作
//     */
//    fun <T> withConnection(
//        dataSource: DbDataSource,
//        action: (connection: DatabaseConnectionCore) -> T,
//    ): T? {
//        val localDataSource = DbImplUtil.getMaybeLocalDataSource(dataSource)
//
//        val use = localDataSource?.let {
//            DatabaseConnectionManager.getInstance().build(dataSource.project, it).setAskPassword(false).create()?.use { connectionRef ->
//                action(connectionRef.get())
//            }
//        }
//        return use
//    }
//}
//
///**
// * SQL 构建器
// */
//class SqlBuilder {
//    private val conditions = mutableListOf<String>()
//    private val params = mutableMapOf<String, Any?>()
//    private var baseQuery = ""
//
//    fun setBaseQuery(sql: String): SqlBuilder {
//        baseQuery = sql
//        return this
//    }
//
//    fun addCondition(condition: String): SqlBuilder {
//        conditions.add(condition)
//        return this
//    }
//
//    fun addParam(key: String, value: Any?): SqlBuilder {
//        params[key] = value
//        return this
//    }
//
//    fun build(): String {
//        val whereClause = if (conditions.isNotEmpty()) {
//            " WHERE " + conditions.joinToString(" AND ")
//        } else ""
//
//        return baseQuery + whereClause
//    }
//}
//
///**
// * 查询处理器
// */
//data class QueryHandler<T>(
//    val sqlBuilder: SqlBuilder,
//    val handler: (rs: RemoteResultSet) -> T,
//) {
//    companion object {
//        fun <T> create(
//            baseQuery: String,
//            handler: (rs: RemoteResultSet) -> T,
//        ): QueryHandler<T> {
//            return QueryHandler(
//                SqlBuilder().setBaseQuery(baseQuery), handler
//            )
//        }
//    }
//}
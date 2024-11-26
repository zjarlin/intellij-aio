//package com.addzero.addl.action.autoddlwithdb.scanner
//
//import com.intellij.database.dataSource.DatabaseConnection
//import com.intellij.database.dataSource.DatabaseConnectionPoint
//import com.intellij.database.dialects.oracle.debugger.executor
//import com.intellij.database.psi.DbPsiFacade
//import com.intellij.database.util.DbImplUtil
//import com.intellij.openapi.project.Project
//
//object SqlExecutor {
//    /**
//     * 执行SQL查询
//     */
//    fun executeQuery(
//        project: Project,
//        dataSource: DatabaseConnectionPoint,
//        sql: String
//    ): List<Map<String, Any?>> {
//        val connection = getConnection(dataSource)
//        return try {
//
//            connection.executor().execute(,)
//
//
//            connection.executeQuery(sql) { resultSet ->
//                val results = mutableListOf<Map<String, Any?>>()
//                val metaData = resultSet.metaData
//                val columnCount = metaData.columnCount
//
//                while (resultSet.next()) {
//                    val row = mutableMapOf<String, Any?>()
//                    for (i in 1..columnCount) {
//                        val columnName = metaData.getColumnName(i)
//                        val value = resultSet.getObject(i)
//                        row[columnName] = value
//                    }
//                    results.add(row)
//                }
//                results
//            }
//        } finally {
//            connection.close()
//        }
//    }
//
//    /**
//     * 执行更新SQL（INSERT, UPDATE, DELETE等）
//     */
//    fun executeUpdate(
//        project: Project,
//        dataSource: DatabaseConnectionPoint,
//        sql: String
//    ): Int {
//        val connection = getConnection(dataSource)
//        return try {
//            connection.executeUpdate(sql)
//        } finally {
//            connection.close()
//        }
//    }
//
//    /**
//     * 获取数据库连接
//     */
//    private fun getConnection(dataSource: DatabaseConnectionPoint): DatabaseConnection {
//        return DbImplUtil.getConnection(dataSource)
//            ?: throw IllegalStateException("无法获取数据库连接")
//    }
//
//    /**
//     * 获取当前选中的数据源
//     */
//    fun getCurrentDataSource(project: Project): DatabaseConnectionPoint? {
//        return DbPsiFacade.getInstance(project).dataSources.firstOrNull()
//    }
//}
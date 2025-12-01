package com.addzero.util.database

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project

fun LocalDataSource.toConnectionInfo(): DatabaseConnectionInfo = DatabaseConnectionInfo(
    jdbcUrl = this.url ?: "",
    username = this.username,
    password = null, // 密码需要通过 CredentialStore 获取
    driverClass = this.driverClass
)

val LocalDataSource.dialect: String
    get() = DialectInferrer.inferFromJdbcUrl(this.url)

fun Project.getDefaultDialect(): String {
    val firstDataSource = IntellijDataSourceManager.getFirstDataSource(this)
    return firstDataSource?.dialect ?: "mysql"
}

fun Project.getConnectionInfo(dataSourceName: String? = null): DatabaseConnectionInfo? {
    val dataSource = if (dataSourceName != null) {
        IntellijDataSourceManager.getDataSourceByName(this, dataSourceName)
    } else {
        IntellijDataSourceManager.getFirstDataSource(this)
    }
    return dataSource?.toConnectionInfo()
}

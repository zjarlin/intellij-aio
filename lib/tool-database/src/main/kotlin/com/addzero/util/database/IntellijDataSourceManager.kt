package com.addzero.util.database

import com.intellij.database.dataSource.DataSourceStorage
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.database.util.DbImplUtilCore
import com.intellij.openapi.project.Project

object IntellijDataSourceManager {
    
    fun getAllDataSources(project: Project): List<LocalDataSource> {
        return DataSourceStorage.getProjectStorage(project)?.dataSources?.toList() ?: emptyList()
    }

    fun getDataSourceByName(project: Project, name: String): LocalDataSource? {
        return getAllDataSources(project).firstOrNull { it.name == name }
    }

    fun getDataSourceById(project: Project, uniqueId: String): LocalDataSource? {
        return getAllDataSources(project).firstOrNull { it.uniqueId == uniqueId }
    }

    fun getFirstDataSource(project: Project): LocalDataSource? {
        return getAllDataSources(project).firstOrNull()
    }

    fun getDbDataSource(project: Project, localDataSource: LocalDataSource): DbDataSource? {
        return DbImplUtilCore.getDbDataSource(localDataSource)
    }

    fun getDataSourceNames(project: Project): List<String> {
        return getAllDataSources(project).map { it.name }
    }

    fun hasDataSources(project: Project): Boolean {
        return getAllDataSources(project).isNotEmpty()
    }
}

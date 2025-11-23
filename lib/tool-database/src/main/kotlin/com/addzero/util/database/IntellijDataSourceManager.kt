package com.addzero.util.database

import com.intellij.database.dataSource.DataSourceStorage
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.openapi.project.Project

object IntellijDataSourceManager {
    
    fun getAllDataSources(project: Project): List<LocalDataSource> =
        DataSourceStorage.getProjectStorage(project)?.dataSources?.toList() ?: emptyList()

    fun getDataSourceByName(project: Project, name: String): LocalDataSource? =
        getAllDataSources(project).firstOrNull { it.name == name }

    fun getDataSourceById(project: Project, uniqueId: String): LocalDataSource? =
        getAllDataSources(project).firstOrNull { it.uniqueId == uniqueId }

    fun getFirstDataSource(project: Project): LocalDataSource? =
        getAllDataSources(project).firstOrNull()

    fun getDbDataSource(project: Project, localDataSource: LocalDataSource): DbDataSource? =
        DbPsiFacade.getInstance(project).dataSources.firstOrNull { it.name == localDataSource.name }

    fun getDataSourceNames(project: Project): List<String> =
        getAllDataSources(project).map { it.name }

    fun hasDataSources(project: Project): Boolean =
        getAllDataSources(project).isNotEmpty()
}

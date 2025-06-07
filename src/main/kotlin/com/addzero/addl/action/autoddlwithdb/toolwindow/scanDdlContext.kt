package com.addzero.addl.action.autoddlwithdb.toolwindow

import com.addzero.addl.action.autoddlwithdb.scanner.findJavaEntityClasses
import com.addzero.addl.action.autoddlwithdb.scanner.findktEntityClasses
import com.addzero.addl.autoddlstarter.generator.entity.DDLContext
import com.addzero.addl.autoddlstarter.generator.factory.DDLContextFactory4JavaMetaInfo
import com.addzero.addl.ctx.isKotlinProject
import com.addzero.addl.settings.SettingContext
import com.intellij.openapi.project.Project

fun scanDdlContext(project: Project): List<DDLContext> {
    val dbType = SettingContext.settings.dbType
    //        val scanPkg = ""

    val kotlinProject = isKotlinProject(project)
    val ddlContexts = if (kotlinProject) {

        val findAllEntityClasses = findktEntityClasses(project)
        val map = findAllEntityClasses.map {
            val createDDLContext = DDLContextFactory4JavaMetaInfo.createDDLContext4KtClass(it, dbType)
            createDDLContext
        }
        map
    } else {
        val findJavaEntityClasses = findJavaEntityClasses(project)
        val map = findJavaEntityClasses.map {
            val createDDLContext = DDLContextFactory4JavaMetaInfo.createDDLContext(it, dbType)
            createDDLContext
        }
        map
    }

    val toList = ddlContexts.map {
        val tableEnglishName = it.tableEnglishName
        val removeSurrounding = tableEnglishName.removeSurrounding("\"")
        it.tableEnglishName = removeSurrounding
        it
    }.toList()
    return toList
}

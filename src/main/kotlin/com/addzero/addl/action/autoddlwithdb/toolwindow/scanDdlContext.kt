package com.addzero.addl.action.autoddlwithdb.toolwindow

import com.addzero.addl.action.autoddlwithdb.scanner.findJavaEntityClasses
import com.addzero.addl.action.autoddlwithdb.scanner.findktEntityClasses
import com.addzero.addl.autoddlstarter.generator.entity.DDLContext
import com.addzero.addl.autoddlstarter.generator.factory.DDLContextFactory4JavaMetaInfo
import com.addzero.addl.ctx.isKotlinProject
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.fieldinfo.K2PropertyUtil
import com.addzero.addl.util.removeAnyQuote
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty

fun scanDdlContext(project: Project): List<DDLContext> {
    val dbType = SettingContext.settings.dbType
    //        val scanPkg = ""

    val kotlinProject = isKotlinProject(project)
    val ddlContexts = if (kotlinProject) {

        val findAllEntityClasses = findktEntityClasses(project)
        //处理多对多中间表的生成
//        val aaad= genMany2ManyDDLContext(findAllEntityClasses)

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

data class ManyToManyDLContext4kTRes(val mapperedBy: String?, val ktProperty: KtProperty, val ktClass: KtClass)

fun genMany2ManyDDLContext(findAllEntityClasses: List<KtClass>): Map<String, String> {

    val ret = mutableMapOf<String, String>()
//    val databaseType = SettingContext.settings.dbType
//    val idType = SettingContext.settings.idType


    val map = findAllEntityClasses.filter {
        val filterPropertiesByAnnotations = K2PropertyUtil.filterPropertiesByAnnotations2(it, listOf("ManyToMany"))
        filterPropertiesByAnnotations.isNotEmpty()
    }.flatMap { ktcla ->
        val filterPropertiesByAnnotations = K2PropertyUtil.filterPropertiesByAnnotations2(ktcla, listOf("ManyToMany"))

        filterPropertiesByAnnotations.map {
            ManyToManyDLContext4kTRes(null, it, ktcla)
        }.toList()


    }
    if (map.isEmpty()) {
        return emptyMap<String, String>()
    }
    val dict = map.associate { it.ktProperty.name to it.ktClass }

        map.forEach {
            val ktClass = it.ktClass
            val ktProperty = it.ktProperty
            val propertyAnnotationInfo = K2PropertyUtil.getPropertyAnnotationInfo2(ktProperty)
            val toList = propertyAnnotationInfo.flatMap { it.arguments.filter { it.name == "mappedBy" } }.toList()
            if (toList.isEmpty()) {
                return emptyMap<String, String>()
            }
            toList.forEach {
                val value = it.value.removeAnyQuote()
                val zuobianKlass = dict[value]
    //            ret[zuobianKlass?.name ?: ""] = ktClass.name ?: ""
                ret[zuobianKlass?.name ?: ""] = ktClass.name ?: ""

            }

        }
    return ret
//    val toList = ret.entries.map {
//        val key = it.key
//        val value = it.value
//
//        val tablename = "${value}_${key}_mapping"
//
//        val zuobianId = "${value}_id"
//        val zhongjianId = "${key}_id"
//        val listOf = listOf(
//            DDlRangeContext(
//                colName = zuobianId,
//                colType = "mysql",
//                colComment = "",
//                colLength = "",
//                isPrimaryKey = "",
//                isSelfIncreasing = ""
//            ), DDlRangeContext(
//                colName = zhongjianId,
//                colType = "bigint",
//                colComment = "",
//                colLength = "",
//                isPrimaryKey = "",
//                isSelfIncreasing = ""
//            )
//
//
//        )
//
//        val ddlContext = DDLContext(
//            tableChineseName = tablename, tableEnglishName = tablename, databaseType = "mysql", dto = listOf
//        )
//        ddlContext
//
//
//    }.toList()
//    return toList
}

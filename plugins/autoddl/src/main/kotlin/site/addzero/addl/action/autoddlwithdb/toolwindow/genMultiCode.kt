package site.addzero.addl.action.autoddlwithdb.toolwindow

import cn.hutool.core.util.IdUtil
import site.addzero.addl.autoddlstarter.generator.IDatabaseGenerator
import site.addzero.addl.autoddlstarter.generator.entity.DDLContext
import site.addzero.addl.ktututil.toJson
import site.addzero.addl.settings.SettingContext
import site.addzero.addl.util.DialogUtil
import site.addzero.addl.util.ShowContentUtil
import com.intellij.openapi.project.Project

 fun genMultiCode(
    contexts: List<DDLContext>,
    project: Project
) {
//    val jimmermetaPath = "${project.basePath}/.autoddl/meta"
     val entityDdlContextMetaJsonPath = "${project.basePath}/${SettingContext.settings.entityDdlContextMetaJsonPath}"
    val filePath = "${project.basePath}/${SettingContext.settings.flaywayPath}"

    val databaseDDLGenerator = IDatabaseGenerator.Companion.getDatabaseDDLGenerator(SettingContext.settings.dbType)
    //生成json文件
    ShowContentUtil.genCode(
        project, contexts.toJson(),
        "entity_ddlcontext",
        ".json",
        filePath = entityDdlContextMetaJsonPath
    )
    DialogUtil.showInfoMsg("已生成实体的ddl上下文json,目录为$entityDdlContextMetaJsonPath")


    contexts.forEach {
        val generateCreateTableDDL = databaseDDLGenerator.generateCreateTableDDL(it)

        val generateAddColDDL = databaseDDLGenerator.generateAddColDDL(it)

        val tableEnglishName = it.tableEnglishName

        val suffix = "init"
        val suffixAlter = "alter"
        ShowContentUtil.genCode(
            project, generateCreateTableDDL,
            "V${IdUtil.getSnowflakeNextIdStr()}__${tableEnglishName}_$suffix",
            ".sql",
            filePath = "$filePath/$suffix"
        )

//            ShowContentUtil.openTextInEditor(
//                project, generateCreateTableDDL,
//                "V${System.currentTimeMillis()}__${tableEnglishName}_create",
//                ".sql",
//                filePath = "$filePath/create"
//
//            )
        ShowContentUtil.genCode(
            project, generateAddColDDL,
            "V${IdUtil.getSnowflakeNextIdStr()}__${tableEnglishName}_$suffixAlter",
            ".sql",
            filePath = "$filePath/$suffixAlter"
        )


    }
    DialogUtil.showInfoMsg("生成了${contexts.size}张表的ddl")


}

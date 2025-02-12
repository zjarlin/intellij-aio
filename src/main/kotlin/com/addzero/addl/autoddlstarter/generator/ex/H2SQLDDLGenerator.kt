package com.addzero.addl.autoddlstarter.generator.ex

import cn.hutool.core.util.StrUtil
import com.addzero.addl.autoddlstarter.generator.DatabaseDDLGenerator
import com.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.fieldMappings
import com.addzero.addl.autoddlstarter.generator.entity.DDLContext
import com.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo
import com.addzero.addl.autoddlstarter.generator.filterBaseEneity
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.JlStrUtil

class H2SQLDDLGenerator : DatabaseDDLGenerator() {
    override fun generateCreateTableDDL(ddlContext: DDLContext): String {
        val tableEnglishName = ddlContext.tableEnglishName
        val tableChineseName = ddlContext.tableChineseName
        val dto = ddlContext.dto

        var cols = """        ${
            dto.joinToString(System.lineSeparator()) {
                """
                    ${it.colName} ${it.colType}  ${it.colLength},
                """.trimIndent()
            }
        }
"""

        cols = JlStrUtil.removeLastCharOccurrence(cols, ',')

        val settings = SettingContext.settings
        val id = settings.id
        val createBy = settings.createBy
        val updateBy = settings.updateBy
        val createTime = settings.createTime
        val updateTime = settings.updateTime
        val idType = settings.idType

        val createTableSQL = """
    create table "$tableEnglishName" (
        $id $idType primary key,
        $createBy $idType ,
        $updateBy $idType ,
        $createTime timestamp ,
        $updateTime timestamp ,
$cols       
    );
    comment on table "$tableEnglishName" is '$tableChineseName';
    """.trimIndent()

        // 添加字段注释
        val comments = dto
            .distinctBy { it.colName }
            .filter { filterBaseEneity(it) }
            .joinToString(System.lineSeparator()) {
                """
                comment on column $tableEnglishName.${it.colName} is '${it.colComment}';
                """.trimIndent()
            }

        val baseComments = """
            comment on column $tableEnglishName.$id is '主键';
            comment on column $tableEnglishName.$createBy is '创建者';
            comment on column $tableEnglishName.$createTime is '创建时间';
            comment on column $tableEnglishName.$updateBy is '更新者';
            comment on column $tableEnglishName.$updateTime is '更新时间';
        """.trimIndent()

        return createTableSQL + System.lineSeparator() + baseComments + System.lineSeparator() + comments
    }

    override fun generateAddColDDL(ddlContext: DDLContext): String {
        val (tableChineseName, tableEnglishName, databaseType, databaseName, dto) = ddlContext
        val dmls = dto.joinToString(System.lineSeparator()) {

            // 如果 databaseName 不为空，则拼接成 databaseName.tableEnglishName
            val tableRef = if (databaseName.isBlank()) {
                JlStrUtil.makeSurroundWith(tableEnglishName, "\"") 
            } else {
                "\"$databaseName\".\"$tableEnglishName\""
            }
            // 生成 ALTER 语句以及字段注释
            val upperCaseColName = StrUtil.toUnderlineCase(it.colName)
            """
            alter table $tableRef add column "$upperCaseColName" ${it.colType}${it.colLength};
            comment on column $tableRef."$upperCaseColName" is '${it.colComment}';
        """.trimIndent()
        }

        return dmls
    }

    override fun mapTypeByMysqlType(mysqlType: String): String {
        return fieldMappings.find { it.mysqlType.equals(mysqlType, ignoreCase = true) }?.h2Type!!
    }

    override fun mapTypeByJavaType(javaFieldMetaInfo: JavaFieldMetaInfo): String {
        return fieldMappings.find { it.predi.test(javaFieldMetaInfo) }?.h2Type!!
    }
}
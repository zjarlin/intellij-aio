package com.addzero.addl.autoddlstarter.generator.ex

import cn.hutool.core.collection.CollUtil
import cn.hutool.core.util.StrUtil
import com.addzero.addl.autoddlstarter.generator.DatabaseDDLGenerator
import com.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.fieldMappings
import com.addzero.addl.autoddlstarter.generator.entity.DDLContext
import com.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo
import com.addzero.addl.autoddlstarter.generator.filterBaseEneity
import com.addzero.addl.ktututil.toUnderlineCase
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.JlStrUtil
import com.addzero.addl.util.JlStrUtil.ignoreCaseNotIn


class MysqlDDLGenerator : DatabaseDDLGenerator() {
    override fun generateCreateTableDDL(ddlContext: DDLContext): String {
        val tableEnglishName = ddlContext.tableEnglishName
        val tableChineseName = ddlContext.tableChineseName
        val dto = ddlContext.dto
        val settings = SettingContext.settings
        val id = settings.id
        val createBy = settings.createBy
        val updateBy = settings.updateBy
        val createTime = settings.createTime
        val updateTime = settings.updateTime

        val createTableSQL = """
    create table `$tableEnglishName` (
        `$id` varchar(64) not null ,
        `$createBy` varchar(255) not null comment '创建者',
        `$updateBy` varchar(255) null comment '更新者',
        `$createTime` datetime not null default current_timestamp comment '创建时间',
        `$updateTime` datetime null default current_timestamp on update current_timestamp comment '更新时间',
        ${
            dto

                .filter { filterBaseEneity(it) }
//                .filter { it.colName ignoreCaseNotIn listOf(id, createBy, updateBy, createTime, updateTime) }
            .joinToString(System.lineSeparator()) {
                val colLength = it.colLength
                """
                       `${it.colName.toUnderlineCase()}` ${it.colType}    $colLength    comment '${it.colComment}' ,
                """.trimIndent()
            }
        }
        primary key (`id`)
    ) engine=innodb default charset=utf8mb4
     comment = '${tableChineseName}'; 
""".trimIndent()
        return createTableSQL
    }


    override fun generateAddColDDL(ddlContext: DDLContext): String {
        val (tableChineseName, tableEnglishName, databaseType, databaseName, dto) = ddlContext
        val dmls = dto

        .joinToString(System.lineSeparator()) {

            // 如果 databaseName 不为空，则拼接成 databaseName.tableEnglishName
            val tableRef = if (databaseName.isBlank()) {
                JlStrUtil.makeSurroundWith(tableEnglishName, "`")
            } else {
                "`$databaseName`.`$tableEnglishName`"
            }
            // 生成 ALTER 语句以及字段注释
            val toUnderlineCase = StrUtil.toUnderlineCase(it.colName)

//            alter table biz_env_vars add 列_name int null comment 'cadsca';

            """
            alter table $tableRef add column `$toUnderlineCase` ${it.colType} ${it.colLength}  comment '${it.colComment}';
        """.trimIndent()
        }

        return dmls
    }


    override fun mapTypeByMysqlType(mysqlType: String): String {
        return fieldMappings.find { it.mysqlType.equals(mysqlType, ignoreCase = true) }?.mysqlType!!
    }


    override fun mapTypeByJavaType(javaFieldMetaInfo: JavaFieldMetaInfo): String {
        return fieldMappings.find { it.predi.test(javaFieldMetaInfo) }?.mysqlType!!

    }
}
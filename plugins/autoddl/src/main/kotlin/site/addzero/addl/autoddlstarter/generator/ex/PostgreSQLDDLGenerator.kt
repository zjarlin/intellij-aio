package site.addzero.addl.autoddlstarter.generator.ex

import cn.hutool.core.util.StrUtil
import site.addzero.addl.autoddlstarter.generator.DatabaseDDLGenerator
import site.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.fieldMappings
import site.addzero.addl.autoddlstarter.generator.entity.DDLContext
import site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo
import site.addzero.addl.autoddlstarter.generator.filterBaseEneity
import site.addzero.addl.settings.SettingContext
import site.addzero.addl.util.JlStrUtil

class PostgreSQLDDLGenerator : DatabaseDDLGenerator() {

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

        var colsComments = """        ${
            dto
                .distinctBy { it.colName }

                .filter { filterBaseEneity(it) }
//                .filter { it.colName ignoreCaseNotIn listOf(id, createBy, updateBy, createTime, updateTime) }

//                .filter { it.colName !in listOf(id, createBy, updateBy, createTime, updateTime) }
                .joinToString(System.lineSeparator()) {
                    """
 fieldComment on column $tableEnglishName.${it.colName} is '${it.colComment}'; 
                """.trimIndent()
                }
        }
"""

        val createTableSQL = """
    create table if not exists "$tableEnglishName" (
        $id $idType primary key,
        $createBy $idType ,
        $updateBy $idType ,
        $createTime timestamp ,
        $updateTime timestamp ,
$cols       
    );
    fieldComment on table "$tableEnglishName" is '$tableChineseName';
 ${
            """
            fieldComment on column $tableEnglishName.$id is '主键';
            fieldComment on column $tableEnglishName.$createBy is '创建者';
            fieldComment on column $tableEnglishName.$createTime is '创建时间';
            fieldComment on column $tableEnglishName.$updateBy is '更新者';
            fieldComment on column $tableEnglishName.$updateTime is '更新时间'; 
            """.trimIndent()
        }
    $colsComments
""".trimIndent()

        return createTableSQL
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
            alter table $tableRef add column "$upperCaseColName" ${it.colType}${it.colLength}; fieldComment on column $tableRef."$upperCaseColName" is '${it.colComment}';
        """.trimIndent()
        }

        return dmls
    }

    override fun mapTypeByMysqlType(mysqlType: String): String {
        val pgType = fieldMappings.find { it.mysqlType.equals(mysqlType, ignoreCase = true) }?.pgType
        return pgType!!
    }

    override fun mapTypeByJavaType(javaFieldMetaInfo: JavaFieldMetaInfo): String {
        return fieldMappings.find { it.predi.test(javaFieldMetaInfo) }?.pgType!!

    }

}

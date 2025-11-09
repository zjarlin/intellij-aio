package site.addzero.addl

import site.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.getDatabaseDDLGenerator
import site.addzero.addl.autoddlstarter.generator.entity.DDLRangeContextUserInput
import site.addzero.addl.autoddlstarter.generator.factory.DDLContextFactory4UserInputMetaInfo
import site.addzero.util.ShowContentUtil.openTextInEditor
import com.alibaba.fastjson2.JSON
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class AutoDDL : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project

        // 显示表单弹窗
        val form = AutoDDLForm(project)
        if (form.showAndGet()) {

            // 确保表格的编辑内容已经保存
            if (form.fieldsTable!!.cellEditor != null) {
                form.fieldsTable!!.cellEditor.stopCellEditing()
            }

            // 表单被提交，生成DDL
            val formDTO = form.formDTO
            formDTO.fields = formDTO.fields.filter {
                !it.javaType.isNullOrBlank() && !it.fieldChineseName.isNullOrBlank()
            }
            val ddlResult = genDDL(formDTO)
            // 使用 IntelliJ 内置的 SQL 编辑器显示 SQL 语句
//         ShowSqlUtil.   showDDLInTextField(project, ddlResult)
            project.openTextInEditor(
                ddlResult,
                "create_table_${formDTO.tableEnglishName}",
                ".sql",
                project!!.basePath
            )
        }
    }


}

private fun genDDL(formDTO: FormDTO): String {
    val (tableName, tableEnglishName, dbType, dbName, fields) = formDTO
    val map = fields.map {

        DDLRangeContextUserInput(it.javaType, it.fieldName, it.fieldChineseName)
    }
    //用户输入元数据工厂构建方法
    val createDDLContext =
        DDLContextFactory4UserInputMetaInfo.createDDLContext(tableEnglishName, tableName, dbType, map)
    //未来加入实体元数据抽取工厂
    val databaseDDLGenerator = getDatabaseDDLGenerator(dbType)
    val generateCreateTableDDL = databaseDDLGenerator.generateCreateTableDDL(createDDLContext)
    return generateCreateTableDDL
}


fun main() {
    val trimIndent = """
       {
  "dbName" : "示例数据库名称",
  "dbType" : "mysql",
  "fields" : [ {
    "fieldChineseName" : "字段注释",
    "fieldName" : "字段名",
    "javaType" : "String"
  } ],
  "tableEnglishName" : "示例英文名",
  "tableName" : "示例表名"
} 
    """.trimIndent()
    val parseObject = JSON.parseObject(trimIndent, FormDTO::class.java)
    val genDDL = genDDL(parseObject)
    println(genDDL)
}

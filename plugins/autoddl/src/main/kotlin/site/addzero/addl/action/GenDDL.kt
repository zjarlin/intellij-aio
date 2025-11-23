package site.addzero.addl.action

import site.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.getDatabaseDDLGenerator
import site.addzero.addl.autoddlstarter.generator.entity.DDLContext
import site.addzero.addl.autoddlstarter.generator.factory.DDLContextFactory4JavaMetaInfo.createDDLContext
import site.addzero.addl.settings.MyPluginSettingsService
import site.addzero.util.ShowContentUtil.openTextInEditor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import site.addzero.addl.action.anycodegen.util.toLsiClass
import site.addzero.addl.util.PsiValidateUtil
import site.addzero.util.lsi_impl.impl.intellij.context.lsiContext


class GenDDL : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val context = project.lsiContext()

        // 检查是否有有效的类（POJO或Jimmer实体）
        e.presentation.isEnabled = context.hasValidClass
    }


    override fun actionPerformed(e: AnActionEvent) {
        // 获取当前项目和编辑器上下文
        val project: Project = e.project ?: return
        val context = project.lsiContext()
        
        val lsiClass = context.currentClass ?: return

        // 使用LSI生成DDL上下文
        val ddlContext = generateDDLContextFromLsiClass(lsiClass)


        val databaseDDLGenerator = getDatabaseDDLGenerator(defaultDbType())

        val generateCreateTableDDL = databaseDDLGenerator.generateCreateTableDDL(ddlContext)
        val sql = databaseDDLGenerator.generateAddColDDL(ddlContext)
        val lineSeparator = System.lineSeparator()
        val s = generateCreateTableDDL + lineSeparator + lineSeparator + sql

        // 将生成的 SQL 语句写入到新的文件并打开
        project.openTextInEditor(
            s,
            "alter_table_${ddlContext .tableEnglishName}",
            ".sql"
        )
    }

    // 根据 LsiClass 生成 DDLContext 对象
    private fun generateDDLContextFromLsiClass(lsiClass: site.addzero.util.lsi.clazz.LsiClass): DDLContext {
        val defaultDbType = defaultDbType()
        // 使用LSI工厂方法创建DDLContext
        return site.addzero.addl.autoddlstarter.generator.factory.DDLContextFactory4JavaMetaInfo.createDDLContextFromLsi(lsiClass, defaultDbType)
    }

    private fun defaultDbType(): String {
        val settings = MyPluginSettingsService.getInstance().state
        val defaultDbType = settings.dbType
        if (defaultDbType.isNullOrBlank()) {
            return "mysql"
        }
        return defaultDbType
    }


}

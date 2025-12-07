package site.addzero.addl.action

import site.addzero.addl.settings.MyPluginSettingsService
import site.addzero.util.ShowContentUtil.openTextInEditor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import site.addzero.util.lsi_impl.impl.intellij.context.lsiContext
import site.addzero.util.ddlgenerator.toCreateTableDDL
import site.addzero.util.lsi.clazz.guessTableName
import site.addzero.util.lsi.database.databaseFields
import site.addzero.util.ddlgenerator.toAddColumnDDL
import site.addzero.util.db.DatabaseType


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

        // 使用新的扩展函数API生成DDL
        val dbType = getDefaultDbType()
        val tableName = lsiClass.guessTableName
        
        // 生成CREATE TABLE DDL
        val createTableDDL = lsiClass.toCreateTableDDL(dbType)
        
        // 生成ADD COLUMN DDL
        val addColumnDDL = lsiClass.databaseFields.joinToString("\n\n") { field ->
            field.toAddColumnDDL(tableName, dbType)
        }
        
        val lineSeparator = System.lineSeparator()
        val sql = createTableDDL + lineSeparator + lineSeparator + addColumnDDL

        // 将生成的 SQL 语句写入到新的文件并打开
        project.openTextInEditor(
            sql,
            "alter_table_$tableName",
            ".sql"
        )
    }

    private fun getDefaultDbType(): DatabaseType {
        val settings = MyPluginSettingsService.getInstance().state
        val defaultDbType = settings.dbType
        return if (defaultDbType.isNullOrBlank()) {
            DatabaseType.MYSQL
        } else {
            try {
                DatabaseType.valueOf(defaultDbType.uppercase())
            } catch (e: IllegalArgumentException) {
                DatabaseType.MYSQL
            }
        }
    }


}

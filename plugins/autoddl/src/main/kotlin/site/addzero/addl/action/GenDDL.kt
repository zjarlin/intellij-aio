package site.addzero.addl.action

import site.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.getDatabaseDDLGenerator
import site.addzero.addl.autoddlstarter.generator.consts.MYSQL
import site.addzero.addl.autoddlstarter.generator.entity.DDLContext
import site.addzero.addl.autoddlstarter.generator.factory.DDLContextFactory4JavaMetaInfo.createDDLContext
import site.addzero.addl.autoddlstarter.generator.factory.DDLContextFactory4JavaMetaInfo.createDDLContext4KtClass
import site.addzero.addl.settings.MyPluginSettingsService
import site.addzero.util.ShowContentUtil.openTextInEditor
import site.addzero.util.psi.PsiUtil.psiCtx
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass


class GenDDL : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val (editor, psiClass, ktClass, psiFile, virtualFile, classPath) = (project ?: return).psiCtx()

        // 使用工具类检查是否为POJO或Jimmer实体
        val isValidTarget = PsiValidateUtil.isValidTarget(ktClass, psiClass)

        e.presentation.isEnabled =  isValidTarget.first
    }


    override fun actionPerformed(e: AnActionEvent) {
        // 获取当前项目和编辑器上下文
        val project: Project = e.project ?: return
        val (editor, psiClass, ktClass, psiFile, virtualFile, classPath) = project.psiCtx()

        val ddlContext = if (ktClass == null) {
            psiClass ?: return
            // 生成 DDLContext 并转化为 SQL 语句
            val generateDDLContextFromClass = generateDDLContextFromClass(psiClass)
            generateDDLContextFromClass
        } else {

            val createDDLContext4KtClass = createDDLContext4KtClass(ktClass)
            createDDLContext4KtClass
        }


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

    // 根据 PsiClass 生成 DDLContext 对象
    private fun generateDDLContextFromClass(psiClass: PsiClass): DDLContext {
        val defaultDbType = defaultDbType()
        val createDDLContext = createDDLContext(psiClass, defaultDbType)
        return createDDLContext
    }

    private fun defaultDbType(): String {
        val settings = MyPluginSettingsService.getInstance().state
        val defaultDbType = settings.dbType
        if (defaultDbType.isNullOrBlank()) {
            return MYSQL
        }
        return defaultDbType
    }


}

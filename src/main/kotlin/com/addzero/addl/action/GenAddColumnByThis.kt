package com.addzero.addl.action

import cn.hutool.core.util.StrUtil
import com.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.getDatabaseDDLGenerator
import com.addzero.addl.autoddlstarter.generator.consts.MYSQL
import com.addzero.addl.autoddlstarter.generator.entity.DDLContext
import com.addzero.addl.autoddlstarter.generator.factory.DDLContextFactory4JavaMetaInfo.createDDLContext
import com.addzero.addl.autoddlstarter.generator.factory.DDLContextFactory4JavaMetaInfo.createDDLContext4KtClass
import com.addzero.addl.settings.MyPluginSettings
import com.addzero.addl.settings.MyPluginSettingsService
import com.addzero.addl.util.ShowSqlUtil
import com.addzero.addl.util.fieldinfo.ktClass
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil


class GenAddColumnByThis : AnAction() {


    override fun actionPerformed(e: AnActionEvent) {
        // 获取当前项目和编辑器上下文
        val project: Project = e.project ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
//        val virtualFile = editor.virtualFile
        val virtualFile = FileEditorManager.getInstance(project).getSelectedEditor()?.file

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile!!) ?: return

        val ktClass = virtualFile.ktClass(project)

        val ddlContext = if (ktClass == null) {
            val psiClass = PsiTreeUtil.findChildOfType(psiFile, PsiClass::class.java) ?: return
            // 生成 DDLContext 并转化为 SQL 语句
            val generateDDLContextFromClass = generateDDLContextFromClass(psiClass)
            generateDDLContextFromClass
        } else {

            val createDDLContext4KtClass = createDDLContext4KtClass(ktClass)
            createDDLContext4KtClass
        }



        val databaseDDLGenerator = getDatabaseDDLGenerator(defaultDbType())
        val sql = databaseDDLGenerator.generateAddColDDL(ddlContext)
        // 将生成的 SQL 语句写入到新的文件并打开
        ShowSqlUtil.openSqlInEditor(project, sql, "alter_table_${ddlContext.tableEnglishName}")
    }

    // 根据 PsiClass 生成 DDLContext 对象
    private fun generateDDLContextFromClass(psiClass: PsiClass): DDLContext {
        val defaultDbType = defaultDbType()
        val createDDLContext = createDDLContext(psiClass, defaultDbType)
        return createDDLContext
    }

    private fun defaultDbType(): String {
        val settings =  MyPluginSettingsService.getInstance().state
        val defaultDbType = settings.dbType
        if (StrUtil.isBlank(defaultDbType)) {
            return MYSQL
        }
        return defaultDbType
    }


}
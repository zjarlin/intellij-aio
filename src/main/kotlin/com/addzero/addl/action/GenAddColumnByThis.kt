package com.addzero.addl.action

import cn.hutool.core.util.StrUtil
import com.addzero.addl.autoddlstarter.generator.IDatabaseGenerator.Companion.getDatabaseDDLGenerator
import com.addzero.addl.autoddlstarter.generator.consts.MYSQL
import com.addzero.addl.autoddlstarter.generator.entity.DDLContext
import com.addzero.addl.autoddlstarter.generator.factory.DDLContextFactory4JavaMetaInfo.createDDLContext
import com.addzero.addl.util.ShowSqlUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil

class GenAddColumnByThis : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        // 获取当前项目和编辑器上下文
        val project: Project = e.project ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val document = editor.document
//        val virtualFile = editor.virtualFile
        val virtualFile = FileEditorManager.getInstance(project).getSelectedEditor()?.file
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile!!) ?: return

        // 获取当前类的 PsiClass 对象
        val psiClass = PsiTreeUtil.findChildOfType(psiFile, PsiClass::class.java) ?: return
        // 生成 DDLContext 并转化为 SQL 语句
        val ddlContext = generateDDLContextFromClass(psiClass)

        val databaseDDLGenerator = getDatabaseDDLGenerator(defaultDbType())
        val sql = databaseDDLGenerator.generateAddColDDL(ddlContext)
        // 将生成的 SQL 语句写入到新的文件并打开
        ShowSqlUtil. openSqlInEditor(project, sql,"GenAddColumnByThis")
    }

    // 根据 PsiClass 生成 DDLContext 对象
    private fun generateDDLContextFromClass(psiClass: PsiClass): DDLContext {
        val defaultDbType = defaultDbType()
        val createDDLContext = createDDLContext(psiClass, defaultDbType)
        return createDDLContext
    }

    private fun defaultDbType(): String {
        val settings = MyPluginSettings.instance
        val state = settings.state
        val defaultDbType = state.defaultDbType
        if (StrUtil.isBlank(defaultDbType)) {
            return MYSQL
        }
        return defaultDbType
    }

    // 在编辑器中打开生成的 SQL 语句
//    private fun openSqlInEditor(project: Project, sql: String) {
//        WriteCommandAction.runWriteCommandAction(project) {
//            // 创建一个临时文件来存储 SQL
//            val fileName = "generated_sql_${System.currentTimeMillis()}.sql"
//            val file = FileUtil.file(project.basePath, fileName)
//            file.writeText(sql)
//
//            // 打开 SQL 文件
//            val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
//            if (virtualFile != null) {
//                FileEditorManager.getInstance(project).openFile(virtualFile, true)
//            }
//        }
//    }

}

fun getClassComment(psiClass: PsiClass): String? {
    // 获取类的文档注释
    val docComment: PsiDocComment? = psiClass.docComment
    return docComment?.getText() // 获取注释内容
}
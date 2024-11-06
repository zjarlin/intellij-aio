package com.addzero.addl.action.exceldto

import JavaExcelEntityGenerator
import KotlinExcelEntityGenerator
import com.addzero.addl.util.ShowSqlUtil
import com.addzero.addl.util.fieldinfo.PsiUtil.psiCtx
import com.addzero.addl.util.getParentPathAndmkdir
import com.addzero.common.kt_util.addSuffixIfNot
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

class GenEasyExcelDTO : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        // 获取当前项目和编辑器上下文
        val project: Project = e.project ?: return
        val (editor, psiClass, ktClass, psiFile, virtualFile, classPath) = psiCtx(project)

        if (ktClass == null) {
            val let = psiClass?.let { JavaExcelEntityGenerator().generateJavaEntity(it, project) }
            val name = psiClass?.name
            val addSuffixIfNot = name.addSuffixIfNot("ExcelDTO")

            val filePath = virtualFile.path
            val filePath1 = filePath.getParentPathAndmkdir("dto")
            ShowSqlUtil.openTextInEditor(
                project, let!!, addSuffixIfNot, ".java", filePath1
            )
            return
        }
        val generateKotlinEntity = KotlinExcelEntityGenerator().generateKotlinEntity(ktClass!!, project)
        val name = ktClass?.name
        val addSuffixIfNot = name.addSuffixIfNot("ExcelDTO")
        val filePath = virtualFile.path
        val filePath1 = filePath.getParentPathAndmkdir("dto")
        ShowSqlUtil.openTextInEditor(
            project, generateKotlinEntity, addSuffixIfNot, ".kt", filePath1
        )
    }

}
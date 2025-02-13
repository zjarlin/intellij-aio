package com.github.zjarlin.autoddl.intention.bak

import cn.hutool.core.util.StrUtil
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.PsiValidateUtil
import com.addzero.common.kt_util.isBlank
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import isKotlinProject


class bakAddSwaggerAnnotationJavaAction : IntentionAction {

    override fun getText() = "Add Swagger Annotations"
    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null) return false
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset)
        val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        return psiClass != null && PsiValidateUtil.isValidTarget(null, psiClass).first
    }

    override fun startInWriteAction() = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return

        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset)
        val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: return
        val kotlinProject = isKotlinProject(project)

        for (field in psiClass.fields) {
            processField(project, field,kotlinProject)
        }
    }

    private fun processField(project: Project, field: PsiField, kotlinProject: Boolean) {
        // 检查是否已有Swagger注解
        val hasSwaggerAnnotation = field.annotations.any { annotation ->
            val name = annotation.qualifiedName?.substringAfterLast('.')
            name in listOf("Schema", "ApiModelProperty")
        }

        if (!hasSwaggerAnnotation) {
            val docComment = field.docComment
            if (docComment != null) {
                addSwaggerAnnotation(project, field, docComment,kotlinProject)
            }
        }
    }


    fun addSwaggerAnnotation(project: Project, field: PsiField, docComment: PsiDocComment, kotlinProject: Boolean) {
        // 清理文档注释
        val description = cleanDocComment(docComment.text)

        // 创建新的注解文本
        val settings = SettingContext.settings
        val id = settings.id
        val idType = settings.idType
        var swaggerAnnotation = settings.swaggerAnnotation


        if (kotlinProject) {
            swaggerAnnotation = StrUtil.appendIfMissing(swaggerAnnotation,
                "@", "get:")
        }


        val format = StrUtil.format(swaggerAnnotation, description)
        if (format.isBlank()) {
            return
        }

        // 使用 PsiElementFactory 创建注解
        val factory = JavaPsiFacade.getElementFactory(project)
        val annotation = factory.createAnnotationFromText(format, field)

        // 将注解添加到字段上方
        field.modifierList?.addBefore(annotation, field.modifierList?.firstChild)

        // 格式化代码
        CodeStyleManager.getInstance(project).reformat(field)
    }


//    private fun addSwaggerAnnotation(project: Project, field: PsiField, docComment: PsiDocComment) {
//        // 清理文档注释
//        val description = cleanDocComment(docComment.text)
//
//        // 创建新的注解
//        val factory = JavaPsiFacade.getInstance(project).elementFactory
//        val annotation = factory.createAnnotationFromText("@Schema(description = \"$description\")", field)
//
//        // 将注解添加到字段上
//        field.modifierList?.addBefore(annotation, field.modifierList?.firstChild)
//
//        // 格式化代码
//        CodeStyleManager.getInstance(project).reformat(field)
//    }

    private fun cleanDocComment(docComment: String): String {
        return docComment
            .replace(Regex("/\\*\\*?|\"\"\"?"), "")  // 去除开头的 /* 或 /** 和引号
            .replace(Regex("\\*/"), "")     // 去除结尾的 */
            .replace(Regex("\\*"), "")      // 去除行内的 *
            .replace(Regex("\\s+"), " ")    // 合并多个空格为一个
            .replace("/**", "")
            .replace("*/", "")
            .replace("*", "")
            .trim()
    }
}
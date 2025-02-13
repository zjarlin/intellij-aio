package com.github.zjarlin.autoddl.intention.bak

import cn.hutool.core.util.StrUtil
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.PsiValidateUtil
import com.addzero.addl.util.fieldinfo.PsiUtil.psiCtx
import com.addzero.common.kt_util.isBlank
import com.addzero.common.kt_util.isNotNull
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import com.intellij.openapi.editor.Editor
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtPsiFactory

class bakAddSwaggerAnnotationAction : IntentionAction {

    override fun getText() = "Add Swagger Annotations"
    override fun getFamilyName() = text
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null) return false
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset)
        val ktClass = PsiTreeUtil.getParentOfType(element, KtClass::class.java)
        return ktClass != null && PsiValidateUtil.isValidTarget(ktClass, null).first
    }
    override fun startInWriteAction() = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return

        val (editor, psiClass, ktClass, psiFile, virtualFile, classPath) = psiCtx(project)

        if (ktClass.isNotNull()) {
            val properties1 = ktClass?.getProperties()
            properties1?.forEach {

                processProperty (project,it)
            }
        }

    }

    private fun processProperty(project: Project, property: KtProperty) {
        // 检查是否已有Swagger注解
        val hasSwaggerAnnotation = property.annotationEntries.any { annotation ->
            val name = annotation.shortName?.asString()
            name in listOf("Schema", "ApiModelProperty")
        }

        if (!hasSwaggerAnnotation) {
            val docComment = property.docComment?.text
            if (docComment != null) {
                addSwaggerAnnotation(project, property, docComment)
            }
        }
    }



    private fun addSwaggerAnnotation(project: Project, property: KtProperty, docComment: String) {
        // 清理文档注释
        val description = cleanDocComment(docComment)

        // 创建新的注解文本
        val swaggerAnnotation = SettingContext.settings.swaggerAnnotation
        val format = StrUtil.format(swaggerAnnotation, description)
        if (format.isBlank()) {
            return
        }

        // 使用 KtPsiFactory 创建注解
        val ktPsiFactory = KtPsiFactory(project)
        val annotation = ktPsiFactory.createAnnotationEntry(format)

        // 将注解添加到属性上
        property.addAnnotationEntry(annotation)

        // 格式化代码
        CodeStyleManager.getInstance(project).reformat(property)
    }



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
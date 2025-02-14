package com.github.zjarlin.autoddl.intention

import cn.hutool.core.collection.CollUtil
import cn.hutool.core.util.StrUtil
import com.addzero.addl.util.PsiValidateUtil
import com.addzero.addl.util.fieldinfo.PsiUtil
import com.github.zjarlin.autoddl.intention.psipropertyutil.PsiPropertyUtil.addPsiJavaAnnotation
import com.github.zjarlin.autoddl.intention.psipropertyutil.PsiPropertyUtil.cleanDocComment
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

abstract class AbstractDocCommentAnnotationAction : IntentionAction {

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null) return false
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset)

        // 检查是否为Kotlin文件
        val ktClass = PsiTreeUtil.getParentOfType(element, KtClass::class.java)
        if (ktClass != null) {
            return PsiValidateUtil.isValidTarget(ktClass, null).first
        }

        // 检查是否为Java文件
        val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        return psiClass != null && PsiValidateUtil.isValidTarget(null, psiClass).first
    }

    override fun startInWriteAction() = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return

        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset)

        // 处理Kotlin类
        val ktClass = PsiTreeUtil.getParentOfType(element, KtClass::class.java)
        if (ktClass != null) {
            processKotlinClass(project, ktClass)
            return
        }

        // 处理Java类
        val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        if (psiClass != null) {
            processJavaClass(project, psiClass)
        }
    }

    private fun processKotlinClass(project: Project, ktClass: KtClass) {
        ktClass.getProperties().forEach { property ->
            processKotlinProperty(project, property)
        }
    }

    private fun processJavaClass(project: Project, psiClass: PsiClass) {
        val fields = psiClass.fields
        for (field in fields) {
            processJavaField(project, field)
        }
    }

    private fun processKotlinProperty(project: Project, property: KtProperty) {
        // 检查是否已有相应注解

        val annotationNames = getAnnotationNames()


        val hasAnnotation = property.annotationEntries.any { annotation ->
            val empty = CollUtil.isEmpty(annotationNames)
            if (empty) {
                //对于空的已有注解,则认定是自定义注解生成逻辑
                false
            } else {
                val shortName = annotation.shortName
                val name = shortName?.asString()
                val b = name in annotationNames
                b
            }

        }

        if (!hasAnnotation) {
            val docComment = PsiUtil.guessFieldComment(property)
            if (docComment != null) {
                addKotlinAnnotation(project, property, docComment)
            }
        }
    }

    private fun processJavaField(project: Project, field: PsiField) {
        // 检查是否已有相应注解
        val hasAnnotation = field.annotations.any { annotation ->
            val name = annotation.qualifiedName?.substringAfterLast('.')
            name in getAnnotationNames()
        }

        if (!hasAnnotation) {
            val docComment = PsiUtil.guessFieldComment(field)
//            val docComment = field.docComment
            if (docComment != null) {
                addPsiJavaAnnotation(project, field, docComment, getAnnotationTemplate())
            }
        }
    }

    protected open fun addKotlinAnnotation(project: Project, property: KtProperty, docComment: String) {
        // 默认实现，子类可以根据需要重写
        val description = cleanDocComment(docComment)
        var annotationText = getAnnotationTemplate().replace("{}", description)
        annotationText = StrUtil.replace(annotationText, "@", "@get:")

//        annotationText = annotationText.escapeSpecialCharacters()

        val factory = KtPsiFactory(project)

        try {
            val annotation = factory.createAnnotationEntry(annotationText)
            property.addAnnotationEntry(annotation)
        } catch (e: Exception) {
            return
        }

        CodeStyleManager.getInstance(project).reformat(property)
    }

    /**
     * 获取注解模板
     * 例如："@Schema(description = \"{}\")" 或 "@ExcelProperty(\"{}\")"
     */
    protected abstract fun getAnnotationTemplate(): String

    /**
     * 获取需要检查的注解名称列表
     * 例如：["Schema", "ApiModelProperty"] 或 ["ExcelProperty"]
     */
    protected abstract fun getAnnotationNames(): List<String>
}
package site.addzero.addl.intention

import cn.hutool.core.collection.CollUtil
import cn.hutool.core.util.StrUtil
import site.addzero.addl.ai.util.ai.AiUtil
import site.addzero.addl.intention.psipropertyutil.PsiPropertyUtil.addPsiJavaAnnotation
import site.addzero.addl.intention.psipropertyutil.PsiPropertyUtil.cleanDocComment
import site.addzero.util.psi.PsiUtil.getCurrentPsiElement
import site.addzero.util.psi.PsiUtil.guessFieldComment
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.util.ai.invoker.AiUtil
import site.addzero.util.psi.PsiUtil.isJavaPojo
import site.addzero.util.psi.PsiUtil.isKotlinPojo

abstract class AbstractDocCommentAnnotationAction : IntentionAction {

    // 存储无注释字段的集合
    private val noCommentFields = mutableMapOf<String, Any>()

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val element = file.getCurrentPsiElement(editor)
        val kotlinPojo = isKotlinPojo(element)
        // 检查是否为Java文件
        val javaPojo = isJavaPojo(element)
        return javaPojo||kotlinPojo
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
        // 清空字段集合
        noCommentFields.clear()
        // 收集无注释的字段
        ktClass.getProperties().forEach { property ->
            processKotlinProperty(project, property)
        }
        // 如果有无注释的字段，批量处理
        if (noCommentFields.isNotEmpty()) {
            // 调用AI接口获取批量注释
            val commentMap = AiUtil.batchGetComments(noCommentFields)

            noCommentFields.forEach { (fieldName, field) ->
                val comment = commentMap?.get(fieldName) ?: ""
                addKotlinAnnotation(project, field as KtProperty, comment)
            }
        }
    }

    private fun processJavaClass(project: Project, psiClass: PsiClass) {
        // 清空字段集合
        noCommentFields.clear()

        // 收集无注释的字段
        val fields = psiClass.fields
        for (field in fields) {
            processJavaField(project, field)
        }

        // 如果有无注释的字段，批量处理
        if (noCommentFields.isNotEmpty()) {
            // 调用AI接口获取批量注释
            val commentMap = AiUtil.batchGetComments(noCommentFields)

            // 异步处理文档注释
            noCommentFields.forEach { (fieldName, field) ->
                val comment = commentMap?.get(fieldName) ?: ""
                addPsiJavaAnnotation(project, field as PsiField, comment, getAnnotationTemplate())
            }
        }
    }

    private fun processJavaField(project: Project, field: PsiField) {
        // 检查是否已有相应注解
        // 检查是否已有相应注解
        val annotationNames = getAnnotationNames()

        val hasAnnotation = field.annotations.any { annotation ->
            val empty = CollUtil.isEmpty(annotationNames)
            if (empty) {
                //对于空的已有注解,则认定是自定义注解生成逻辑
                false
            } else {
//                val shortName = annotation.qualifiedName
                val name = annotation.qualifiedName?.substringAfterLast('.')
                name in annotationNames
            }

        }

        // 将无注释的字段添加到集合中
        val comment = field.guessFieldComment()
        if (!hasAnnotation) {
            if (comment.isNotBlank()) {
                addPsiJavaAnnotation(project, field as PsiField, comment, getAnnotationTemplate())
            }else{
                field.name?.let { fieldName ->
                    noCommentFields[fieldName] = field
                }

            }

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
                name in annotationNames
            }
        }

        if (!hasAnnotation) {
            // 获取现有的文档注释
            val docComment = property.guessFieldComment()

            if (docComment.isNotBlank()) {
                addKotlinAnnotation(project, property, docComment)
            } else {
                // 将无注释的字段添加到集合中
                property.name?.let { fieldName ->
                    noCommentFields[fieldName] = property
                }
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

package site.addzero.aiannotator.intention.base

import site.addzero.aiannotator.util.AiService
import site.addzero.aiannotator.util.PsiPropertyUtil
import site.addzero.aiannotator.util.PsiUtil
import site.addzero.aiannotator.util.PsiUtil.guessFieldComment
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

abstract class AbstractDocCommentAnnotationAction : IntentionAction {

    private val noCommentFields = mutableMapOf<String, Any>()

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val element = file?.let { PsiUtil.getCurrentPsiElement(it, editor) }
        val kotlinPojo = PsiUtil.isKotlinPojo(element)
        val javaPojo = PsiUtil.isJavaPojo(element)
        return javaPojo || kotlinPojo
    }

    override fun startInWriteAction() = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return

        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset)

        val ktClass = PsiTreeUtil.getParentOfType(element, KtClass::class.java)
        if (ktClass != null) {
            processKotlinClass(project, ktClass)
            return
        }

        val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        if (psiClass != null) {
            processJavaClass(project, psiClass)
        }
    }

    private fun processKotlinClass(project: Project, ktClass: KtClass) {
        noCommentFields.clear()
        
        ktClass.getProperties().forEach { property ->
            processKotlinProperty(project, property)
        }
        
        if (noCommentFields.isNotEmpty()) {
            val commentMap = AiService.batchGetComments(noCommentFields)
            
            noCommentFields.forEach { (fieldName, field) ->
                val comment = commentMap?.get(fieldName) ?: ""
                if (comment.isNotBlank()) {
                    addKotlinAnnotation(project, field as KtProperty, comment)
                }
            }
        }
    }

    private fun processJavaClass(project: Project, psiClass: PsiClass) {
        noCommentFields.clear()

        val fields = psiClass.fields
        for (field in fields) {
            processJavaField(project, field)
        }

        if (noCommentFields.isNotEmpty()) {
            val commentMap = AiService.batchGetComments(noCommentFields)

            noCommentFields.forEach { (fieldName, field) ->
                val comment = commentMap?.get(fieldName) ?: ""
                if (comment.isNotBlank()) {
                    PsiPropertyUtil.addPsiJavaAnnotation(
                        project, 
                        field as PsiField, 
                        comment, 
                        getAnnotationTemplate()
                    )
                }
            }
        }
    }

    private fun processJavaField(project: Project, field: PsiField) {
        val annotationNames = getAnnotationNames()

        val hasAnnotation = field.annotations.any { annotation ->
            if (annotationNames.isEmpty()) {
                false
            } else {
                val name = annotation.qualifiedName?.substringAfterLast('.')
                name in annotationNames
            }
        }

        val comment = field.guessFieldComment()
        if (!hasAnnotation) {
            if (comment.isNotBlank()) {
                PsiPropertyUtil.addPsiJavaAnnotation(
                    project, 
                    field, 
                    comment, 
                    getAnnotationTemplate()
                )
            } else {
                field.name?.let { fieldName ->
                    noCommentFields[fieldName] = field
                }
            }
        }
    }

    private fun processKotlinProperty(project: Project, property: KtProperty) {
        val annotationNames = getAnnotationNames()
        val hasAnnotation = property.annotationEntries.any { annotation ->
            if (annotationNames.isEmpty()) {
                false
            } else {
                val name = annotation.shortName?.asString()
                name in annotationNames
            }
        }

        if (!hasAnnotation) {
            val docComment = property.guessFieldComment()

            if (docComment.isNotBlank()) {
                addKotlinAnnotation(project, property, docComment)
            } else {
                property.name?.let { fieldName ->
                    noCommentFields[fieldName] = property
                }
            }
        }
    }

    protected open fun addKotlinAnnotation(project: Project, property: KtProperty, docComment: String) {
        val description = PsiPropertyUtil.cleanDocComment(docComment)
        var annotationText = getAnnotationTemplate().replace("{}", description)
        annotationText = annotationText.replace("@", "@get:")

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

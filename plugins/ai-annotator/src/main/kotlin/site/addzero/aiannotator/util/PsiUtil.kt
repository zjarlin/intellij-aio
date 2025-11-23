package site.addzero.aiannotator.util

import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.psi.*

object PsiUtil {

    fun PsiFile?.getCurrentPsiElement(editor: Editor?): PsiElement? {
        if (this == null || editor == null) return null
        val offset = editor.caretModel.offset
        return findElementAt(offset)
    }

    fun PsiFile?.isKotlinPojo(editor: Editor?): Boolean {
        val element = this.getCurrentPsiElement(editor) ?: return false
        return isKotlinPojo(element)
    }

    fun isKotlinPojo(element: PsiElement?): Boolean {
        val ktClass = PsiTreeUtil.getParentOfType(element, KtClass::class.java) ?: return false
        return ktClass.isData() || !ktClass.isInterface() && !ktClass.isEnum() && !ktClass.isAnnotation()
    }

    fun PsiFile?.isJavaPojo(editor: Editor?): Boolean {
        val element = this.getCurrentPsiElement(editor) ?: return false
        return isJavaPojo(element)
    }

    fun isJavaPojo(element: PsiElement?): Boolean {
        val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: return false
        return !psiClass.isInterface && !psiClass.isEnum && !psiClass.isAnnotationType
    }

    fun PsiField.guessFieldComment(): String {
        val docComment = this.docComment
        if (docComment != null) {
            return extractJavadocDescription(docComment)
        }

        for (annotation in this.annotations) {
            val comment = extractAnnotationComment(annotation)
            if (comment.isNotBlank()) {
                return comment
            }
        }

        return ""
    }

    fun KtProperty.guessFieldComment(): String {
        val kdoc = this.docComment
        if (kdoc != null) {
            return kdoc.getDefaultSection().getContent().trim()
        }

        for (annotation in this.annotationEntries) {
            val comment = extractKotlinAnnotationComment(annotation)
            if (comment.isNotBlank()) {
                return comment
            }
        }

        return ""
    }

    private fun extractJavadocDescription(docComment: PsiDocComment): String {
        val description = docComment.descriptionElements
            .joinToString(" ") { it.text.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()
        return description
    }

    private fun extractAnnotationComment(annotation: PsiAnnotation): String {
        val qualifiedName = annotation.qualifiedName ?: return ""
        
        return when {
            qualifiedName.endsWith("Schema") -> extractSchemaDescription(annotation)
            qualifiedName.endsWith("ApiModelProperty") -> extractApiModelPropertyValue(annotation)
            qualifiedName.endsWith("ExcelProperty") || qualifiedName.endsWith("Excel") -> 
                extractExcelPropertyValue(annotation)
            else -> ""
        }
    }

    private fun extractKotlinAnnotationComment(annotation: KtAnnotationEntry): String {
        val shortName = annotation.shortName?.asString() ?: return ""
        
        return when (shortName) {
            "Schema" -> extractKotlinSchemaDescription(annotation)
            "ApiModelProperty" -> extractKotlinApiModelPropertyValue(annotation)
            "ExcelProperty", "Excel" -> extractKotlinExcelPropertyValue(annotation)
            else -> ""
        }
    }

    private fun extractSchemaDescription(annotation: PsiAnnotation): String {
        return annotation.findAttributeValue("description")?.text?.removeSurrounding("\"") ?: ""
    }

    private fun extractApiModelPropertyValue(annotation: PsiAnnotation): String {
        return annotation.findAttributeValue("value")?.text?.removeSurrounding("\"") ?: ""
    }

    private fun extractExcelPropertyValue(annotation: PsiAnnotation): String {
        return annotation.findAttributeValue("value")?.text?.removeSurrounding("\"") ?: ""
    }

    private fun extractKotlinSchemaDescription(annotation: KtAnnotationEntry): String {
        return extractKotlinAnnotationAttribute(annotation, "description")
    }

    private fun extractKotlinApiModelPropertyValue(annotation: KtAnnotationEntry): String {
        return extractKotlinAnnotationAttribute(annotation, "value")
    }

    private fun extractKotlinExcelPropertyValue(annotation: KtAnnotationEntry): String {
        return extractKotlinAnnotationAttribute(annotation, "value")
    }

    private fun extractKotlinAnnotationAttribute(annotation: KtAnnotationEntry, attrName: String): String {
        val arguments = annotation.valueArguments
        for (arg in arguments) {
            val name = arg.getArgumentName()?.asName?.asString()
            if (name == attrName || (name == null && attrName == "value")) {
                val expr = arg.getArgumentExpression()
                if (expr is KtStringTemplateExpression) {
                    return expr.entries.joinToString("") { 
                        when (it) {
                            is KtLiteralStringTemplateEntry -> it.text
                            else -> ""
                        }
                    }
                }
            }
        }
        return ""
    }
}

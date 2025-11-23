package site.addzero.aiannotator.util

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiField
import com.intellij.psi.codeStyle.CodeStyleManager

object PsiPropertyUtil {

    fun cleanDocComment(docComment: String): String {
        return docComment
            .replace(Regex("/\\*\\*?|\"\"\"|\"\"\"?"), "")
            .replace(Regex("\\*/"), "")
            .replace(Regex("\\*"), "")
            .replace(Regex("\\s+"), " ")
            .replace("/**", "")
            .replace("*/", "")
            .replace("*", "")
            .trim()
    }

    fun addPsiJavaAnnotation(
        project: Project,
        field: PsiField,
        docComment: String,
        annotationTemplate: String
    ) {
        val description = cleanDocComment(docComment)
        val format = annotationTemplate.replace("{}", description)
        
        if (format.isBlank()) {
            return
        }

        val factory = JavaPsiFacade.getElementFactory(project)
        try {
            val annotation = factory.createAnnotationFromText(format, field)
            field.modifierList?.addBefore(annotation, field.modifierList?.firstChild)
        } catch (e: Exception) {
            return
        }

        CodeStyleManager.getInstance(project).reformat(field)
    }
}

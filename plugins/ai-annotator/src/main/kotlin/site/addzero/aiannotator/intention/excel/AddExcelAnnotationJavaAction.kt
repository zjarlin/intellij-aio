package site.addzero.aiannotator.intention.excel

import site.addzero.aiannotator.intention.base.AbstractDocCommentAnnotationAction
import site.addzero.aiannotator.settings.AiAnnotatorSettingsService
import site.addzero.aiannotator.util.PsiUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class AddExcelAnnotationJavaAction : AbstractDocCommentAnnotationAction() {
    
    override fun getAnnotationTemplate(): String {
        return AiAnnotatorSettingsService.getInstance().state.excelAnnotation
    }

    override fun getAnnotationNames(): List<String> {
        return listOf("ExcelProperty", "Excel")
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val javaPojo = file?.let { PsiUtil.isJavaPojo(it, editor) } ?: false
        return javaPojo
    }

    override fun getText(): String {
        return "Add Excel Annotation"
    }
}

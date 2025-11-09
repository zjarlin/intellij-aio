package com.addzero.addl.intention.excel

import com.addzero.addl.intention.AbstractDocCommentAnnotationAction
import com.addzero.addl.settings.SettingContext
import com.addzero.util.psi.PsiUtil.isKotlinPojo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class AddExcelPropertyAnnotationAction : AbstractDocCommentAnnotationAction() {
    override fun getAnnotationTemplate(): String {
        return SettingContext.settings.excelAnnotation
    }

    override fun getAnnotationNames(): List<String> {
        return listOf("ExcelProperty","Excel")
    }
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val kotlinPojo = file.isKotlinPojo(editor)
        return  kotlinPojo
    }

    override fun getText(): String {
        return "Add ExcelProperty Annotation"
    }


}

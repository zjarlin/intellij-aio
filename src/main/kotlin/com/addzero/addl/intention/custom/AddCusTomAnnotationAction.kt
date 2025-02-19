package com.addzero.addl.intention.custom

import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.fieldinfo.PsiUtil.isKotlinPojo
import com.addzero.addl.intention.AbstractDocCommentAnnotationAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class AddCusTomAnnotationAction : AbstractDocCommentAnnotationAction() {
    override fun getAnnotationTemplate(): String {
        return SettingContext.settings.customAnnotation
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val kotlinPojo = isKotlinPojo(editor, file)
        return  kotlinPojo
    }

    override fun getAnnotationNames(): List<String> {
        return listOf()
    }

    override fun getText(): String {
        return "Add Custom Annotation"
    }


}

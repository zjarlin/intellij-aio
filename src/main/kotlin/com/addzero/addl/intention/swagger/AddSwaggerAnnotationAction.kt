package com.addzero.addl.intention.swagger

import com.addzero.addl.intention.AbstractDocCommentAnnotationAction
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.fieldinfo.PsiUtil.isKotlinPojo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class AddSwaggerAnnotationAction : AbstractDocCommentAnnotationAction() {
    override fun getAnnotationTemplate(): String {
        val swaggerAnnotation = SettingContext.settings.swaggerAnnotation
        return swaggerAnnotation
    }
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val kotlinPojo = isKotlinPojo(editor, file)
        return  kotlinPojo
    }



    override fun getAnnotationNames(): List<String> {
        return listOf("Schema", "ApiModelProperty")
    }

    override fun getText(): String {
        return "Add Swagger Annotation"
    }

}

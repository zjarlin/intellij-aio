package com.github.zjarlin.autoddl.intention.custom

import com.addzero.addl.settings.SettingContext
import com.github.zjarlin.autoddl.intention.AbstractDocCommentAnnotationAction

class AddCusTomAnnotationAction : AbstractDocCommentAnnotationAction() {
    override fun getAnnotationTemplate(): String {
        val swaggerAnnotation = SettingContext.settings.swaggerAnnotation
        return swaggerAnnotation
    }

    override fun getAnnotationNames(): List<String> {
        return listOf("Schema", "ApiModelProperty")
    }

    override fun getText(): String {
        return "Add Custom Annotation"
    }


}
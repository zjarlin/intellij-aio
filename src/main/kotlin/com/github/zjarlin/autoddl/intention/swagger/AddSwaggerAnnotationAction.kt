package com.github.zjarlin.autoddl.intention.swagger

import com.addzero.addl.settings.SettingContext
import com.github.zjarlin.autoddl.intention.AbstractDocCommentAnnotationAction

class AddSwaggerAnnotationAction : AbstractDocCommentAnnotationAction() {
    override fun getAnnotationTemplate(): String {
        val swaggerAnnotation = SettingContext.settings.swaggerAnnotation
        return swaggerAnnotation
    }

    override fun getAnnotationNames(): List<String> {
        return listOf("Schema", "ApiModelProperty")
    }

    override fun getText(): String {
        return "Add Swagger Annotation"
    }


}
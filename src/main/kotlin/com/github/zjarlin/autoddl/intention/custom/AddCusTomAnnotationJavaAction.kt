package com.github.zjarlin.autoddl.intention.custom

import com.addzero.addl.settings.SettingContext
import com.github.zjarlin.autoddl.intention.AbstractDocCommentAnnotationAction

class AddCusTomAnnotationJavaAction : AbstractDocCommentAnnotationAction() {
    override fun getAnnotationTemplate(): String {
        val swaggerAnnotation = SettingContext.settings.swaggerAnnotation
        return swaggerAnnotation
    }

    override fun getAnnotationNames(): List<String> {
        return listOf()
    }

    override fun getText(): String {
        return "Add Custom Annotation"
    }


}
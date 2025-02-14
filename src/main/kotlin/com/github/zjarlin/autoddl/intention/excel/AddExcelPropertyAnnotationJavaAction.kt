package com.github.zjarlin.autoddl.intention.excel

import com.addzero.addl.settings.SettingContext
import com.github.zjarlin.autoddl.intention.AbstractDocCommentAnnotationAction

class AddExcelPropertyAnnotationJavaAction : AbstractDocCommentAnnotationAction() {
    override fun getAnnotationTemplate(): String {
        return SettingContext.settings.excelAnnotation
    }

    override fun getAnnotationNames(): List<String> {
        return listOf("ExcelProperty")
    }

    override fun getText(): String {
        return "Add ExcelProperty Annotation"
    }


}
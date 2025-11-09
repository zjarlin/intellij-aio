package com.addzero.util.lsi_impl.impl.psi.anno

import com.addzero.util.lsi.constant.*
import com.intellij.psi.PsiAnnotation
import site.addzero.util.str.removeAnyQuote

fun PsiAnnotation.getArg(argName: String): String? {
    val text1 = this.findAttributeValue(argName)?.text
    return text1?.trim('"')
}

fun PsiAnnotation.getArg(): String? {
    return getArg("value")
}

fun Array<out PsiAnnotation>.guessFieldCommentOrNull(): String? {
    this.forEach { annotation ->
        val description = when (annotation.qualifiedName) {
            API_MODEL_PROPERTY -> {
                annotation.findAttributeValue("value")?.text
            }

            SCHEMA -> {
                annotation.findAttributeValue("description")?.text
            }

            EXCEL_PROPERTY_ALIBABA, EXCEL_PROPERTY_IDEV -> {
                annotation.findAttributeValue("value")?.text
            }

            EXCEL_EASYPOI -> {
                annotation.findAttributeValue("name")?.text
            }

            else -> null
        }

        if (!description.isNullOrBlank()) {
            return description.removeAnyQuote()
        }
    }

    return null
}

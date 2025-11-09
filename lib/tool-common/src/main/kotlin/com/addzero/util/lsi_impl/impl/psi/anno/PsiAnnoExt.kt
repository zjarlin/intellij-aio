package com.addzero.util.lsi_impl.impl.psi.anno

import com.intellij.psi.PsiAnnotation

fun PsiAnnotation.getArg(argName: String): String? {
    val text1 = this.findAttributeValue(argName)?.text
    return text1?.trim('"')
}

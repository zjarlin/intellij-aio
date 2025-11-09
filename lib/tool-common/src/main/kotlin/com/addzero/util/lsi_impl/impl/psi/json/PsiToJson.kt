package com.addzero.util.lsi_impl.impl.psi.json

import com.addzero.util.lsi.impl.psi.field.getDefaultValue
import com.addzero.util.lsi.impl.psi.field.toDefaultValueMap
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import java.util.*

/**
 * PsiClass 0 JSON lbiU
 */

/**
 *  PsiClass lb: Map ӄ(� JSON �
 * Map � key :W�
value :W��ؤ<
 *
 * @param project y
 * @return +@	W��vؤ<� LinkedHashMap
 */
fun PsiClass.toJsonMap(project: Project): LinkedHashMap<Any?, Any?> {
    return this.toDefaultValueMap(project)
}

/**
 * @deprecated ( toJsonMap ��}
�p
 */
@Deprecated(
    "Use toJsonMap instead for better naming clarity",
    ReplaceWith("this.toJsonMap(project)", "com.addzero.util.lsi.impl.psi.json.toJsonMap")
)
fun PsiClass.generateMap(project: Project): LinkedHashMap<Any?, Any?> {
    return toJsonMap(project)
}

package com.addzero.addl.util

import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.psi.KtClass

object PsiValidateUtil {
    /**
     * 检查是否为POJO或Jimmer实体
     */
    fun isValidTarget(ktClass: KtClass?, psiClass: PsiClass?): Pair<Boolean, Boolean> {
        var iskotlin = false
        val b1 = when {
            ktClass != null -> {
                // 检查Kotlin类
                val annotations = ktClass.annotationEntries.map { it.shortName?.asString() }
                iskotlin=true
                annotations.any { it in listOf("Entity", "Table", "Data", "Getter", "Setter") }
            }

            psiClass != null -> {
                // 检查Java类
                val annotations = psiClass.modifierList?.annotations?.map { it.qualifiedName }
                annotations?.any {
                    val b = it == "lombok.Data" || it == "lombok.Getter" || it == "lombok.Setter"
                    it?.endsWith(".Entity") == true || it?.endsWith(".Table") == true || b
                } ?: false
            }

            else -> false
        }
        return b1 to iskotlin
    }
}
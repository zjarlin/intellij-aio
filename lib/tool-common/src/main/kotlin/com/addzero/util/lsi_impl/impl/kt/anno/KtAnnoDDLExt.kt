package com.addzero.util.lsi_impl.impl.kt.anno

import com.addzero.util.lsi_impl.impl.psi.anno.getArg
import com.intellij.psi.PsiAnnotation

fun Array<out PsiAnnotation>?.guessTableName (): String? {
    this?:return null
    for (annotation in this) {
        val qualifiedName = annotation.qualifiedName
        val arg1 = annotation.getArg("name")
        when (qualifiedName) {
            "com.baomidou.mybatisplus.annotation.TableName" -> {
                val arg = annotation.getArg("value")
                return arg
            }
            "org.babyfish.jimmer.sql.Table" -> {
                // Jimmer 的 @Table 注解
                return arg1
            }
            "javax.persistence.Table", "jakarta.persistence.Table" -> {
                // JPA 的 @Table 注解
                return arg1
            }
        }
    }

    return null
}

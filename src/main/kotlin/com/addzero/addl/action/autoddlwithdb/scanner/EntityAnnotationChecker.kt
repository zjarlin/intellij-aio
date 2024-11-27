package com.addzero.addl.action.autoddlwithdb.scanner

import com.addzero.addl.action.autoddlwithdb.scanner.EntityAnnotationChecker.Companion.ENTITY_ANNOTATIONS
import com.addzero.addl.util.fieldinfo.hasAnnotation
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.psi.KtClass


interface EntityAnnotationChecker<T> {
    fun isEntityClass(clazz: T): Boolean

    companion object {
        val ENTITY_ANNOTATIONS = listOf(
            "javax.persistence.Entity", "jakarta.persistence.Entity", "javax.persistence.Table", "jakarta.persistence.Table", "org.babyfish.jimmer.sql.Table", "com.baomidou.mybatisplus.annotation.TableName"
        )
    }
}

class KotlinEntityAnnotationChecker : EntityAnnotationChecker<KtClass> {
    override fun isEntityClass(clazz: KtClass): Boolean {
        val any = ENTITY_ANNOTATIONS.any {
            clazz.hasAnnotation(it)
        }
        return any
    }
}

class JavaEntityAnnotationChecker : EntityAnnotationChecker<PsiClass> {
    override fun isEntityClass(clazz: PsiClass): Boolean {
        val any = ENTITY_ANNOTATIONS.any {
            clazz.hasAnnotation(it)
        }
        return any
    }
}
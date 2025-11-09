package com.addzero.util.lsi_impl.impl.psi.clazz

import com.addzero.util.lsi.anno.LsiAnnotation
import com.addzero.util.lsi.clazz.LsiClass
import com.addzero.util.lsi.field.LsiField
import com.addzero.util.lsi.method.LsiMethod
import com.addzero.util.lsi_impl.impl.psi.field.PsiLsiField
import com.addzero.util.lsi_impl.impl.psi.method.PsiLsiMethod
import com.addzero.util.lsi_impl.impl.psi.anno.PsiLsiAnnotation
import com.intellij.psi.PsiClass

/**
 * 基于 PSI 的 LsiClass 实现
 */
class PsiLsiClass(private val psiClass: PsiClass) : LsiClass {
    override val name: String?
        get() = psiClass.name

    override val qualifiedName: String?
        get() = psiClass.qualifiedName

    override val comment: String?
        get() = psiClass.docComment?.text

    override val fields: List<LsiField>
        get() = psiClass.allFields.map { PsiLsiField(it) }

    override val annotations: List<LsiAnnotation>
        get() = psiClass.annotations.map { PsiLsiAnnotation(it) }

    override val isInterface: Boolean
        get() = psiClass.isInterface

    override val isEnum: Boolean
        get() = psiClass.isEnum

    override val isCollectionType: Boolean
        get() = psiClass.isCollectionType()

    override val isPojo: Boolean
        get() = psiClass.isPojo()

    override val superClasses: List<LsiClass>
        get() {
            val supers = psiClass.supers
            val toList = supers.toList()
            val map = supers.map { it.supers.toList() }.flatten()
            val classes = toList + map
            val map1 = classes.map { (PsiLsiClass(it)) }
            return map1
        }

    override val interfaces: List<LsiClass>
        get() = psiClass.interfaces.map { PsiLsiClass(it) }


    override val guessTableName: String
        get() = psiClass.guessTableName() ?: ""

    override val methods: List<LsiMethod>
        get() = psiClass.jimmerProperty().map { PsiLsiMethod(it) }
}

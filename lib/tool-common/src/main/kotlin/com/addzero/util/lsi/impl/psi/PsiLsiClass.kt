package com.addzero.util.lsi.impl.psi

import com.addzero.util.lsi.LsiAnnotation
import com.addzero.util.lsi.LsiClass
import com.addzero.util.lsi.LsiField
import com.addzero.util.lsi.LsiMethod
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod

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
        get() = PsiClassAnalyzers.CollectionTypeAnalyzer.isCollectionType(psiClass)

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
        get() {
            val guessTableNameByAnno = psiClass.guessTableNameByAnno()
            val string = guessTableNameByAnno ?: psiClass.text
            return string
        }


    override val methods: List<LsiMethod>
        get() {
            val methods1 = psiClass.methods()
            val lsiMethods = methods1.map { it.toLsiMethod() }
            return lsiMethods
        }
}

fun PsiMethod.toLsiMethod(): LsiMethod {

    return TODO("提供返回值")
}

package com.addzero.util.lsi.impl.kt

import com.addzero.util.lsi.LsiAnnotation
import com.addzero.util.lsi.LsiClass
import com.addzero.util.lsi.LsiField
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty

/**
 * 基于 Kotlin PSI 的 LsiClass 实现
 */
class KtLsiClass(private val ktClass: KtClass) : LsiClass {


    override val name: String?
        get() = ktClass.name

    override val qualifiedName: String?
        get() = ktClass.fqName?.asString()

    override val comment: String?
        get() = ktClass.docComment?.text

    override val fields: List<LsiField>
        get() = ktClass.getProperties().map { KtLsiField(it) }

    override val annotations: List<LsiAnnotation>
        get() = ktClass.annotationEntries.map { KtLsiAnnotation(it) }

    override val isInterface: Boolean
        get() = ktClass.isInterface()

    override val isEnum: Boolean
        get() = ktClass.isEnum()

    override val isCollectionType: Boolean
        get() = KtClassAnalyzers.CollectionTypeAnalyzer.isCollectionType(ktClass)

    override val superClasses: List<LsiClass>
        get() = TODO("需要根据 Kotlin PSI 获取父类")

    override val interfaces: List<LsiClass>
        get() = TODO("需要根据 Kotlin PSI 获取接口")
}

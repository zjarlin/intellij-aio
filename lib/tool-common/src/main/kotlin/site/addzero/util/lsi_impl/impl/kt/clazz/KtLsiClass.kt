package site.addzero.util.lsi_impl.impl.kt.clazz

import site.addzero.util.lsi.anno.LsiAnnotation
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.method.LsiMethod
import site.addzero.util.lsi_impl.impl.kt.anno.KtLsiAnnotation
import site.addzero.util.lsi_impl.impl.kt.field.KtLsiField
import site.addzero.util.lsi_impl.impl.kt.`fun`.KtLsiMethod
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction

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
        get() = ktClass.isCollectionType()

    override val isPojo: Boolean
        get() = ktClass.isPojo()

    override val superClasses: List<LsiClass>
        get() = TODO("需要根据 Kotlin PSI 获取父类")

    override val interfaces: List<LsiClass>
        get() = TODO("需要根据 Kotlin PSI 获取接口")

    override val guessTableName: String
        get() = ktClass.guessTableEnglishName()

    override val methods: List<LsiMethod>
        get() = ktClass.declarations.filterIsInstance<KtFunction>()
            .map { KtLsiMethod(it) }
}

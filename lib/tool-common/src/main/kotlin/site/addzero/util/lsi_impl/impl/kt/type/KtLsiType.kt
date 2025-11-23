package site.addzero.util.lsi_impl.impl.kt.type

import org.jetbrains.kotlin.psi.KtTypeReference
import site.addzero.util.lsi.anno.LsiAnnotation
import site.addzero.util.lsi.assist.isArray
import site.addzero.util.lsi.assist.isKotlinPrimitiveType
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.type.LsiType
import site.addzero.util.lsi_impl.impl.kt.anno.KtLsiAnnotation

/**
 * 基于 Kotlin PSI 的 LsiType 实现
 */
class KtLsiType(private val ktType: KtTypeReference) : LsiType {
    override val name: String?
        get() = ktType.text

    override val qualifiedName: String?
        get() = ktType.name

    override val presentableText: String?
        get() = ktType.text

    override val annotations: List<LsiAnnotation>
        get() = ktType.annotationEntries.map { KtLsiAnnotation(it) }

    override val isCollectionType: Boolean
        get() = ktType.isCollectionType()

    override val isNullable: Boolean
        get() = ktType.typeElement?.text?.endsWith("?") ?: false

    override val typeParameters: List<LsiType>
        get() = emptyList() // 简化处理，暂不实现泛型参数

    override val isPrimitive: Boolean
        get() = isKotlinPrimitiveType(ktType.text)

    override val componentType: LsiType?
        get() = null // 简化处理，暂不实现数组元素类型

    override val isArray: Boolean
        get() {
            val text = ktType.text
            return text.isArray()
        }

    override val lsiClass: LsiClass?
        get() = null // TODO: 不知道kt类型对应的KtClass 怎么获取,进一步转LsiClass

}

package com.addzero.util.lsi.impl.kt

import com.addzero.util.lsi.LsiAnnotation
import com.addzero.util.lsi.LsiType
import org.jetbrains.kotlin.psi.KtTypeReference

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
        get() = KtClassAnalyzers.CollectionTypeAnalyzer.isCollectionType(ktType)

    override val isNullable: Boolean
        get() = ktType.typeElement?.text?.endsWith("?") ?: false

    override val typeParameters: List<LsiType>
        get() = emptyList() // 简化处理，暂不实现泛型参数

    override val isPrimitive: Boolean
        get() = isKotlinPrimitiveType(ktType.text)

    override val componentType: LsiType?
        get() = null // 简化处理，暂不实现数组元素类型

    override val isArray: Boolean
        get() = ktType.text.startsWith("Array<") || ktType.text.endsWith("[]")

    private fun isKotlinPrimitiveType(typeName: String): Boolean {
        return typeName in setOf("Int", "Boolean", "Byte", "Char", "Double", "Float", "Long", "Short")
    }
}

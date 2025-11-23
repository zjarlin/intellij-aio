package site.addzero.util.lsi_impl.impl.psi.type

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTypesUtil
import site.addzero.util.lsi.assist.isCollectionType
import site.addzero.util.lsi_impl.impl.psi.clazz.toDefaultValueMap
import site.addzero.util.str.containsAnyIgnoreCase
import java.util.*

fun PsiClassType.qualifiedName(): String? {
    return this.resolve()?.qualifiedName
}
    fun PsiType.getJavaClassFromPsiType(): Class<*> {
        val clazz = this.toPsiClass()
        val name = clazz?.name
        if (name.isNullOrBlank()) {
            return String::class.java
        }

        return TODO("提供返回值")
    }


fun PsiType.toPsiClass(): PsiClass? {

    val resolvedClass = PsiTypesUtil.getPsiClass(this)
    return resolvedClass
}

fun PsiType.isNullable(): Boolean {
    // Void类型总是可空的
    if (this == PsiTypes.voidType()) return true
    // 基本类型不可空
    if (this is PsiPrimitiveType) return false
    // 检查是否有 Nullable 注解
    for (annotation in this.annotations) {
        val shortName = annotation.nameReferenceElement?.referenceName
        if (shortName != null && shortName.containsAnyIgnoreCase("Nullable")) {
            return true
        }
    }
    return true
}

/**
 * 处理 List 类型的默认值生成
 * 从 containingClass 推导 project，无需显式传入
 *
 * @param containingClass 包含该类型的类
 * @return 包含一个示例元素的 List
 */
fun PsiType.handleListDefaultValue(containingClass: PsiClass): Any {
    val project = containingClass.project
    val list: MutableList<Any?> = ArrayList()
    if (this !is PsiClassType) return list

    val parameters = this.parameters
    if (parameters.isEmpty()) return list

    val subType = parameters[0]
    val subTypeCanonicalText = subType.canonicalText

    val value = when {
        subType.isListType() -> subType.handleListDefaultValue(containingClass)
        subTypeCanonicalText == "java.lang.String" -> "str"
        subTypeCanonicalText == "java.util.Date" -> Date().time
        else -> {
            val resolvedClass = PsiTypesUtil.getPsiClass(subType)
            resolvedClass?.let { it.toDefaultValueMap() } ?: subTypeCanonicalText
        }
    }
    list.add(value)
    return list
}

fun PsiType.isListType(): Boolean {
    val canonicalText = this.canonicalText
    return canonicalText.startsWith("java.util.List") || canonicalText.startsWith("kotlin.collections.List")
}

fun PsiType.isInheritor(): Boolean {
    val inheritor = InheritanceUtil.isInheritor(this, CommonClassNames.JAVA_UTIL_COLLECTION)
    val inheritor1 = InheritanceUtil.isInheritor(this, CommonClassNames.JAVA_UTIL_MAP)
    return inheritor || inheritor1
}


fun PsiType.isCollectionType(): Boolean {

    if (this !is PsiClassType) return false

    val qualifiedName = qualifiedName() ?: return false

    val collectionType = qualifiedName.isCollectionType()

    val bool = when {
        isInheritor() -> true
        qualifiedName.isCollectionType() -> true

        else -> false
    }
    return bool
}

package site.addzero.util.lsi_impl.impl.psi.field

import site.addzero.util.lsi.assist.getDefaultValueForType
import site.addzero.util.lsi.assist.getDefaultAnyValueForType
import site.addzero.util.lsi.constant.JIMMER_COLUMN
import site.addzero.util.lsi.constant.MP_TABLE_FIELD
import site.addzero.util.lsi_impl.impl.psi.anno.getArg
import site.addzero.util.lsi_impl.impl.psi.anno.guessFieldCommentOrNull
import site.addzero.util.lsi_impl.impl.psi.clazz.toDefaultValueMap
import site.addzero.util.lsi_impl.impl.psi.clazz.toMap
import site.addzero.util.lsi_impl.impl.psi.clazz.resolveClassByName
import site.addzero.util.lsi_impl.impl.psi.type.handleListDefaultValue
import site.addzero.util.lsi_impl.impl.psi.type.isCollectionType
import site.addzero.util.lsi_impl.impl.psi.type.isListType
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import site.addzero.util.str.cleanDocComment
import java.util.*

fun PsiField.isStaticField(): Boolean {
    return hasModifierProperty(PsiModifier.STATIC)
}

fun PsiField.getColumnName(): String? {
    this.annotations.forEach { annotation ->
        when (annotation.qualifiedName) {
            JIMMER_COLUMN -> {
                annotation.getArg("name")
            }

            MP_TABLE_FIELD -> {
                annotation.getArg("value")
            }
        }
    }
    return null
}

fun PsiField.getComment(): String? {
    // 首先尝试从注解中获取描述
    val annotations1 = this.annotations
    val guessFieldComment = annotations1.guessFieldCommentOrNull()
    return guessFieldComment ?: cleanDocComment(this.docComment?.text)
}

fun PsiField.isCollectionType(): Boolean {
    return this.type.isCollectionType()
}

fun PsiField.isConstantField(): Boolean {
    return hasModifierProperty(PsiModifier.FINAL) && hasModifierProperty(PsiModifier.STATIC)
}


fun PsiField.defaultValue(project: Project): Any {
    val type = this.type

    // 处理基本类型
    if (type is PsiPrimitiveType) {
        val typeName = type.name
        return getDefaultAnyValueForType(typeName)
    }

    // 处理引用类型
    val typeName = type.presentableText

    // 先尝试获取默认值
    val defaultValue = getDefaultAnyValueForType(typeName)

    // 如果是已知的基本类型，直接返回
    if (defaultValue != typeName) {
        return defaultValue
    }

    // 处理 List 类型
    if (typeName.startsWith("List")) {
        return handleList(type, project, this.containingClass!!)
    }

    // 处理自定义类型 - 使用 containingClass.resolveClassByName
    val fieldClass = this.containingClass?.resolveClassByName(typeName, project)
    return fieldClass?.toMap() ?: typeName
}

private fun handleList(psiType: PsiType, project: Project, containingClass: PsiClass): Any {
    val list: MutableList<Any?> = ArrayList()
    val classType = psiType as PsiClassType
    val subTypes = classType.parameters
    if (subTypes.size > 0) {
        val subType = subTypes[0]
        val subTypeName = subType.presentableText
        if (subTypeName.startsWith("List")) {
            list.add(handleList(subType, project, containingClass))
        } else {
            // 使用 containingClass.resolveClassByName 代替 detectCorrectClassByName
            val targetClass = containingClass.resolveClassByName(subTypeName, project)
            if (targetClass != null) {
                list.add(targetClass.toMap())
            } else if (subTypeName == "String") {
                list.add("str")
            } else if (subTypeName == "Date") {
                list.add((Date()).time)
            } else {
                list.add(subTypeName)
            }
        }
    }

    return list
}


fun PsiField.getDefaultValue(): Any {
    val canonicalText = type.canonicalText
    if (type is PsiPrimitiveType) {
        return getDefaultAnyValueForType(canonicalText)
    }
    val defaultValue = getDefaultAnyValueForType(canonicalText)

    // 如果返回的是类型名本身（表示不是已知的基本类型），则处理特殊情况
    return if (defaultValue == canonicalText) {
        when {
            type.isListType() -> type.handleListDefaultValue(project, this.containingClass!!)
            else -> {
                val resolvedClass = PsiTypesUtil.getPsiClass(type)
                resolvedClass?.toDefaultValueMap() ?: canonicalText
            }
        }
    } else {
        defaultValue
    }
}

// 移除 getDefaultValueForFqType 函数，因为它的逻辑已经整合到统一的 getDefaultAnyValueForType 函数中


fun PsiField.addComment(project: Project) {
    // 创建新的文档注释
    val factory = PsiElementFactory.getInstance(project)
    val newDocComment = factory.createDocCommentFromText("/** */")
    addBefore(newDocComment, this.firstChild)
}


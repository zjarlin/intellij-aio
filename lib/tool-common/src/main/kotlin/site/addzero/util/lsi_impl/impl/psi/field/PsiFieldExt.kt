package site.addzero.util.lsi_impl.impl.psi.field

import site.addzero.util.lsi.assist.getDefaultValueForType
import site.addzero.util.lsi.constant.JIMMER_COLUMN
import site.addzero.util.lsi.constant.MP_TABLE_FIELD
import site.addzero.util.lsi_impl.impl.psi.anno.getArg
import site.addzero.util.lsi_impl.impl.psi.anno.guessFieldCommentOrNull
import site.addzero.util.lsi_impl.impl.psi.clazz.toDefaultValueMap
import site.addzero.util.lsi_impl.impl.psi.clazz.toMap
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
    if (type is PsiPrimitiveType) {
        return if (type == PsiType.INT) {
            0
        } else if (type == PsiType.BOOLEAN) {
            java.lang.Boolean.TRUE
        } else if (type == PsiType.BYTE) {
            "1".toByte()
        } else if (type == PsiType.CHAR) {
            '-'
        } else if (type == PsiType.DOUBLE) {
            0.0
        } else if (type == PsiType.FLOAT) {
            0.0f
        } else if (type == PsiType.LONG) {
            0L
        } else {
            if (type == PsiType.SHORT) "0".toShort() else type.getPresentableText()
        }
    } else {
        val typeName = type.presentableText
        if (typeName != "Integer" && typeName != "Long") {
            if (typeName != "Double" && typeName != "Float") {
                if (typeName == "Boolean") {
                    return java.lang.Boolean.TRUE
                } else if (typeName == "Byte") {
                    return "1".toByte()
                } else if (typeName == "String") {
                    return "str"
                } else if (typeName == "Date") {
                    return (Date()).time
                } else if (typeName.startsWith("List")) {
                    return handleList(type, project, this.containingClass!!)
                } else {
                    val fieldClass = this.detectCorrectClassByName(
                        typeName,
                        this.containingClass!!, project
                    )
                    return if (fieldClass != null) fieldClass.toMap() else typeName
                }
            } else {
                return 0.0f
            }
        } else {
            return 0
        }
    }
}

private fun handleList(psiType: PsiType, project: Project, containingClass: PsiClass): Any {
    val list: MutableList<Any?> = ArrayList()
    val classType = psiType as PsiClassType
    val subTypes = classType.parameters
    if (subTypes.size > 0) {
        val subType = subTypes[0]
        val subTypeName = subType.presentableText
        if (subTypeName.startsWith("List")) {
            list.add(this.handleList(subType, project, containingClass))
        } else {
            val targetClass = this.detectCorrectClassByName(subTypeName, containingClass, project)
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
        return getDefaultValueForType(canonicalText)
    }
    val defaultValueForFqType = getDefaultValueForFqType(canonicalText)
    return defaultValueForFqType
}

fun PsiField.getDefaultValueForFqType(canonicalText: String): Any {
    val any = when {
        canonicalText == "java.lang.Integer" || canonicalText == "java.lang.Long" -> 0
        canonicalText == "java.lang.Double" || canonicalText == "java.lang.Float" -> 0.0
        canonicalText == "java.lang.Boolean" -> true
        canonicalText == "java.lang.Byte" -> 1.toByte()
        canonicalText == "java.lang.String" -> "str"
        canonicalText == "java.util.Date" -> Date().time

        type.isListType() -> type.handleListDefaultValue(project, this.containingClass!!)
        else -> {
            val resolvedClass = PsiTypesUtil.getPsiClass(type)
            resolvedClass?.toDefaultValueMap() ?: canonicalText
        }
    }
    return any
}


fun PsiField.addComment(project: Project) {
    // 创建新的文档注释
    val factory = PsiElementFactory.getInstance(project)
    val newDocComment = factory.createDocCommentFromText("/** */")
    addBefore(newDocComment, this.firstChild)
}


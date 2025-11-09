package com.addzero.util.lsi_impl.impl.kt.field

import com.addzero.util.lsi.assist.isCollectionType
import com.addzero.util.lsi_impl.impl.kt.anno.getArg
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.isConstant
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import site.addzero.util.str.cleanDocComment
import site.addzero.util.str.removeAnyQuote

fun KtProperty.isInObject(): Boolean {
    // 检查是否是对象声明中的属性
    val isInObject = this.getParentOfType<KtObjectDeclaration>(true) != null
    return isInObject
}


fun KtProperty.isJvmStatic(): Boolean {
    val hasJvmStatic = this.annotationEntries.any {
        it.shortName?.asString() == "JvmStatic"
    }
    return hasJvmStatic
}

fun KtProperty.isStaticField(): Boolean {
    // 检查是否有 const 修饰符
    if (hasModifier(KtTokens.CONST_KEYWORD)) {
        return true
    }


    return when {
        // const 属性一定是静态的
        isConstant() -> true
        // 伴生对象中的 @JvmStatic 属性是静态的
        isInCompanionObject() && isJvmStatic() -> true

        // 对象声明中的 @JvmStatic 属性是静态的
        isInObject() && isJvmStatic() -> true

        // 顶层属性是静态的
        this.isTopLevel -> true

        else -> false
    }
}

fun KtProperty.isInCompanionObject(): Boolean {
    // 检查是否是伴生对象中的属性
    val isInCompanionObject = this.getParentOfType<KtObjectDeclaration>(true)?.isCompanion() == true
    return isInCompanionObject

}

fun KtProperty.getColumnName(): String? {
    val annotationEntries = this.annotationEntries
    val arg = annotationEntries.getArg("Column", "name")
    return arg
}

fun KtProperty.guessFieldComment(idName: String): String {
    // 如果是主键字段，直接返回 "主键"
    if (this.name == idName) {
        return "主键"
    }
    // 获取 KtProperty 上的所有注解
    val annotations = this.annotationEntries
    // 遍历所有注解
    for (annotation in annotations) {
        val qualifiedName = annotation.shortName?.asString()

        val arg1 = annotation.getArg("value")
        val des = when (qualifiedName) {
            "ApiModelProperty" -> {
                // 获取 description 参数值
                arg1
            }

            "Schema" -> {
                // 获取 description 参数值
                val des = annotation.getArg("description")
                des
            }

            "ExcelProperty" -> {
                // 获取 description 参数值
                val des = arg1
                des
            }

            else -> {
                null
            }
        }
        if (!des.isNullOrBlank()) {
            return des?.removeAnyQuote()!!
        }
    }

    // 如果没有找到 Swagger 注解，则尝试获取文档注释
    val docComment = this.docComment
    val text = cleanDocComment(docComment?.text)
    if (text.isNullOrBlank()) {
        return ""
    }

    return text
}


fun KtProperty.getComment(): String? {
    // 首先尝试从注解中获取描述
    this.annotationEntries.forEach { annotation ->
        val shortName = annotation.shortName?.asString()
        val description = when (shortName) {
            "ApiModelProperty" -> {
                // 获取第一个参数（value）
                annotation.valueArguments.firstOrNull()?.getArgumentExpression()?.text
            }

            "Schema" -> {
                // 获取 description 参数
                annotation.valueArguments.find { it.getArgumentName()?.asName?.asString() == "description" }?.getArgumentExpression()?.text
            }

            "ExcelProperty" -> {
                // 获取 value 参数
                annotation.valueArguments.find {
                    val argName = it.getArgumentName()?.asName?.asString()
                    argName == "value" || argName == null
                }?.getArgumentExpression()?.text
            }

            "Excel" -> {
                // 获取 name 参数
                annotation.valueArguments.find { it.getArgumentName()?.asName?.asString() == "name" }?.getArgumentExpression()?.text
            }

            else -> null
        }

        if (!description.isNullOrBlank()) {
            return description.removeAnyQuote()
        }
    }

    // 如果注解中没有，则返回清理后的文档注释
    return cleanDocComment(this.docComment?.text)
}

fun KtProperty.fqName(): String {
    // 获取类的全限定名
    val fqName = this.fqName?.asString() ?: ""
    return fqName
}

fun KtProperty.isCollectionType(): Boolean {
    val fqName = this.fqName()
    val collectionType = fqName.isCollectionType()
    return collectionType
}

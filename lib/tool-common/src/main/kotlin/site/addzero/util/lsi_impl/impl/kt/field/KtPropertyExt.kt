package site.addzero.util.lsi_impl.impl.kt.field

import org.jetbrains.kotlin.asJava.toLightAnnotation
import site.addzero.util.lsi.assist.isCollectionType
import site.addzero.util.lsi_impl.impl.kt.anno.getArg
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

// ============ 注解操作相关 ============

/**
 * 添加注解到 Kotlin 属性
 * 从文档注释中提取描述并格式化为注解
 *
 * @param annotationTemplate 注解模板，如 "@Schema(description = \"{}\")"
 * @param description 描述文本（可选），如果不提供则从属性的文档注释中提取
 * @param useGetter 是否添加 @get: 前缀（用于 getter 注解）
 */
fun KtProperty.addAnnotation(
    annotationTemplate: String,
    description: String? = null,
    useGetter: Boolean = true
) {
    val project = this.project

    // 获取描述文本
    val desc = description ?: getComment() ?: return
    val cleanedDesc = cleanDocComment(desc)

    if (cleanedDesc.isBlank()) return

    // 格式化注解模板
    var annotationText = annotationTemplate.replace("{}", cleanedDesc)

    // 如果需要，添加 @get: 前缀
    if (useGetter && !annotationText.startsWith("@get:")) {
        annotationText = annotationText.replace("@", "@get:")
    }

    // 创建并添加注解
    try {
        val factory = org.jetbrains.kotlin.psi.KtPsiFactory(project)
        val annotation = factory.createAnnotationEntry(annotationText)
        addAnnotationEntry(annotation)

        // 格式化代码
        com.intellij.psi.codeStyle.CodeStyleManager.getInstance(project).reformat(this)
    } catch (e: Exception) {
        // 忽略创建失败的情况（可能是格式问题）
    }
}

/**
 * 检查属性是否包含指定简单名称的注解
 *
 * @param shortNames 注解简单名称列表，如 ["Schema", "ApiModelProperty"]
 * @return 如果包含任一注解返回 true，否则返回 false
 */
fun KtProperty.hasAnnotationByShortName(vararg shortNames: String): Boolean {
    return annotationEntries.any { annotation ->
        val shortName = annotation.shortName?.asString() ?: return@any false
        shortName in shortNames
    }
}

/**
 * 检查属性是否包含指定全限定名的注解
 * 需要通过 toLightAnnotation 转换获取全限定名
 *
 * @param qualifiedNames 注解全限定名列表
 * @return 如果包含任一注解返回 true，否则返回 false
 */
fun KtProperty.hasAnnotationByQualifiedName(vararg qualifiedNames: String): Boolean {
    return annotationEntries.any { annotation ->
        val qualifiedName = annotation.toLightAnnotation()?.qualifiedName ?: return@any false
        qualifiedName in qualifiedNames
    }
}

/**
 * 获取属性上指定简单名称的所有注解
 *
 * @param shortNames 注解简单名称列表
 * @return 匹配的注解列表
 */
fun KtProperty.getAnnotationsByShortName(vararg shortNames: String): List<org.jetbrains.kotlin.psi.KtAnnotationEntry> {
    return annotationEntries.filter { annotation ->
        val shortName = annotation.shortName?.asString() ?: return@filter false
        shortName in shortNames
    }
}


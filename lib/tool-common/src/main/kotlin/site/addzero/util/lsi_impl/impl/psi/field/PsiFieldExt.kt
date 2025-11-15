package site.addzero.util.lsi_impl.impl.psi.field

import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import site.addzero.util.lsi.assist.getDefaultAnyValueForType
import site.addzero.util.lsi.constant.JIMMER_COLUMN
import site.addzero.util.lsi.constant.MP_TABLE_FIELD
import site.addzero.util.lsi_impl.impl.psi.anno.getArg
import site.addzero.util.lsi_impl.impl.psi.anno.guessFieldCommentOrNull
import site.addzero.util.lsi_impl.impl.psi.clazz.toDefaultValueMap
import site.addzero.util.lsi_impl.impl.psi.type.handleListDefaultValue
import site.addzero.util.lsi_impl.impl.psi.type.isCollectionType
import site.addzero.util.lsi_impl.impl.psi.type.isListType
import site.addzero.util.lsi_impl.impl.psi.type.toPsiClass
import site.addzero.util.str.cleanDocComment

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

/**
 * 获取字段的默认值
 * 支持：基本类型、包装类型、集合类型、自定义类型

 * @return 字段类型对应的默认值
 */
fun PsiField.getDefaultValue(): Any {
    val project = this.containingFile.project
    val type = this.type
    // 处理基本类型
    if (type is PsiPrimitiveType) {
        return getDefaultAnyValueForType(type.name)
    }
    // 对于引用类型，先尝试用全限定名获取默认值
    val canonicalText = type.canonicalText
    val defaultValue = getDefaultAnyValueForType(canonicalText)

    // 如果不是类型名本身（即找到了已知类型的默认值），直接返回
    if (defaultValue != canonicalText) {
        return defaultValue
    }
    // 对于未知的复杂类型，进一步处理
    // 处理集合类型（List、Set、Collection 等）
    if (type.isListType()) {
        return type.handleListDefaultValue(this.containingClass!!)
    }
    // 处理自定义类型 - 尝试解析类并生成其默认值 Map
    val resolvedClass = type.toPsiClass()
    val toDefaultValueMap = resolvedClass?.toDefaultValueMap()
    return toDefaultValueMap ?: canonicalText
}

fun PsiField.addComment() {
    val project = this.containingFile.project
    // 创建新的文档注释
    val factory = PsiElementFactory.getInstance(project)
    val newDocComment = factory.createDocCommentFromText("/** */")
    addBefore(newDocComment, this.firstChild)
}

// ============ 注解操作相关 ============

/**
 * 添加注解到字段
 * 从文档注释中提取描述并格式化为注解
 *
 * @param annotationTemplate 注解模板，如 "@Schema(description = \"{}\")"
 * @param description 描述文本（可选），如果不提供则从字段的文档注释中提取
 */
fun PsiField.addAnnotation(annotationTemplate: String, description: String? = null) {
    val project = this.containingFile.project

    // 获取描述文本
    val desc = description ?: getComment() ?: return
    val cleanedDesc = cleanDocComment(desc)

    if (cleanedDesc.isBlank()) return

    // 格式化注解模板
    val annotationText = annotationTemplate.replace("{}", cleanedDesc)

    // 创建并添加注解
    try {
        val factory = JavaPsiFacade.getElementFactory(project)
        val annotation = factory.createAnnotationFromText(annotationText, this)

        // 将注解添加到字段上方
        modifierList?.addBefore(annotation, modifierList?.firstChild)

        // 格式化代码
        CodeStyleManager.getInstance(project).reformat(this)
    } catch (e: Exception) {
        // 忽略创建失败的情况（可能是格式问题）
    }
}

/**
 * 检查字段是否包含指定简单名称的注解
 *
 * @param shortNames 注解简单名称列表，如 ["Schema", "ApiModelProperty"]
 * @return 如果包含任一注解返回 true，否则返回 false
 */
fun PsiField.hasAnnotationByShortName(vararg shortNames: String): Boolean {
    return annotations.any { annotation ->
        val shortName = annotation.qualifiedName?.substringAfterLast('.') ?: return@any false
        shortName in shortNames
    }
}

/**
 * 检查字段是否包含指定全限定名的注解
 *
 * @param qualifiedNames 注解全限定名列表
 * @return 如果包含任一注解返回 true，否则返回 false
 */
fun PsiField.hasAnnotationByQualifiedName(vararg qualifiedNames: String): Boolean {
    return annotations.any { annotation ->
        annotation.qualifiedName in qualifiedNames
    }
}

/**
 * 获取字段上指定简单名称的所有注解
 *
 * @param shortNames 注解简单名称列表
 * @return 匹配的注解列表
 */
fun PsiField.getAnnotationsByShortName(vararg shortNames: String): List<PsiAnnotation> {
    return annotations.filter { annotation ->
        val shortName = annotation.qualifiedName?.substringAfterLast('.') ?: return@filter false
        shortName in shortNames
    }
}


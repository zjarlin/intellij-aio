package com.addzero.util.psi

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.decompiler.psi.text.getAllModifierLists
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

/**
 * K2模式下的属性工具类
 *
 * @deprecated 此类中的大部分方法已经迁移到 LSI 层，请使用 LSI 相关方法替代
 * - 使用 KtClass.toLsiClass() 转换为 LsiClass
 * - 使用 KtProperty.toLsiField() 转换为 LsiField
 * - 使用 LsiField.hasAnnotation() 检查注解
 * - 使用 KtClass.filterPropertiesByAnnotations() 过滤带注解的属性
 */
@Deprecated("Use LSI layer instead", ReplaceWith("com.addzero.util.lsi.*"))
object K2PropertyUtil {


    /**
     * 过滤出包含指定注解的属性
     * @param ktClass Kotlin类
     * @param annotationFqNames 注解全限定名列表
     * @return 包含指定注解的属性列表
     *
     * @deprecated 使用 KtClass.filterPropertiesByAnnotations(*annotationFqNames) 替代
     * 注意：此方法使用字符串匹配，不准确。新方法使用正确的全限定名匹配
     */
    @Deprecated(
        "Use KtClass.filterPropertiesByAnnotations() from LsiExtensions instead",
        ReplaceWith(
            "ktClass.filterPropertiesByAnnotations(*annotationFqNames.toTypedArray())",
            "com.addzero.util.lsi.filterPropertiesByAnnotations"
        )
    )
    fun filterPropertiesByAnnotations2(
        ktClass: KtClass, annotationFqNames: List<String>
    ): List<KtProperty> {

        val properties = ktClass.getProperties()
        val filter = properties.filter { property ->
            property.annotationEntries.any { annotation ->
                val name = annotation.name
                val any = annotationFqNames.any {
                    annotation.text.contains(it)
                }
                any

//                val kotlinFqName = annotation.kotlinFqName?.asString()?:""
//                name in annotationFqNames
            }
        }
        return filter

    }

    /**
     * 获取属性的默认值
     * @param property Kotlin属性
     * @return 默认值字符串，如果没有则返回null
     *
     * @deprecated 使用 LsiField.defaultValue 替代
     */
    @Deprecated(
        "Use LsiField.defaultValue instead",
        ReplaceWith("property.toLsiField().defaultValue", "com.addzero.util.lsi.toLsiField")
    )
    fun getPropertyDefaultValue(property: KtProperty): String? {
        return property.initializer?.text
    }

    /**
     * 添加注解到属性
     * @param property Kotlin属性
     * @param annotationText 注解文本
     * @param project 项目实例
     *
     * @deprecated 此方法不属于元数据提取范畴，应该放在专门的 PSI 修改工具类中
     */
    @Deprecated("This method should be in a dedicated PSI modification utility class")
    fun addAnnotationToProperty(
        property: KtProperty, annotationText: String, project: Project
    ) {
        val factory = KtPsiFactory(project)
        val annotation = factory.createAnnotationEntry(annotationText)
        property.addAnnotationEntry(annotation)
    }

    /**
     * 检查属性是否是var
     * @param property Kotlin属性
     * @return 是否是var
     *
     * @deprecated 使用 LsiField.isVar 替代
     */
    @Deprecated(
        "Use LsiField.isVar instead",
        ReplaceWith("property.toLsiField().isVar", "com.addzero.util.lsi.toLsiField")
    )
    fun isVar(property: KtProperty): Boolean {
        return property.isVar
    }

    /**
     * 获取属性的文档注释
     * @param property Kotlin属性
     * @return 文档注释文本，如果没有则返回null
     *
     * @deprecated 使用 LsiField.comment 替代
     */
    @Deprecated(
        "Use LsiField.comment instead",
        ReplaceWith("property.toLsiField().comment", "com.addzero.util.lsi.toLsiField")
    )
    fun getPropertyDocComment(property: KtProperty): String? {
        return property.docComment?.text
    }

    /**
     * 检查属性是否是顶层属性
     * @param property Kotlin属性
     * @return 是否是顶层属性
     *
     * @deprecated 简单包装，直接使用 property.isTopLevel
     */
    @Deprecated("Use property.isTopLevel directly", ReplaceWith("property.isTopLevel"))
    fun isTopLevel(property: KtProperty): Boolean {
        return property.parent is KtFile
    }

    /**
     * 获取属性的所有修饰符
     * @param property Kotlin属性
     * @return 修饰符列表
     *
     * @deprecated 返回字符串列表不够结构化，建议根据具体需求使用 LsiField 的属性
     */
    @Deprecated("Use specific LsiField properties like isStatic, isVar, isLateInit instead")
    fun getPropertyModifiers(property: KtProperty): List<String> {
        val modifierList = property.modifierList
        val allModifierLists = modifierList?.getAllModifierLists()
        return allModifierLists?.map { it.text } ?: emptyList()
    }

    /**
     * 检查属性是否是const
     * @param property Kotlin属性
     * @return 是否是const
     *
     * @deprecated 使用 LsiField.isConstant 替代
     */
    @Deprecated(
        "Use LsiField.isConstant instead",
        ReplaceWith("property.toLsiField().isConstant", "com.addzero.util.lsi.toLsiField")
    )
    fun isConst(property: KtProperty): Boolean {
        return property.modifierList?.hasModifier(KtTokens.CONST_KEYWORD) ?: false
    }

    /**
     * 获取属性的getter和setter
     * @param property Kotlin属性
     * @return Pair<getter文本, setter文本>
     *
     * @deprecated 返回文本形式的访问器用途不明确，建议根据具体需求重新设计
     */
    @Deprecated("Returning text form of accessors is not useful, redesign based on specific needs")
    fun getPropertyAccessors(property: KtProperty): Pair<String?, String?> {
        return Pair(
            property.getter?.text, property.setter?.text
        )
    }

    /**
     * 检查属性是否是延迟初始化
     * @param property Kotlin属性
     * @return 是否是延迟初始化
     *
     * @deprecated 使用 LsiField.isLateInit 替代
     */
    @Deprecated(
        "Use LsiField.isLateInit instead",
        ReplaceWith("property.toLsiField().isLateInit", "com.addzero.util.lsi.toLsiField")
    )
    fun isLateInit(property: KtProperty): Boolean {
        return property.modifierList?.hasModifier(KtTokens.LATEINIT_KEYWORD) ?: false
    }


    /**
     * 注解信息数据类
     *
     * @deprecated 使用 LsiAnnotation 替代
     */
    @Deprecated("Use LsiAnnotation instead", ReplaceWith("LsiAnnotation", "com.addzero.util.lsi.LsiAnnotation"))
    data class AnnotationInfo(
        val ktProperty: KtProperty, val name: String, val arguments: List<AnnotationArgument>
    )

    /**
     * 注解参数数据类
     *
     * @deprecated 使用 LsiAnnotation.parameters 替代
     */
    @Deprecated("Use LsiAnnotation.parameters instead")
    data class AnnotationArgument(
        var ktProperty: KtProperty?, val name: String?, val value: String
    )


}


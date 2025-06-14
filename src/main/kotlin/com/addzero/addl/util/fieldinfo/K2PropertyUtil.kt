package com.addzero.addl.util.fieldinfo

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.decompiler.psi.text.getAllModifierLists
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

/**
 * K2模式下的属性工具类
 */
object K2PropertyUtil {

    /**
     * 过滤出包含指定注解的属性
     * @param ktClass Kotlin类
     * @param annotationFqNames 注解全限定名列表
     * @return 包含指定注解的属性列表
     */
    fun filterPropertiesByAnnotations(
        ktClass: KtClass, annotationFqNames: List<String>
    ): List<KtProperty> {
        return analyze(ktClass) {
            ktClass.getProperties().filter { property ->
                val symbol = property.symbol
                if (symbol is KaPropertySymbol) {
                    symbol.annotations.any { annotation ->
                        annotation.classId?.asSingleFqName()?.asString() in annotationFqNames
                    }
                } else false
            }
        }
    }


    /**
     * 过滤出包含指定注解的属性
     * @param ktClass Kotlin类
     * @param annotationFqNames 注解全限定名列表
     * @return 包含指定注解的属性列表
     */
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
     * 获取属性的类型
     * @param property Kotlin属性
     * @return 属性类型的字符串表示
     */
    fun getPropertyType(property: KtProperty): String {
        return analyze(property) {
            property.symbol.returnType.toString()
        }
    }

    /**
     * 获取属性的所有注解
     * @param property Kotlin属性
     * @return 注解全限定名列表
     */
    fun getPropertyAnnotations(property: KtProperty): List<String> {
        return analyze(property) {
            val symbol = property.symbol
            if (symbol is KaPropertySymbol) {
                symbol.annotations.mapNotNull { it.classId?.asSingleFqName()?.asString() }
            } else emptyList()
        }
    }

    /**
     * 检查属性是否可空
     * @param property Kotlin属性
     * @return 是否可空
     */
    fun isNullable(property: KtProperty): Boolean {
        return analyze(property) {
            property.symbol.returnType.isMarkedNullable
        }
    }

    /**
     * 获取属性的默认值
     * @param property Kotlin属性
     * @return 默认值字符串，如果没有则返回null
     */
    fun getPropertyDefaultValue(property: KtProperty): String? {
        return property.initializer?.text
    }

    /**
     * 添加注解到属性
     * @param property Kotlin属性
     * @param annotationText 注解文本
     * @param project 项目实例
     */
    fun addAnnotationToProperty(
        property: KtProperty, annotationText: String, project: Project
    ) {
        val factory = KtPsiFactory(project)
        val annotation = factory.createAnnotationEntry(annotationText)
        property.addAnnotationEntry(annotation)
    }

    /**
     * 获取属性的可见性修饰符
     * @param property Kotlin属性
     * @return 可见性修饰符字符串
     */
    fun getPropertyVisibility(property: KtProperty): String {
        val analyze = analyze(property) {
            property.symbol.visibility.name
        }
        return analyze
    }

    /**
     * 检查属性是否是var
     * @param property Kotlin属性
     * @return 是否是var
     */
    fun isVar(property: KtProperty): Boolean {
        return property.isVar
    }

    /**
     * 获取属性的文档注释
     * @param property Kotlin属性
     * @return 文档注释文本，如果没有则返回null
     */
    fun getPropertyDocComment(property: KtProperty): String? {
        return property.docComment?.text
    }

    /**
     * 检查属性是否是顶层属性
     * @param property Kotlin属性
     * @return 是否是顶层属性
     */
    fun isTopLevel(property: KtProperty): Boolean {
        return property.parent is KtFile
    }

    /**
     * 获取属性的所有修饰符
     * @param property Kotlin属性
     * @return 修饰符列表
     */
    fun getPropertyModifiers(property: KtProperty): List<String> {
        val modifierList = property.modifierList
        val allModifierLists = modifierList?.getAllModifierLists()
        return allModifierLists?.map { it.text } ?: emptyList()
    }

    /**
     * 检查属性是否是const
     * @param property Kotlin属性
     * @return 是否是const
     */
    fun isConst(property: KtProperty): Boolean {
        return property.modifierList?.hasModifier(KtTokens.CONST_KEYWORD) ?: false
    }

    /**
     * 获取属性的getter和setter
     * @param property Kotlin属性
     * @return Pair<getter文本, setter文本>
     */
    fun getPropertyAccessors(property: KtProperty): Pair<String?, String?> {
        return Pair(
            property.getter?.text, property.setter?.text
        )
    }

    /**
     * 检查属性是否是延迟初始化
     * @param property Kotlin属性
     * @return 是否是延迟初始化
     */
    fun isLateInit(property: KtProperty): Boolean {
        return property.modifierList?.hasModifier(KtTokens.LATEINIT_KEYWORD) ?: false
    }


    /**
     * 获取属性的注解信息
     * @param property Kotlin属性
     * @return 注解信息列表
     */
    fun getPropertyAnnotationInfo(property: KtProperty): List<AnnotationInfo> {
        return analyze(property) {
            val symbol = property.symbol
            if (symbol is KaPropertySymbol) {
                symbol.annotations.map { annotation ->
                    AnnotationInfo(
                        ktProperty = property,
                        name = annotation.classId?.asSingleFqName()?.asString() ?: "",
                        arguments = annotation.arguments.map { arg ->
                            AnnotationArgument(
                                name = arg.name?.asString(),
                                ktProperty = property,
                                value = when (val value = arg.expression) {
                                    is KaConstantValue -> value.value.toString()
                                    else -> value.toString()
                                }
                            )
                        })
                }
            } else emptyList()
        }
    }

    fun processKotlinAnnotation(annotation: KtAnnotationEntry): List<AnnotationArgument> {
        // 获取所有参数
        val valueArguments = annotation.valueArguments
        val map = valueArguments.map {
            val argName = it.getArgumentName()?.asName?.identifier // 参数名（如 "value"）
            val argValue = it.getArgumentExpression()?.text // 参数值（如 "42" 或 "\"text\""）
            AnnotationArgument(
                ktProperty = null, name = argName, value = argValue.toString()
            )

        }
        return map
    }

    /**
     * 获取属性的注解信息
     * @param property Kotlin属性
     * @return 注解信息列表
     */
    fun getPropertyAnnotationInfo2(property: KtProperty): List<AnnotationInfo> {
        val map1 = property.annotationEntries.map { annotation ->
            val processKotlinAnnotation = processKotlinAnnotation(annotation)
            processKotlinAnnotation.forEach { it.ktProperty = property }
            AnnotationInfo(
                ktProperty = property,
                name = annotation.kotlinFqName?.asString() ?: "",
                arguments = processKotlinAnnotation
            )
        }

        return map1
    }


    /**
     * 注解信息数据类
     */
    data class AnnotationInfo(
        val ktProperty: KtProperty, val name: String, val arguments: List<AnnotationArgument>
    )

    /**
     * 注解参数数据类
     */
    data class AnnotationArgument(
        var ktProperty: KtProperty?, val name: String?, val value: String
    )

    /**
     * 获取指定注解的参数值
     * @param property Kotlin属性
     * @param annotationName 注解全限定名
     * @param paramName 参数名
     * @return 参数值，如果不存在则返回null
     */
    fun getAnnotationParameterValue(
        property: KtProperty, annotationName: String, paramName: String
    ): String? {
        return analyze(property) {
            val symbol = property.symbol
            if (symbol is KaPropertySymbol) {
                symbol.annotations.find {
                    it.classId?.asSingleFqName()?.asString() == annotationName
                }?.arguments?.find { it.name?.asString() == paramName }?.let { arg ->
                    when (val value = arg.expression) {
                        is KaConstantValue -> value.value.toString()
                        else -> value.toString()
                    }
                }
            } else null
        }
    }

    /**
     * 检查属性是否有指定注解
     * @param property Kotlin属性
     * @param annotationName 注解全限定名
     * @return 是否有该注解
     */
    fun hasAnnotation(property: KtProperty, annotationName: String): Boolean {
        return analyze(property) {
            val symbol = property.symbol
            if (symbol is KaPropertySymbol) {
                symbol.annotations.any { it.classId?.asSingleFqName()?.asString() == annotationName }
            } else false
        }
    }


}

/**
 * 使用示例
 */
//fun example(ktClass: KtClass) {
//    // 过滤出带有特定注解的属性
//    val propertiesWithAnnotation = K2PropertyUtil.filterPropertiesByAnnotations(
//        ktClass,
//        listOf("javax.persistence.Column", "jakarta.persistence.Column")
//    )
//
//    // 处理每个属性
//    propertiesWithAnnotation.forEach { property ->
//        // 获取属性类型
//        val type = K2PropertyUtil.getPropertyType(property)
//
//        // 获取属性注解
//        val annotations = K2PropertyUtil.getPropertyAnnotations(property)
//
//        // 检查是否可空
//        val isNullable = K2PropertyUtil.isNullable(property)
//
//        // 获取文档注释
//        val docComment = K2PropertyUtil.getPropertyDocComment(property)
//
//        // 获取可见性
//        val visibility = K2PropertyUtil.getPropertyVisibility(property)
//
//        // 获取修饰符
//        val modifiers = K2PropertyUtil.getPropertyModifiers(property)
//
//        // 获取访问器
//        val (getter, setter) = K2PropertyUtil.getPropertyAccessors(property)
//
//        // 获取类型参数
////        val typeParameters = K2PropertyUtil.getPropertyTypeParameters(property)
//
//        // 使用这些信息进行后续处理
//        println("""
//            属性名: ${property.name}
//            类型: $type
//            注解: $annotations
//            可空: $isNullable
//            文档: $docComment
//            可见性: $visibility
//            修饰符: $modifiers
//            Getter: $getter
//            Setter: $setter
//        """.trimIndent())
//    }
//}

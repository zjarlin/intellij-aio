package com.addzero.util.lsi

import com.addzero.util.lsi.impl.kt.KtLsiClass
import com.addzero.util.lsi.impl.kt.KtLsiField
import com.addzero.util.lsi.impl.psi.PsiLsiClass
import com.addzero.util.lsi.impl.psi.PsiLsiField
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty

/**
 * LSI 扩展函数 - 提供 PSI/Kt 到 LSI 的便捷转换
 */

/**
 * 将 PsiClass 转换为 LsiClass
 */
fun PsiClass.toLsiClass(): LsiClass = PsiLsiClass(this)

/**
 * 将 KtClass 转换为 LsiClass
 */
fun KtClass.toLsiClass(): LsiClass = KtLsiClass(this)

/**
 * 将 PsiField 转换为 LsiField
 */
fun PsiField.toLsiField(): LsiField = PsiLsiField(this)

/**
 * 将 KtProperty 转换为 LsiField
 */
fun KtProperty.toLsiField(): LsiField = KtLsiField(this)

/**
 * 判断 PsiField 是否为数据库字段
 * 委托给 LSI 层实现
 */
fun PsiField.isDbField(): Boolean = toLsiField().isDbField

/**
 * 判断 KtProperty 是否为数据库字段
 * 委托给 LSI 层实现
 */
fun KtProperty.isDbField(): Boolean = toLsiField().isDbField

/**
 * 获取 PsiClass 的数据库字段列表
 * 委托给 LSI 层实现
 */
fun PsiClass.getDbFields(): List<LsiField> = toLsiClass().dbFields

/**
 * 获取 KtClass 的数据库字段列表
 * 委托给 LSI 层实现
 */
fun KtClass.getDbFields(): List<LsiField> = toLsiClass().dbFields

/**
 * 获取 PsiClass 的所有数据库字段（包括继承的）
 * 委托给 LSI 层实现
 */
fun PsiClass.getAllDbFields(): List<LsiField> = toLsiClass().getAllDbFields()

/**
 * 获取 KtClass 的所有数据库字段（包括继承的）
 * 委托给 LSI 层实现
 */
fun KtClass.getAllDbFields(): List<LsiField> = toLsiClass().getAllDbFields()

/**
 * 过滤具有指定注解的属性
 * 委托给 LSI 层实现
 * @param annotationFqNames 注解全限定名列表
 * @return 包含指定注解的属性列表
 */
fun KtClass.filterPropertiesByAnnotations(vararg annotationFqNames: String): List<LsiField> {
    return toLsiClass().fields.filter { field ->
        field.hasAnnotation(*annotationFqNames)
    }
}

/**
 * 过滤具有指定注解的字段
 * 委托给 LSI 层实现
 * @param annotationFqNames 注解全限定名列表
 * @return 包含指定注解的字段列表
 */
fun PsiClass.filterFieldsByAnnotations(vararg annotationFqNames: String): List<LsiField> {
    return toLsiClass().fields.filter { field ->
        field.hasAnnotation(*annotationFqNames)
    }
}

/**
 * 获取字段注解的指定参数值
 * @param annotationFqName 注解全限定名
 * @param parameterName 参数名称
 * @return 参数值，如果不存在则返回 null
 */
fun LsiField.getAnnotationParameter(annotationFqName: String, parameterName: String): String? {
    val annotation = annotations.find { it.qualifiedName == annotationFqName } ?: return null
    return annotation.parameters[parameterName]
}

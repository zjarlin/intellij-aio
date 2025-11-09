package com.addzero.util.lsi_impl.impl.psi

import com.addzero.util.lsi.clazz.LsiClass
import com.addzero.util.lsi.field.LsiField
import com.addzero.util.lsi.method.LsiMethod
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod

/**
 * PSI 到 LSI 的扩展函数
 * 提供 PsiClass, PsiField, PsiMethod 到 LSI 层的便捷转换
 */

/**
 * 将 PsiClass 转换为 LsiClass
 */
fun PsiClass.toLsiClass(): LsiClass = PsiLsiClass(this)

/**
 * 将 PsiField 转换为 LsiField
 */
fun PsiField.toLsiField(): LsiField = PsiLsiField(this)

/**
 * 将 PsiMethod 转换为 LsiMethod
 */
fun PsiMethod.toLsiMethod(): LsiMethod = PsiLsiMethod(this)

/**
 * 判断 PsiField 是否为数据库字段
 * 委托给 LSI 层实现
 */
fun PsiField.isDbField(): Boolean = toLsiField().isDbField

/**
 * 获取 PsiClass 的数据库字段列表
 * 委托给 LSI 层实现
 */
fun PsiClass.getDbFields(): List<LsiField> = toLsiClass().dbFields

/**
 * 获取 PsiClass 的所有数据库字段（包括继承的）
 * 委托给 LSI 层实现
 */
fun PsiClass.getAllDbFields(): List<LsiField> = toLsiClass().getAllDbFields()

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
 * 判断 PsiClass 是否为 POJO
 * 委托给 LSI 层实现
 */
fun PsiClass.isPojo(): Boolean = toLsiClass().isPojo

/**
 * 从 Jimmer 接口实体的方法中提取字段信息
 * Jimmer 接口实体的字段通过 getter 方法定义
 * 支持 @JoinColumn 注解的关联字段处理
 *
 * @return 字段列表（基于方法定义的伪字段）
 */
fun PsiClass.extractInterfaceFields(): List<LsiMethod> {
    if (!isInterface) {
        return emptyList()
    }

    return methods.filter { method ->
        // 排除返回集合类型的方法
        method.returnType?.let { returnType ->
            val psiLsiType = PsiLsiType(returnType)
            !psiLsiType.isCollectionType
        } ?: false
    }
}

/**
 * 获取 Java 类的字段元数据信息
 * 如果是接口，则从方法提取；如果是普通类，则从字段提取
 *
 * @return 字段列表
 */
fun PsiClass.getJavaFields(): List<LsiField> {
    if (isInterface) {
        // 接口：字段定义在方法中（Jimmer 模式）
        // 这里无法直接返回 LsiField，因为方法不是字段
        // 调用者应该使用 extractInterfaceFields() 然后自行处理
        return emptyList()
    }

    // 普通类：直接使用 LSI 的 dbFields
    return toLsiClass().dbFields
}

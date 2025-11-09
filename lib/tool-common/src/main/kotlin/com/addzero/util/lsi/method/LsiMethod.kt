package com.addzero.util.lsi.method

import com.addzero.util.lsi.anno.LsiAnnotation
import com.addzero.util.lsi.clazz.LsiClass
import com.addzero.util.lsi.type.LsiType

/**
 * 语言无关的方法结构抽象接口
 * Lsi = Language Structure Interface
 */
interface LsiMethod {
    /**
     * 获取方法名称
     */
    val name: String?

    /**
     * 获取方法返回类型
     */
    val returnType: LsiType?

    /**
     * 获取方法返回类型名称
     */
    val returnTypeName: String?

    /**
     * 获取方法注释
     */
    val comment: String?

    /**
     * 获取方法上的注解
     */
    val annotations: List<LsiAnnotation>

    /**
     * 判断是否为静态方法
     */
    val isStatic: Boolean

    /**
     * 判断是否为抽象方法
     */
    val isAbstract: Boolean

    /**
     * 获取方法参数列表
     */
    val parameters: List<LsiParameter>

    /**
     * 获取声明该方法的类
     */
    val declaringClass: LsiClass?

}

/**
 * 语言无关的方法参数抽象接口
 */
interface LsiParameter {
    /**
     * 获取参数名称
     */
    val name: String?

    /**
     * 获取参数类型
     */
    val type: LsiType?

    /**
     * 获取参数类型名称
     */
    val typeName: String?

    /**
     * 获取参数上的注解
     */
    val annotations: List<LsiAnnotation>
}


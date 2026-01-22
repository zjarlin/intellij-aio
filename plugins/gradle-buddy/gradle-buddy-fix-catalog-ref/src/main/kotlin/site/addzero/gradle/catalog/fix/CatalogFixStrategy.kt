package site.addzero.gradle.catalog.fix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.project.Project
import site.addzero.gradle.catalog.CatalogReferenceError

/**
 * 版本目录引用修复策略接口
 */
interface CatalogFixStrategy {
    /**
     * 判断是否支持修复此类型的错误
     */
    fun support(error: CatalogReferenceError): Boolean

    /**
     * 创建快速修复
     */
    fun createFix(project: Project, error: CatalogReferenceError): LocalQuickFix?

    /**
     * 获取修复描述
     */
    fun getFixDescription(error: CatalogReferenceError): String
}

package site.addzero.gradle.catalog.fix

import com.intellij.openapi.project.Project
import site.addzero.gradle.catalog.CatalogReferenceError

/**
 * 版本目录修复策略工厂
 */
object CatalogFixStrategyFactory {

    private val strategies: List<CatalogFixStrategy> = listOf(
        WrongFormatFixStrategy(),
        NotDeclaredFixStrategy()
    )

    /**
     * 根据错误类型获取合适的修复策略
     */
    fun getStrategy(error: CatalogReferenceError): CatalogFixStrategy? {
        return strategies.firstOrNull { it.support(error) }
    }

    /**
     * 获取所有支持的策略
     */
    fun getAllStrategies(): List<CatalogFixStrategy> {
        return strategies
    }
}

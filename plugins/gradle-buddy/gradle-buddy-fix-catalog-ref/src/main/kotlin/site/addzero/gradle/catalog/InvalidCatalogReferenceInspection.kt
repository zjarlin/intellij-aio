package site.addzero.gradle.catalog

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import site.addzero.gradle.catalog.fix.CatalogFixStrategyFactory

/**
 * 检查 Gradle 构建文件中的版本目录引用是否有效
 */
class InvalidCatalogReferenceInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "无效的版本目录引用"

    override fun getShortName(): String = "InvalidCatalogReference"

    override fun getGroupDisplayName(): String = "Gradle"

    override fun getStaticDescription(): String? {
        return "检查 Gradle 版本目录引用是否有效，包括格式错误和未声明的依赖"
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                super.visitDotQualifiedExpression(expression)

                if (isPartialCatalogReference(expression)) {
                    return
                }

                // 检查是否是版本目录引用
                if (!isCatalogReference(expression)) {
                    return
                }

                val project = holder.project
                val scanner = CatalogReferenceScanner(project)
                val catalogs = scanner.scanAllCatalogs()

                // 提取引用的目录名和别名
                val (catalogName, reference) = extractCatalogReference(expression) ?: return

                // 检查目录是否存在
                val availableAliases = catalogs[catalogName]
                if (availableAliases == null) {
                    // 目录不存在，跳过检查
                    return
                }

                // 检查别名是否存在
                if (availableAliases.contains(reference)) {
                    // 引用正确，无需修复
                    return
                }

                // 判断错误类型
                val error = detectErrorType(catalogName, reference, availableAliases)

                // 获取合适的修复策略
                val strategy = CatalogFixStrategyFactory.getStrategy(error)
                if (strategy == null) {
                    // 没有合适的策略，跳过
                    return
                }

                // 创建快速修复
                val fix = strategy.createFix(project, error)
                if (fix == null) {
                    // 无法创建修复，只报告问题
                    holder.registerProblem(
                        expression,
                        strategy.getFixDescription(error),
                        ProblemHighlightType.WARNING
                    )
                } else {
                    holder.registerProblem(
                        expression,
                        strategy.getFixDescription(error),
                        ProblemHighlightType.WARNING,
                        fix
                    )
                }
            }
        }
    }

    /**
     * 判断表达式是否是版本目录引用
     * 例如: libs.gradle.plugin.ksp
     */
    private fun isCatalogReference(expression: KtDotQualifiedExpression): Boolean {
        // 获取最左边的标识符
        var current = expression
        while (current.receiverExpression is KtDotQualifiedExpression) {
            current = current.receiverExpression as KtDotQualifiedExpression
        }

        val rootName = (current.receiverExpression as? KtNameReferenceExpression)?.getReferencedName()

        // 常见的版本目录名称
        val catalogNames = setOf("libs", "zlibs", "klibs", "testLibs")
        return rootName in catalogNames
    }

    /**
     * 提取目录名和引用路径
     * 例如: libs.gradle.plugin.ksp -> ("libs", "gradle.plugin.ksp")
     */
    private fun extractCatalogReference(expression: KtDotQualifiedExpression): Pair<String, String>? {
        var topExpression = expression
        while (topExpression.parent is KtDotQualifiedExpression) {
            topExpression = topExpression.parent as KtDotQualifiedExpression
        }
        val fullText = topExpression.text
        val parts = fullText.split(".")

        if (parts.size < 2) {
            return null
        }

        val catalogName = parts[0]
        val reference = parts.drop(1).joinToString(".")

        return catalogName to reference
    }

    private fun isPartialCatalogReference(expression: KtDotQualifiedExpression): Boolean {
        val parent = expression.parent as? KtDotQualifiedExpression ?: return false
        return parent.receiverExpression == expression
    }

    /**
     * 检测错误类型
     */
    private fun detectErrorType(
        catalogName: String,
        invalidReference: String,
        availableAliases: Set<String>
    ): CatalogReferenceError {
        // 尝试找到正确的格式
        val correctReference = findCorrectFormat(invalidReference, availableAliases)

        return if (correctReference != null) {
            // 找到了正确格式，说明是格式错误
            CatalogReferenceError.WrongFormat(
                catalogName = catalogName,
                invalidReference = invalidReference,
                correctReference = correctReference,
                availableAliases = availableAliases
            )
        } else {
            // 找不到正确格式，说明未声明
            CatalogReferenceError.NotDeclared(
                catalogName = catalogName,
                invalidReference = invalidReference,
                availableAliases = availableAliases
            )
        }
    }

    /**
     * 尝试找到正确的格式
     * 例如: gradlePlugin.ksp -> gradle.plugin.ksp
     */
    private fun findCorrectFormat(invalidReference: String, availableAliases: Set<String>): String? {
        // 尝试各种可能的转换
        val candidates = mutableSetOf<String>()

        // 1. 驼峰转点分隔
        // gradlePlugin.ksp -> gradle.plugin.ksp
        val camelCaseConverted = invalidReference.replace(Regex("([a-z])([A-Z])")) { matchResult ->
            "${matchResult.groupValues[1]}.${matchResult.groupValues[2].lowercase()}"
        }
        candidates.add(camelCaseConverted)

        // 2. 下划线转点
        candidates.add(invalidReference.replace('_', '.'))

        // 3. 连字符转点
        candidates.add(invalidReference.replace('-', '.'))

        // 4. 全小写
        candidates.add(invalidReference.lowercase())

        // 查找匹配的别名
        return candidates.firstOrNull { it in availableAliases }
    }
}

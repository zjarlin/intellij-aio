package site.addzero.gradle.catalog

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
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

                if (CatalogExpressionUtils.isPartialCatalogReference(expression)) {
                    return
                }

                if (!CatalogExpressionUtils.isCatalogReference(expression)) {
                    return
                }

                // 跳过包含方法调用的表达式（如 libs.findVersion("jdk").orElse(null)）
                if (CatalogExpressionUtils.containsMethodCall(expression)) {
                    return
                }

                val project = holder.project
                val scanner = CatalogReferenceScanner(project)
                val catalogs = scanner.scanAllCatalogs()

                val (catalogName, reference) = CatalogExpressionUtils.extractCatalogReference(expression) ?: return

                val availableAliases = catalogs[catalogName] ?: return

                if (availableAliases.contains(reference)) {
                    return
                }

                val error = CatalogExpressionUtils.detectErrorType(catalogName, reference, availableAliases)

                val strategy = CatalogFixStrategyFactory.getStrategy(error) ?: return

                val fix = strategy.createFix(project, error)
                if (fix == null) {
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
}

package site.addzero.gradle.catalog

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * 版本目录表达式的共享工具方法
 * 提供 isCatalogReference、extractCatalogReference、containsMethodCall 等通用逻辑，
 * 避免在 Inspection / Intention 中重复实现。
 */
@Suppress("SpellCheckingInspection")
object CatalogExpressionUtils {

    /** 支持的版本目录名称 */
    private val CATALOG_NAMES = setOf("libs", "zlibs", "klibs", "testLibs")

    /** 获取表达式链中最顶层的 KtDotQualifiedExpression */
    fun getTopExpression(expression: KtDotQualifiedExpression): KtDotQualifiedExpression {
        var top = expression
        while (top.parent is KtDotQualifiedExpression) {
            top = top.parent as KtDotQualifiedExpression
        }
        return top
    }

    /**
     * 判断表达式是否是版本目录引用
     * 例如: libs.gradle.plugin.ksp
     */
    fun isCatalogReference(expression: KtDotQualifiedExpression): Boolean {
        val topExpression = getTopExpression(expression)
        if (containsForbiddenSegment(topExpression)) return false

        var current = topExpression
        while (current.receiverExpression is KtDotQualifiedExpression) {
            current = current.receiverExpression as KtDotQualifiedExpression
        }

        val rootName = (current.receiverExpression as? KtNameReferenceExpression)?.getReferencedName()
        return rootName in CATALOG_NAMES
    }

    /**
     * 提取目录名和引用路径
     * 例如: libs.gradle.plugin.ksp -> ("libs", "gradle.plugin.ksp")
     */
    fun extractCatalogReference(expression: KtDotQualifiedExpression): Pair<String, String>? {
        val topExpression = getTopExpression(expression)
        val fullText = topExpression.text
        val parts = fullText.split(".")

        if (parts.size < 2) {
            return null
        }

        val catalogName = parts[0]
        val reference = parts.drop(1).joinToString(".")

        return catalogName to reference
    }

    /** 检查表达式是否包含 .javaClass 等不应被视为目录引用的片段 */
    fun containsForbiddenSegment(expression: KtDotQualifiedExpression): Boolean {
        val fullText = expression.text
        return fullText.contains(".javaClass") || fullText.endsWith(".javaClass")
    }

    /**
     * 检查表达式是否包含方法调用
     * 例如: libs.findVersion("jdk").orElse(null) 包含 findVersion() 和 orElse() 方法调用
     * 这些是合法的 API 调用，不应该被当作版本目录引用来验证
     */
    fun containsMethodCall(expression: KtDotQualifiedExpression): Boolean {
        val topExpression = getTopExpression(expression)

        fun hasCallExpression(expr: KtDotQualifiedExpression): Boolean {
            if (expr.selectorExpression is KtCallExpression) {
                return true
            }
            val receiver = expr.receiverExpression
            if (receiver is KtDotQualifiedExpression) {
                return hasCallExpression(receiver)
            }
            return false
        }

        return hasCallExpression(topExpression)
    }

    /** 判断表达式是否只是更大目录引用链的中间节点 */
    fun isPartialCatalogReference(expression: KtDotQualifiedExpression): Boolean {
        val parent = expression.parent as? KtDotQualifiedExpression ?: return false
        return parent.receiverExpression == expression
    }

    /**
     * 从 element 向上找到最顶层的 KtDotQualifiedExpression
     */
    fun findTopDotExpression(element: PsiElement): KtDotQualifiedExpression? {
        var dotExpression = PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java)
            ?: return null
        while (dotExpression.parent is KtDotQualifiedExpression) {
            dotExpression = dotExpression.parent as KtDotQualifiedExpression
        }
        return dotExpression
    }

    /**
     * 从 element 向上找到目标 KtDotQualifiedExpression，
     * 并严格校验其文本与预期的 catalogName.oldReference 完全匹配
     */
    fun findTargetDotExpression(
        element: PsiElement,
        catalogName: String,
        oldReference: String
    ): KtDotQualifiedExpression? {
        val dotExpression = findTopDotExpression(element) ?: return null
        val expectedText = "$catalogName.$oldReference"
        if (dotExpression.text != expectedText) {
            return null
        }
        return dotExpression
    }

    /**
     * 直接替换已定位的 dotExpression，防止双重 catalogName
     */
    fun replaceDotExpression(
        project: com.intellij.openapi.project.Project,
        dotExpression: KtDotQualifiedExpression,
        catalogName: String,
        newReference: String
    ) {
        val cleanRef = if (newReference.startsWith("$catalogName.")) {
            newReference.removePrefix("$catalogName.")
        } else {
            newReference
        }

        val newFullReference = "$catalogName.$cleanRef"
        val factory = KtPsiFactory(project)
        val newExpression = factory.createExpression(newFullReference)
        dotExpression.replace(newExpression)
    }

    /**
     * 检测错误类型：格式错误 或 未声明
     */
    fun detectErrorType(
        catalogName: String,
        invalidReference: String,
        availableAliases: Set<String>
    ): CatalogReferenceError {
        val correctReference = findCorrectFormat(invalidReference, availableAliases)

        return if (correctReference != null) {
            CatalogReferenceError.WrongFormat(
                catalogName = catalogName,
                invalidReference = invalidReference,
                correctReference = correctReference,
                availableAliases = availableAliases
            )
        } else {
            val matcher = AliasSimilarityMatcher()
            val suggestedAliases = matcher.findSimilarAliases(invalidReference, availableAliases)

            CatalogReferenceError.NotDeclared(
                catalogName = catalogName,
                invalidReference = invalidReference,
                availableAliases = availableAliases,
                suggestedAliases = suggestedAliases
            )
        }
    }

    /**
     * 尝试找到正确的格式
     * 例如: gradlePlugin.ksp -> gradle.plugin.ksp
     */
    fun findCorrectFormat(invalidReference: String, availableAliases: Set<String>): String? {
        val variants = listOf(
            invalidReference,
            AliasSimilarityMatcher.stripGradleVersionSuffix(invalidReference)
        ).distinct()

        for (ref in variants) {
            val candidates = mutableSetOf<String>()

            // 驼峰转点分隔
            val camelCaseConverted = ref.replace(Regex("([a-z])([A-Z])")) { matchResult ->
                "${matchResult.groupValues[1]}.${matchResult.groupValues[2].lowercase()}"
            }
            candidates.add(camelCaseConverted)

            candidates.add(ref.replace('_', '.'))
            candidates.add(ref.replace('-', '.'))
            candidates.add(ref.lowercase())
            candidates.add(ref)

            val match = candidates.firstOrNull { it in availableAliases }
            if (match != null) return match
        }

        return null
    }

    /**
     * 根据断裂引用的类型过滤候选项
     * 如果断裂引用是 library（不以 versions./plugins./bundles. 开头），
     * 则排除 versions.xxx 候选项，避免出现 libs.versions.xxx 的错误替换
     */
    fun filterCandidatesForReference(
        invalidReference: String,
        candidates: List<AliasSimilarityMatcher.MatchResult>
    ): List<AliasSimilarityMatcher.MatchResult> {
        val prefixes = listOf("versions.", "plugins.", "bundles.")
        val isLibraryRef = prefixes.none { invalidReference.startsWith(it) }
        if (!isLibraryRef) return candidates
        return candidates.filter { !it.alias.startsWith("versions.") }
    }
}

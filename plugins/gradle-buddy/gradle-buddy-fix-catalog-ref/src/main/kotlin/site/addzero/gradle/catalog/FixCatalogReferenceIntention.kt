package site.addzero.gradle.catalog

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.ProblemGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import site.addzero.gradle.catalog.fix.CatalogFixStrategyFactory

/**
 * 修复版本目录引用的意图操作
 * 在 Alt+Enter 时提供快速修复
 */
class FixCatalogReferenceIntention : IntentionAction, PriorityAction {

    private var cachedErrorDescription: String = "修复版本目录引用"

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle Buddy"

    override fun getText(): String = cachedErrorDescription

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (file !is KtFile) {
            println("[FixCatalogReferenceIntention] Not a KtFile: ${file.javaClass.simpleName}")
            return false
        }
        if (!file.name.endsWith(".gradle.kts")) {
            println("[FixCatalogReferenceIntention] Not a gradle.kts file: ${file.name}")
            return false
        }

        val offset = editor?.caretModel?.offset ?: run {
            println("[FixCatalogReferenceIntention] No editor or offset")
            return false
        }
        val element = file.findElementAt(offset) ?: run {
            println("[FixCatalogReferenceIntention] No element at offset $offset")
            return false
        }

        println("[FixCatalogReferenceIntention] Checking element: ${element.text} at offset $offset")

        // 查找版本目录引用
        val error = detectCatalogReferenceError(project, element) ?: run {
            println("[FixCatalogReferenceIntention] No catalog reference error detected")
            return false
        }

        println("[FixCatalogReferenceIntention] Detected error: $error")

        // 检查是否有可用的修复策略
        val strategy = CatalogFixStrategyFactory.getStrategy(error) ?: run {
            println("[FixCatalogReferenceIntention] No strategy available for error")
            return false
        }

        println("[FixCatalogReferenceIntention] Strategy: ${strategy.javaClass.simpleName}")

        // 更新缓存的错误描述
        cachedErrorDescription = strategy.getFixDescription(error)

        val hasFix = strategy.createFix(project, error) != null
        println("[FixCatalogReferenceIntention] Has fix: $hasFix")

        return hasFix
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (file !is KtFile) return
        if (!file.name.endsWith(".gradle.kts")) return

        val offset = editor?.caretModel?.offset ?: return
        val element = file.findElementAt(offset) ?: return

        // 检测错误
        val error = detectCatalogReferenceError(project, element) ?: return

        // 获取修复策略
        val strategy = CatalogFixStrategyFactory.getStrategy(error) ?: return
        val fix = strategy.createFix(project, error) ?: return

        // 创建一个简单的 ProblemDescriptor 实现
        val descriptor = SimpleProblemDescriptor(element, strategy.getFixDescription(error))

        // 应用修复（不在 write action 中，让 fix 自己决定何时执行 write action）
        fix.applyFix(project, descriptor)
    }

    /**
     * 检测版本目录引用错误
     */
    private fun detectCatalogReferenceError(project: Project, element: com.intellij.psi.PsiElement): CatalogReferenceError? {
        // 查找包含此元素的点限定表达式
        val dotExpression = PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java)
            ?: run {
                println("[detectCatalogReferenceError] No dot expression found")
                return null
            }

        println("[detectCatalogReferenceError] Found dot expression: ${dotExpression.text}")

        // 检查是否是版本目录引用
        if (!isCatalogReference(dotExpression)) {
            println("[detectCatalogReferenceError] Not a catalog reference")
            return null
        }

        // 提取目录名和引用
        val (catalogName, reference) = extractCatalogReference(dotExpression) ?: run {
            println("[detectCatalogReferenceError] Failed to extract catalog reference")
            return null
        }

        println("[detectCatalogReferenceError] Catalog: $catalogName, Reference: $reference")

        // 扫描 TOML 文件
        val scanner = CatalogReferenceScanner(project)
        val catalogs = scanner.scanAllCatalogs()

        println("[detectCatalogReferenceError] Found catalogs: ${catalogs.keys}")
        catalogs.forEach { (name, aliases) ->
            println("[detectCatalogReferenceError]   $name: ${aliases.take(5)}...")
        }

        // 检查目录是否存在
        val availableAliases = catalogs[catalogName] ?: run {
            println("[detectCatalogReferenceError] Catalog '$catalogName' not found")
            return null
        }

        println("[detectCatalogReferenceError] Available aliases in '$catalogName': ${availableAliases.take(10)}")

        // 检查别名是否存在
        if (availableAliases.contains(reference)) {
            // 引用正确，无需修复
            println("[detectCatalogReferenceError] Reference '$reference' is valid")
            return null
        }

        println("[detectCatalogReferenceError] Reference '$reference' is invalid")

        // 判断错误类型
        return detectErrorType(catalogName, reference, availableAliases)
    }

    /**
     * 判断表达式是否是版本目录引用
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
     */
    private fun extractCatalogReference(expression: KtDotQualifiedExpression): Pair<String, String>? {
        val fullText = expression.text
        val parts = fullText.split(".")

        if (parts.size < 2) {
            return null
        }

        val catalogName = parts[0]
        val reference = parts.drop(1).joinToString(".")

        return catalogName to reference
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
            // 找不到正确格式，使用相似度匹配查找候选项
            val matcher = AliasSimilarityMatcher()
            val suggestedAliases = matcher.findSimilarAliases(invalidReference, availableAliases, topN = 5)

            println("[detectErrorType] Found ${suggestedAliases.size} similar aliases")

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
     */
    private fun findCorrectFormat(invalidReference: String, availableAliases: Set<String>): String? {
        // 尝试各种可能的转换
        val candidates = mutableSetOf<String>()

        // 1. 驼峰转点分隔
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

    /**
     * 简单的 ProblemDescriptor 实现
     */
    private class SimpleProblemDescriptor(
        private val element: PsiElement,
        private val description: String
    ) : ProblemDescriptor {
        override fun getPsiElement() = element
        override fun getStartElement() = element
        override fun getEndElement() = element
        override fun getTextRangeInElement(): TextRange = element.textRange
        override fun getLineNumber() = 0
        override fun getDescriptionTemplate() = description
        override fun getHighlightType() = ProblemHighlightType.WARNING
        override fun getFixes() = null
        override fun isAfterEndOfLine() = false
        override fun setTextAttributes(p0: TextAttributesKey) {}
        override fun getProblemGroup(): ProblemGroup? = null
        override fun setProblemGroup(p0: ProblemGroup?) {}
        override fun showTooltip() = true
    }
}

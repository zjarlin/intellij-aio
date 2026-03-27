package site.addzero.gradle.catalog

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * 为 build-logic / buildSrc 中的动态 catalog API 提供 TOML 跳转与重命名：
 * - libs.findLibrary("alias").get()
 * - libs.findPlugin("alias").get()
 * - libs.findBundle("alias").get()
 * - libs.findVersion("alias").get()
 */
class DynamicCatalogReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val stringExpression = element as? KtStringTemplateExpression ?: return PsiReference.EMPTY_ARRAY
                    val alias = extractLiteralString(stringExpression) ?: return PsiReference.EMPTY_ARRAY
                    val callInfo = resolveDynamicCatalogCall(stringExpression, alias) ?: return PsiReference.EMPTY_ARRAY

                    return arrayOf(DynamicCatalogStringReference(stringExpression, callInfo))
                }
            }
        )
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtLiteralStringTemplateEntry::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val literalEntry = element as? KtLiteralStringTemplateEntry ?: return PsiReference.EMPTY_ARRAY
                    val stringExpression = literalEntry.parent as? KtStringTemplateExpression ?: return PsiReference.EMPTY_ARRAY
                    val alias = extractLiteralString(stringExpression) ?: return PsiReference.EMPTY_ARRAY
                    val callInfo = resolveDynamicCatalogCall(stringExpression, alias) ?: return PsiReference.EMPTY_ARRAY

                    return arrayOf(
                        DynamicCatalogLiteralEntryReference(
                            literalEntry = literalEntry,
                            stringExpression = stringExpression,
                            callInfo = callInfo
                        )
                    )
                }
            }
        )
    }

    private fun extractLiteralString(expression: KtStringTemplateExpression): String? {
        if (expression.entries.isEmpty()) return null
        if (expression.entries.any { it !is KtLiteralStringTemplateEntry }) return null
        return expression.entries
            .filterIsInstance<KtLiteralStringTemplateEntry>()
            .joinToString(separator = "") { it.text }
            .takeIf { it.isNotBlank() }
    }

    private fun resolveDynamicCatalogCall(
        stringExpression: KtStringTemplateExpression,
        alias: String
    ): DynamicCatalogCallInfo? {
        val callExpression = PsiTreeUtil.getParentOfType(stringExpression, KtCallExpression::class.java) ?: return null
        if (callExpression.valueArguments.singleOrNull()?.getArgumentExpression() != stringExpression) {
            return null
        }

        val methodName = callExpression.calleeExpression?.text ?: return null
        val tableName = when (methodName) {
            "findLibrary" -> "libraries"
            "findPlugin" -> "plugins"
            "findBundle" -> "bundles"
            "findVersion" -> "versions"
            else -> return null
        }

        val receiverExpression = (callExpression.parent as? org.jetbrains.kotlin.psi.KtDotQualifiedExpression)
            ?.receiverExpression ?: return null
        val catalogName = resolveCatalogName(receiverExpression, stringExpression.containingFile) ?: return null

        return DynamicCatalogCallInfo(
            catalogName = catalogName,
            tableName = tableName,
            alias = alias
        )
    }

    private fun resolveCatalogName(receiverExpression: KtExpression, file: PsiElement): String? {
        return when (receiverExpression) {
            is KtNameReferenceExpression -> resolveCatalogNameFromVariable(receiverExpression.getReferencedName(), file as? KtFile)
            else -> resolveCatalogNameFromExpression(receiverExpression)
        }
    }

    private fun resolveCatalogNameFromVariable(variableName: String, file: KtFile?): String? {
        if (file == null) return variableName

        val properties = PsiTreeUtil.collectElementsOfType(file, KtProperty::class.java)
        val matchingProperty = properties.firstOrNull { it.name == variableName }
        if (matchingProperty != null) {
            return resolveCatalogNameFromExpression(matchingProperty.initializer) ?: variableName
        }
        return variableName
    }

    private fun resolveCatalogNameFromExpression(expression: KtExpression?): String? {
        val text = expression?.text ?: return null
        return VERSION_CATALOG_NAMED_REGEX.find(text)?.groupValues?.get(1)
    }

    private class DynamicCatalogStringReference(
        element: KtStringTemplateExpression,
        private val callInfo: DynamicCatalogCallInfo
    ) : PsiPolyVariantReferenceBase<KtStringTemplateExpression>(
        element,
        TextRange(1, element.textLength - 1),
        true
    ) {

        override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
            val scanner = CatalogReferenceScanner(element.project)
            return scanner.findEntries(callInfo.catalogName, callInfo.tableName, callInfo.alias)
                .map { entry -> PsiElementResolveResult(entry) }
                .toTypedArray()
        }

        override fun handleElementRename(newElementName: String): PsiElement {
            val newExpression = KtPsiFactory(element.project).createExpression("\"$newElementName\"")
            return element.replace(newExpression)
        }

        override fun getVariants(): Array<Any> = emptyArray()
    }

    private class DynamicCatalogLiteralEntryReference(
        literalEntry: KtLiteralStringTemplateEntry,
        private val stringExpression: KtStringTemplateExpression,
        private val callInfo: DynamicCatalogCallInfo
    ) : PsiPolyVariantReferenceBase<KtLiteralStringTemplateEntry>(
        literalEntry,
        TextRange(0, literalEntry.textLength),
        true
    ) {

        override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
            val scanner = CatalogReferenceScanner(element.project)
            return scanner.findEntries(callInfo.catalogName, callInfo.tableName, callInfo.alias)
                .map { entry -> PsiElementResolveResult(entry) }
                .toTypedArray()
        }

        override fun handleElementRename(newElementName: String): PsiElement {
            val newExpression = KtPsiFactory(element.project).createExpression("\"$newElementName\"")
            return stringExpression.replace(newExpression)
        }

        override fun getVariants(): Array<Any> = emptyArray()
    }

    private data class DynamicCatalogCallInfo(
        val catalogName: String,
        val tableName: String,
        val alias: String
    )

    companion object {
        private val VERSION_CATALOG_NAMED_REGEX =
            Regex("""versionCatalogs\s*\.\s*named\s*\(\s*"([^"]+)"\s*\)""")
    }
}

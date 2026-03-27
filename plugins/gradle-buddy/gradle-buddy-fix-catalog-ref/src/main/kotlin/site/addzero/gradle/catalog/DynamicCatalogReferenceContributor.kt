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
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
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
                    val callInfo = DynamicCatalogReferenceSupport.resolveDynamicCatalogCall(stringExpression)
                        ?: return PsiReference.EMPTY_ARRAY

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
                    val callInfo = DynamicCatalogReferenceSupport.resolveDynamicCatalogCall(stringExpression)
                        ?: return PsiReference.EMPTY_ARRAY

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

    private typealias DynamicCatalogCallInfo = DynamicCatalogReferenceSupport.DynamicCatalogCallInfo
}

package site.addzero.gradle.catalog

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * 为动态版本目录 API 的字符串参数提供补全。
 * 支持场景：
 * - libs.findLibrary("...").get()
 * - libs.findPlugin("...").get()
 * - libs.findBundle("...").get()
 * - libs.findVersion("...").get()
 */
class DynamicCatalogCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement()
                .inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".gradle.kts"))),
            DynamicCatalogCompletionProvider()
        )
    }
}

private class DynamicCatalogCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val project = parameters.position.project
        val stringExpression = DynamicCatalogReferenceSupport.findTargetStringExpression(parameters.position) ?: return
        val callInfo = DynamicCatalogReferenceSupport.resolveDynamicCatalogCall(stringExpression) ?: return
        val prefix = currentPrefix(stringExpression, parameters.offset) ?: return

        val aliases = DynamicCatalogReferenceSupport.loadAvailableAliases(project, callInfo)
        if (aliases.isEmpty()) {
            return
        }

        val completionResult = result.withPrefixMatcher(prefix)
        val displayType = DynamicCatalogReferenceSupport.tableDisplayName(callInfo.tableName)

        aliases.sorted().forEach { alias ->
            completionResult.addElement(
                createLookupElement(
                    alias = alias,
                    displayType = displayType,
                    stringExpression = stringExpression
                )
            )
        }

        result.stopHere()
    }

    private fun createLookupElement(
        alias: String,
        displayType: String,
        stringExpression: KtStringTemplateExpression
    ): LookupElement {
        return LookupElementBuilder.create(alias)
            .withPresentableText(alias)
            .withTypeText(displayType, true)
            .withLookupStrings(buildAlternativeLookupStrings(alias))
            .withInsertHandler(replaceWholeStringContent(stringExpression, alias))
    }

    /**
     * 允许用户使用点分隔或下划线分隔前缀继续匹配 kebab-case alias。
     * 例如输入 site.addzero.ks 也能命中 site-addzero-ksp-support。
     */
    private fun buildAlternativeLookupStrings(alias: String): Set<String> {
        return linkedSetOf(
            alias,
            alias.replace('-', '.'),
            alias.replace('-', '_')
        )
    }

    private fun replaceWholeStringContent(
        stringExpression: KtStringTemplateExpression,
        alias: String
    ): InsertHandler<LookupElement> {
        val rangeStart = stringExpression.textRange.startOffset + 1

        return InsertHandler { insertionContext, _ ->
            PsiDocumentManager.getInstance(insertionContext.project).commitDocument(insertionContext.document)

            val file = insertionContext.file
            val anchor = file.findElementAt((insertionContext.startOffset - 1).coerceAtLeast(0))
                ?: file.findElementAt(insertionContext.startOffset)
                ?: return@InsertHandler

            val currentStringExpression = PsiTreeUtil.getParentOfType(anchor, KtStringTemplateExpression::class.java, false)
                ?.takeIf { DynamicCatalogReferenceSupport.resolveDynamicCatalogCall(it) != null }
                ?: return@InsertHandler

            val contentStart = currentStringExpression.textRange.startOffset + 1
            val contentEnd = currentStringExpression.textRange.endOffset - 1
            insertionContext.document.replaceString(contentStart, contentEnd, alias)
            insertionContext.editor.caretModel.moveToOffset(rangeStart + alias.length)
            PsiDocumentManager.getInstance(insertionContext.project).commitDocument(insertionContext.document)
        }
    }

    private fun currentPrefix(
        stringExpression: KtStringTemplateExpression,
        offset: Int
    ): String? {
        val contentStart = stringExpression.textRange.startOffset + 1
        val contentEnd = stringExpression.textRange.endOffset - 1
        if (offset < contentStart || offset > contentEnd) {
            return null
        }

        val text = stringExpression.text
        val relativeEnd = (offset - stringExpression.textRange.startOffset).coerceIn(1, text.length - 1)
        return text.substring(1, relativeEnd)
    }
}

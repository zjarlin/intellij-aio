package site.addzero.gradle.catalog

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.toml.lang.psi.TomlKeyValue

internal object DynamicCatalogReferenceSupport {

    fun resolveTargetEntry(element: PsiElement): TomlKeyValue? {
        resolveFromReferences(element)?.let { return it }

        val stringExpression = PsiTreeUtil.getParentOfType(element, KtStringTemplateExpression::class.java, false)
            ?: return null
        return resolveTargetEntry(stringExpression)
    }

    fun resolveTargetEntry(file: PsiFile, offset: Int): TomlKeyValue? {
        val offsets = buildList {
            if (offset in 0 until file.textLength) add(offset)
            if (offset - 1 in 0 until file.textLength) add(offset - 1)
        }.distinct()

        for (candidateOffset in offsets) {
            file.findReferenceAt(candidateOffset)?.resolveAsTomlKeyValue()?.let { return it }
            val leaf = file.findElementAt(candidateOffset) ?: continue
            resolveTargetEntry(leaf)?.let { return it }
        }
        return null
    }

    fun resolveTargetEntry(stringExpression: KtStringTemplateExpression): TomlKeyValue? {
        val callInfo = resolveDynamicCatalogCall(stringExpression) ?: return null
        return CatalogReferenceScanner(stringExpression.project)
            .findEntries(callInfo.catalogName, callInfo.tableName, callInfo.alias)
            .firstOrNull()
    }

    fun resolveDynamicCatalogCall(stringExpression: KtStringTemplateExpression): DynamicCatalogCallInfo? {
        val alias = extractLiteralString(stringExpression) ?: return null
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

    fun extractLiteralString(expression: KtStringTemplateExpression): String? {
        if (expression.entries.isEmpty()) return null
        if (expression.entries.any { it !is KtLiteralStringTemplateEntry }) return null
        return expression.entries
            .filterIsInstance<KtLiteralStringTemplateEntry>()
            .joinToString(separator = "") { it.text }
            .takeIf { it.isNotBlank() }
    }

    private fun resolveFromReferences(element: PsiElement): TomlKeyValue? {
        var current: PsiElement? = element
        repeat(5) {
            current?.references
                ?.firstNotNullOfOrNull { it.resolveAsTomlKeyValue() }
                ?.let { return it }
            current = current?.parent
        }
        return null
    }

    private fun PsiReference.resolveAsTomlKeyValue(): TomlKeyValue? {
        return resolve() as? TomlKeyValue
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

    data class DynamicCatalogCallInfo(
        val catalogName: String,
        val tableName: String,
        val alias: String
    )

    private val VERSION_CATALOG_NAMED_REGEX =
        Regex("""versionCatalogs\s*\.\s*named\s*\(\s*"([^"]+)"\s*\)""")
}

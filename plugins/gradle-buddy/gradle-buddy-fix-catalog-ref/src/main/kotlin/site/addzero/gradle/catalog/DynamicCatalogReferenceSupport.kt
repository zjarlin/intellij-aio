package site.addzero.gradle.catalog

import com.intellij.openapi.project.Project
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
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.toml.lang.psi.TomlKeyValue

internal object DynamicCatalogReferenceSupport {

    fun resolveTargetEntry(element: PsiElement): TomlKeyValue? {
        resolveFromReferences(element)?.let { return it }

        val stringExpression = findTargetStringExpression(element) ?: return null
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

    fun resolveDynamicCatalogCall(element: PsiElement): DynamicCatalogCallInfo? {
        val stringExpression = findTargetStringExpression(element) ?: return null
        return resolveDynamicCatalogCall(stringExpression)
    }

    fun resolveDynamicCatalogCall(stringExpression: KtStringTemplateExpression): DynamicCatalogCallInfo? {
        val alias = extractLiteralString(stringExpression, allowBlank = true) ?: return null
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

    fun detectCatalogReferenceError(project: Project, element: PsiElement): CatalogReferenceError? {
        val stringExpression = findTargetStringExpression(element) ?: return null
        return detectCatalogReferenceError(project, stringExpression)
    }

    fun detectCatalogReferenceError(project: Project, stringExpression: KtStringTemplateExpression): CatalogReferenceError? {
        val callInfo = resolveDynamicCatalogCall(stringExpression) ?: return null
        if (callInfo.alias.isBlank()) {
            return null
        }
        val availableAliases = loadAvailableAliases(project, callInfo)
        if (availableAliases.isEmpty()) {
            return null
        }
        if (callInfo.alias in availableAliases) {
            return null
        }

        val correctReference = findCorrectAliasFormat(callInfo.alias, availableAliases)
        return if (correctReference != null) {
            CatalogReferenceError.WrongFormat(
                catalogName = callInfo.catalogName,
                invalidReference = callInfo.alias,
                correctReference = correctReference,
                availableAliases = availableAliases
            )
        } else {
            val matcher = AliasSimilarityMatcher()
            CatalogReferenceError.NotDeclared(
                catalogName = callInfo.catalogName,
                invalidReference = callInfo.alias,
                availableAliases = availableAliases,
                suggestedAliases = matcher.findSimilarAliases(callInfo.alias, availableAliases)
            )
        }
    }

    fun loadAvailableAliases(project: Project, callInfo: DynamicCatalogCallInfo): Set<String> {
        return CatalogReferenceScanner(project)
            .scanCatalogTableAliases(callInfo.catalogName, callInfo.tableName)
    }

    fun findTargetStringExpression(element: PsiElement): KtStringTemplateExpression? {
        return PsiTreeUtil.getParentOfType(element, KtStringTemplateExpression::class.java, false)
            ?.takeIf { resolveDynamicCatalogCall(it) != null }
    }

    fun findTargetStringExpression(
        element: PsiElement,
        catalogName: String,
        alias: String,
        tableName: String? = null
    ): KtStringTemplateExpression? {
        val stringExpression = findTargetStringExpression(element) ?: return null
        val callInfo = resolveDynamicCatalogCall(stringExpression) ?: return null
        if (callInfo.catalogName != catalogName) {
            return null
        }
        if (callInfo.alias != alias) {
            return null
        }
        if (tableName != null && callInfo.tableName != tableName) {
            return null
        }
        return stringExpression
    }

    fun replaceStringExpression(
        project: Project,
        stringExpression: KtStringTemplateExpression,
        newAlias: String
    ) {
        val newExpression = KtPsiFactory(project).createExpression("\"$newAlias\"")
        stringExpression.replace(newExpression)
    }

    fun extractLiteralString(expression: KtStringTemplateExpression, allowBlank: Boolean = false): String? {
        if (expression.entries.isEmpty()) {
            return if (allowBlank) "" else null
        }
        if (expression.entries.any { it !is KtLiteralStringTemplateEntry }) return null
        val text = expression.entries
            .filterIsInstance<KtLiteralStringTemplateEntry>()
            .joinToString(separator = "") { it.text }
        return if (allowBlank || text.isNotBlank()) text else null
    }

    fun tableDisplayName(tableName: String): String {
        return when (tableName) {
            "libraries" -> "library"
            "plugins" -> "plugin"
            "bundles" -> "bundle"
            "versions" -> "version"
            else -> tableName
        }
    }

    private fun findCorrectAliasFormat(invalidReference: String, availableAliases: Set<String>): String? {
        val normalized = canonicalizeAliasKey(invalidReference)
        return availableAliases.firstOrNull { canonicalizeAliasKey(it) == normalized }
    }

    private fun canonicalizeAliasKey(key: String): String {
        return key
            .trim('"', '\'')
            .replace('-', '.')
            .replace('_', '.')
            .split('.')
            .flatMap { segment ->
                segment
                    .replace(Regex("([a-z0-9])([A-Z])"), "$1.$2")
                    .split('.')
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = ".") { it.lowercase() }
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

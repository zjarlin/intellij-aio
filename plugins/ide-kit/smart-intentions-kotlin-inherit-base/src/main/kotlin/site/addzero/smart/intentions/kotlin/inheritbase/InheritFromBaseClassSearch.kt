package site.addzero.smart.intentions.kotlin.inheritbase

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache

internal object InheritFromBaseClassSearch {
    private const val MAX_DIRECT_NAME_MATCHES = 30
    private const val MAX_PREFIX_MATCHES = 40
    private const val MAX_FALLBACK_NAMES = 600

    fun findChoices(project: Project, query: String): List<BaseTypeChoice> {
        val normalizedQuery = normalizeQuery(query)
        if (normalizedQuery.isBlank()) {
            return emptyList()
        }
        return ReadAction.compute<List<BaseTypeChoice>, RuntimeException> {
            val scope = GlobalSearchScope.allScope(project)
            val cache = PsiShortNamesCache.getInstance(project)
            val directChoices = findDirectChoices(cache, scope, normalizedQuery)
            val prefixChoices = findPrefixChoices(cache, scope, normalizedQuery)
            (directChoices + prefixChoices)
                .distinctBy { choice -> choice.qualifiedName ?: choice.typeText }
                .sortedWith(compareBy<BaseTypeChoice> { choice -> rankChoice(choice, normalizedQuery) }
                    .thenBy { choice -> choice.displayName }
                    .thenBy { choice -> choice.packageName.orEmpty() })
                .take(MAX_PREFIX_MATCHES)
        }
    }

    fun findExactChoice(project: Project, typeText: String): BaseTypeChoice? {
        val normalizedTypeText = normalizeQuery(typeText)
        if (normalizedTypeText.isBlank()) {
            return null
        }
        return ReadAction.compute<BaseTypeChoice?, RuntimeException> {
            val scope = GlobalSearchScope.allScope(project)
            val cache = PsiShortNamesCache.getInstance(project)
            findDirectChoices(cache, scope, normalizedTypeText)
                .firstOrNull { choice -> isExactChoice(choice, normalizedTypeText) }
        }
    }

    private fun findDirectChoices(
        cache: PsiShortNamesCache,
        scope: GlobalSearchScope,
        query: String,
    ): List<BaseTypeChoice> {
        val className = query.substringAfterLast('.').substringBefore('<').trim()
        if (!StringUtil.isJavaIdentifier(className)) {
            return emptyList()
        }
        return cache.getClassesByName(className, scope)
            .asSequence()
            .filter(::isSupportedBaseClass)
            .map { psiClass -> psiClass.toChoice() }
            .take(MAX_DIRECT_NAME_MATCHES)
            .toList()
    }

    private fun findPrefixChoices(
        cache: PsiShortNamesCache,
        scope: GlobalSearchScope,
        query: String,
    ): List<BaseTypeChoice> {
        val simpleQuery = query.substringAfterLast('.').substringBefore('<').trim()
        if (simpleQuery.length < 2) {
            return emptyList()
        }
        return cache.allClassNames
            .asSequence()
            .filter { className -> className.contains(simpleQuery, ignoreCase = true) }
            .take(MAX_FALLBACK_NAMES)
            .flatMap { className -> cache.getClassesByName(className, scope).asSequence() }
            .filter(::isSupportedBaseClass)
            .map { psiClass -> psiClass.toChoice() }
            .take(MAX_PREFIX_MATCHES)
            .toList()
    }

    private fun isSupportedBaseClass(psiClass: PsiClass): Boolean {
        if (psiClass is PsiTypeParameter) {
            return false
        }
        if (!psiClass.isValid) {
            return false
        }
        val qualifiedName = psiClass.qualifiedName
        if (qualifiedName != null && qualifiedName.startsWith("kotlin.reflect.")) {
            return false
        }
        if (!psiClass.isInterface && !psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return false
        }
        return psiClass.name?.isNotBlank() == true
    }

    private fun PsiClass.toChoice(): BaseTypeChoice {
        val simpleName = name.orEmpty()
        val qualifiedName = qualifiedName
        val packageName = qualifiedName?.substringBeforeLast('.', missingDelimiterValue = "")?.ifBlank { null }
        val typeParameterNames = typeParameters.mapIndexed { index, parameter ->
            parameter.name?.takeIf { name -> name.isNotBlank() } ?: "T${index + 1}"
        }
        return BaseTypeChoice(
            displayName = simpleName,
            typeText = qualifiedName ?: simpleName,
            qualifiedName = qualifiedName,
            packageName = packageName,
            typeParameterNames = typeParameterNames,
        )
    }

    private fun rankChoice(choice: BaseTypeChoice, query: String): Int {
        val simpleQuery = query.substringAfterLast('.').substringBefore('<').trim()
        if (choice.qualifiedName == query) {
            return 0
        }
        if (choice.displayName == simpleQuery) {
            return 1
        }
        if (choice.displayName.equals(simpleQuery, ignoreCase = true)) {
            return 2
        }
        if (choice.displayName.startsWith(simpleQuery, ignoreCase = true)) {
            return 3
        }
        return 4
    }

    private fun isExactChoice(choice: BaseTypeChoice, query: String): Boolean {
        val simpleQuery = query.substringAfterLast('.').substringBefore('<').trim()
        return choice.qualifiedName == query || choice.displayName == simpleQuery
    }

    private fun normalizeQuery(query: String): String {
        return query.trim().removeSuffix("()")
    }
}

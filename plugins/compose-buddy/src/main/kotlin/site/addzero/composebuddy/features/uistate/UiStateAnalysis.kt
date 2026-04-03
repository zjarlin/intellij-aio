package site.addzero.composebuddy.features.uistate

import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.composebuddy.support.ComposePsiSupport

data class UiStateProperty(
    val propertyName: String,
    val typeText: String,
)

data class UiStateCandidate(
    val parameter: KtParameter,
    val properties: List<UiStateProperty>,
)

data class UiStateAnalysisResult(
    val function: KtNamedFunction,
    val candidates: List<UiStateCandidate>,
)

object UiStateAnalysis {
    fun analyze(function: KtNamedFunction): UiStateAnalysisResult? {
        if (!ComposePsiSupport.isComposable(function)) return null
        val body = function.bodyExpression ?: return null
        val candidates = function.valueParameters.mapNotNull { parameter ->
            val parameterName = parameter.name ?: return@mapNotNull null
            if (parameter.typeReference?.text?.contains("->") == true) return@mapNotNull null
            val directRefs = ReferencesSearch.search(parameter, LocalSearchScope(body))
                .findAll()
                .mapNotNull { it.element.parent as? KtDotQualifiedExpression }
            if (directRefs.any { it.receiverExpression.text != parameterName }) return@mapNotNull null
            val propertyNames = linkedSetOf<String>()
            body.collectDescendantsOfType<KtDotQualifiedExpression>().forEach { dot ->
                if (dot.receiverExpression.text != parameterName) return@forEach
                val selector = dot.selectorExpression?.text ?: return@forEach
                if (selector.contains("(")) return@forEach
                propertyNames += selector
            }
            if (propertyNames.isEmpty()) return@mapNotNull null
            val typeMap = resolvePropertyTypes(parameter)
            val properties = propertyNames.mapNotNull { name ->
                typeMap[name]?.let { UiStateProperty(name, it) }
            }
            if (properties.isEmpty()) return@mapNotNull null
            UiStateCandidate(parameter, properties)
        }
        if (candidates.isEmpty()) return null
        return UiStateAnalysisResult(function, candidates)
    }

    private fun resolvePropertyTypes(parameter: KtParameter): Map<String, String> {
        val resolved = resolveKtClass(parameter)
        if (resolved != null) {
            val map = linkedMapOf<String, String>()
            resolved.primaryConstructorParameters.forEach { ctor ->
                if (!ctor.hasValOrVar()) return@forEach
                val name = ctor.name ?: return@forEach
                val type = ctor.typeReference?.text ?: return@forEach
                map[name] = type
            }
            resolved.declarations.filterIsInstance<KtProperty>().forEach { property ->
                val name = property.name ?: return@forEach
                val type = property.typeReference?.text ?: return@forEach
                map[name] = type
            }
            return map
        }
        val psiClass = resolvePsiClass(parameter) ?: return emptyMap()
        return psiClass.allFields.associate { it.name to it.type.presentableText }
    }

    private fun resolveKtClass(parameter: KtParameter): KtClass? {
        val userType = parameter.typeReference?.typeElement as? KtUserType ?: return null
        val resolved = userType.referenceExpression?.mainReference?.resolve()
        if (resolved is KtClass) {
            return resolved
        }
        val shortName = userType.referencedName ?: return null
        return parameter.containingKtFile.declarations.filterIsInstance<KtClass>().firstOrNull { it.name == shortName }
    }

    private fun resolvePsiClass(parameter: KtParameter): PsiClass? {
        val typeText = parameter.typeReference?.text ?: return null
        val shortName = typeText.substringBefore("<").substringAfterLast(".")
        if (shortName.isBlank()) return null
        return PsiShortNamesCache.getInstance(parameter.project)
            .getClassesByName(shortName, GlobalSearchScope.projectScope(parameter.project))
            .firstOrNull()
    }
}

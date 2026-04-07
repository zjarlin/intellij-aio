package site.addzero.composebuddy.features.viewmodelinline

import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.composebuddy.support.ComposePsiSupport

data class ViewModelStateUsage(
    val pathSegments: List<String>,
    val parameterName: String,
    val typeText: String,
    val sourceKind: ViewModelStateSourceKind,
)

enum class ViewModelStateSourceKind {
    INPUT,
    COMPUTED,
}

data class ViewModelEventUsage(
    val name: String,
    val receiverPathSegments: List<String>,
    val parameterName: String,
    val parameterTypes: List<String>,
    val returnType: String,
)

data class ViewModelInlineCandidate(
    val parameter: KtParameter?,
    val localProperty: KtProperty?,
    val states: List<ViewModelStateUsage>,
    val events: List<ViewModelEventUsage>,
    val keepOriginalParameter: Boolean,
)

data class ViewModelInlineAnalysisResult(
    val function: KtNamedFunction,
    val candidates: List<ViewModelInlineCandidate>,
)

object ViewModelInlineAnalysis {
    fun analyze(function: KtNamedFunction): ViewModelInlineAnalysisResult? {
        if (!ComposePsiSupport.isComposable(function)) return null
        val body = function.bodyExpression ?: return null
        val parameterCandidates = function.valueParameters.mapNotNull { parameter ->
            val parameterName = parameter.name ?: return@mapNotNull null
            if (!looksLikeViewModel(parameter)) return@mapNotNull null
            val usages = body.collectDescendantsOfType<KtDotQualifiedExpression>()
                .mapNotNull { extractUsage(parameterName, it) }
            if (usages.isEmpty()) return@mapNotNull null

            val resolvedType = resolveTypeNode(parameter)
            val states = linkedMapOf<String, ViewModelStateUsage>()
            val events = linkedMapOf<String, ViewModelEventUsage>()
            usages.forEach { usage ->
                when (usage) {
                    is AccessUsage.Property -> {
                        val propertyResolution = resolvedType?.resolvePropertyUsage(usage.pathSegments) ?: return@forEach
                        val parameterAlias = buildParameterName(usage.pathSegments)
                        states[usage.pathSegments.joinToString(".")] = ViewModelStateUsage(
                            pathSegments = usage.pathSegments,
                            parameterName = parameterAlias,
                            typeText = propertyResolution.typeText,
                            sourceKind = propertyResolution.sourceKind,
                        )
                    }
                    is AccessUsage.Method -> {
                        val signature = resolvedType?.resolveMethodUsage(usage.receiverPathSegments, usage.name) ?: return@forEach
                        events[(usage.receiverPathSegments + usage.name).joinToString(".")] = signature.copy(
                            receiverPathSegments = usage.receiverPathSegments,
                            parameterName = buildParameterName(usage.receiverPathSegments + usage.name),
                        )
                    }
                }
            }
            val prunedStates = states.values.filterNot { state ->
                val statePath = state.pathSegments
                states.values.any { other ->
                    other !== state &&
                        other.pathSegments.size > statePath.size &&
                        other.pathSegments.take(statePath.size) == statePath
                }
            }
            if (states.isEmpty() && events.isEmpty()) return@mapNotNull null
            ViewModelInlineCandidate(
                parameter = parameter,
                localProperty = null,
                states = prunedStates.filter { it.sourceKind == ViewModelStateSourceKind.INPUT },
                events = events.values.toList(),
                keepOriginalParameter = prunedStates.any { it.sourceKind == ViewModelStateSourceKind.COMPUTED },
            )
        }
        val localCandidates = body.collectDescendantsOfType<KtProperty>()
            .mapNotNull { property ->
                val propertyName = property.name ?: return@mapNotNull null
                if (!looksLikeViewModel(property)) return@mapNotNull null
                val usages = body.collectDescendantsOfType<KtDotQualifiedExpression>()
                    .mapNotNull { extractUsage(propertyName, it) }
                if (usages.isEmpty()) return@mapNotNull null

                val resolvedType = resolveTypeNode(property)
                val states = linkedMapOf<String, ViewModelStateUsage>()
                val events = linkedMapOf<String, ViewModelEventUsage>()
                usages.forEach { usage ->
                    when (usage) {
                        is AccessUsage.Property -> {
                            val propertyResolution = resolvedType?.resolvePropertyUsage(usage.pathSegments) ?: return@forEach
                            val parameterAlias = buildParameterName(usage.pathSegments)
                            states[usage.pathSegments.joinToString(".")] = ViewModelStateUsage(
                                pathSegments = usage.pathSegments,
                                parameterName = parameterAlias,
                                typeText = propertyResolution.typeText,
                                sourceKind = propertyResolution.sourceKind,
                            )
                        }
                        is AccessUsage.Method -> {
                            val signature = resolvedType?.resolveMethodUsage(usage.receiverPathSegments, usage.name) ?: return@forEach
                            events[(usage.receiverPathSegments + usage.name).joinToString(".")] = signature.copy(
                                receiverPathSegments = usage.receiverPathSegments,
                                parameterName = buildParameterName(usage.receiverPathSegments + usage.name),
                            )
                        }
                    }
                }
                val prunedStates = states.values.filterNot { state ->
                    val statePath = state.pathSegments
                    states.values.any { other ->
                        other !== state &&
                            other.pathSegments.size > statePath.size &&
                            other.pathSegments.take(statePath.size) == statePath
                    }
                }
                if (states.isEmpty() && events.isEmpty()) return@mapNotNull null
                ViewModelInlineCandidate(
                    parameter = null,
                    localProperty = property,
                    states = prunedStates.filter { it.sourceKind == ViewModelStateSourceKind.INPUT },
                    events = events.values.toList(),
                    keepOriginalParameter = prunedStates.any { it.sourceKind == ViewModelStateSourceKind.COMPUTED },
                )
            }
        val candidates = parameterCandidates + localCandidates
        if (candidates.isEmpty()) return null
        return ViewModelInlineAnalysisResult(function, candidates)
    }

    private fun looksLikeViewModel(parameter: KtParameter): Boolean {
        val name = parameter.name.orEmpty()
        val type = parameter.typeReference?.text.orEmpty()
        return name.contains("viewModel", ignoreCase = true) || type.contains("ViewModel")
    }

    private fun looksLikeViewModel(property: KtProperty): Boolean {
        val name = property.name.orEmpty()
        val type = property.typeReference?.text.orEmpty()
        return name.contains("viewModel", ignoreCase = true) || type.contains("ViewModel")
    }

    private fun resolveTypeNode(parameter: KtParameter): TypeNode? {
        resolveKtClass(parameter)?.let { return TypeNode.KotlinType(it) }
        resolvePsiClass(parameter)?.let { return TypeNode.JavaType(it) }
        return null
    }

    private fun resolveTypeNode(property: KtProperty): TypeNode? {
        resolveKtClass(property)?.let { return TypeNode.KotlinType(it) }
        resolvePsiClass(property)?.let { return TypeNode.JavaType(it) }
        return null
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

    private fun resolveKtClass(property: KtProperty): KtClass? {
        val userType = property.typeReference?.typeElement as? KtUserType ?: return null
        val resolved = userType.referenceExpression?.mainReference?.resolve()
        if (resolved is KtClass) {
            return resolved
        }
        val shortName = userType.referencedName ?: return null
        return property.containingKtFile.declarations.filterIsInstance<KtClass>().firstOrNull { it.name == shortName }
    }

    private fun resolvePsiClass(parameter: KtParameter): PsiClass? {
        val typeText = parameter.typeReference?.text ?: return null
        val shortName = typeText.substringBefore("<").substringAfterLast(".")
        if (shortName.isBlank()) return null
        return PsiShortNamesCache.getInstance(parameter.project)
            .getClassesByName(shortName, GlobalSearchScope.projectScope(parameter.project))
            .firstOrNull()
    }

    private fun resolvePsiClass(property: KtProperty): PsiClass? {
        val typeText = property.typeReference?.text ?: return null
        val shortName = typeText.substringBefore("<").substringAfterLast(".")
        if (shortName.isBlank()) return null
        return PsiShortNamesCache.getInstance(property.project)
            .getClassesByName(shortName, GlobalSearchScope.projectScope(property.project))
            .firstOrNull()
    }

    private fun extractUsage(parameterName: String, expression: KtDotQualifiedExpression): AccessUsage? {
        val chain = collectChain(expression) ?: return null
        if (chain.segments.firstOrNull() != parameterName) return null
        if (chain.segments.size < 2) return null
        return if (chain.isCall) {
            AccessUsage.Method(
                receiverPathSegments = chain.segments.drop(1).dropLast(1),
                name = chain.segments.last(),
            )
        } else {
            AccessUsage.Property(chain.segments.drop(1))
        }
    }

    private fun collectChain(expression: KtExpression): AnalysisAccessChain? {
        return when (expression) {
            is KtDotQualifiedExpression -> {
                val receiver = collectChain(expression.receiverExpression) ?: return null
                when (val selector = expression.selectorExpression) {
                    is org.jetbrains.kotlin.psi.KtNameReferenceExpression -> AnalysisAccessChain(receiver.segments + selector.getReferencedName(), false)
                    is KtCallExpression -> {
                        val callee = selector.calleeExpression?.text ?: return null
                        AnalysisAccessChain(receiver.segments + callee, true)
                    }
                    else -> null
                }
            }
            is org.jetbrains.kotlin.psi.KtNameReferenceExpression -> AnalysisAccessChain(listOf(expression.getReferencedName()), false)
            else -> null
        }
    }

    private fun buildParameterName(pathSegments: List<String>): String {
        if (pathSegments.isEmpty()) return "value"
        if (pathSegments.size == 1) return pathSegments.single()
        return pathSegments.first() + pathSegments.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    }
}

private data class AnalysisAccessChain(
    val segments: List<String>,
    val isCall: Boolean,
)

private sealed interface AccessUsage {
    data class Property(val pathSegments: List<String>) : AccessUsage
    data class Method(val receiverPathSegments: List<String>, val name: String) : AccessUsage
}

private sealed interface TypeNode {
    fun resolvePropertyUsage(pathSegments: List<String>): PropertyResolution?
    fun resolveMethodUsage(receiverPathSegments: List<String>, name: String): ViewModelEventUsage?

    data class KotlinType(private val klass: KtClass) : TypeNode {
        override fun resolvePropertyUsage(pathSegments: List<String>): PropertyResolution? {
            if (pathSegments.isEmpty()) return null
            val property = klass.findProperty(pathSegments.first()) ?: return null
            if (pathSegments.size == 1) {
                return PropertyResolution(
                    typeText = property.typeReference?.text ?: return null,
                    sourceKind = property.sourceKind,
                )
            }
            val child = property.typeReference?.toNestedTypeNode(property.context) ?: return null
            return child.resolvePropertyUsage(pathSegments.drop(1))
        }

        override fun resolveMethodUsage(receiverPathSegments: List<String>, name: String): ViewModelEventUsage? {
            if (receiverPathSegments.isNotEmpty()) {
                val receiverProperty = klass.findProperty(receiverPathSegments.first()) ?: return null
                val child = receiverProperty.typeReference?.toNestedTypeNode(receiverProperty.context) ?: return null
                return child.resolveMethodUsage(receiverPathSegments.drop(1), name)
            }
            val function = klass.declarations.filterIsInstance<KtNamedFunction>().firstOrNull { it.name == name } ?: return null
            return ViewModelEventUsage(
                name = name,
                receiverPathSegments = emptyList(),
                parameterName = name,
                parameterTypes = function.valueParameters.map { it.typeReference?.text ?: "Any" },
                returnType = function.typeReference?.text ?: "Unit",
            )
        }
    }

    data class JavaType(private val psiClass: PsiClass) : TypeNode {
        override fun resolvePropertyUsage(pathSegments: List<String>): PropertyResolution? {
            if (pathSegments.isEmpty()) return null
            val field = psiClass.allFields.firstOrNull { it.name == pathSegments.first() } ?: return null
            if (pathSegments.size == 1) {
                return PropertyResolution(
                    typeText = field.type.presentableText,
                    sourceKind = ViewModelStateSourceKind.INPUT,
                )
            }
            val child = field.type.presentableText.toTypeNode(field) ?: return null
            return child.resolvePropertyUsage(pathSegments.drop(1))
        }

        override fun resolveMethodUsage(receiverPathSegments: List<String>, name: String): ViewModelEventUsage? {
            if (receiverPathSegments.isNotEmpty()) {
                val field = psiClass.allFields.firstOrNull { it.name == receiverPathSegments.first() } ?: return null
                val child = field.type.presentableText.toTypeNode(field) ?: return null
                return child.resolveMethodUsage(receiverPathSegments.drop(1), name)
            }
            val method = psiClass.allMethods.firstOrNull { it.name == name } ?: return null
            return ViewModelEventUsage(
                name = name,
                receiverPathSegments = emptyList(),
                parameterName = name,
                parameterTypes = method.parameterList.parameters.map { it.type.presentableText },
                returnType = method.returnType?.presentableText ?: "Unit",
            )
        }
    }
}

private data class KotlinPropertyInfo(
    val typeReference: KtTypeReference?,
    val context: com.intellij.psi.PsiElement,
    val sourceKind: ViewModelStateSourceKind,
)

private data class PropertyResolution(
    val typeText: String,
    val sourceKind: ViewModelStateSourceKind,
)

private fun KtClass.findProperty(name: String): KotlinPropertyInfo? {
    primaryConstructorParameters.firstOrNull { it.hasValOrVar() && it.name == name }?.let {
        return KotlinPropertyInfo(it.typeReference, it, ViewModelStateSourceKind.INPUT)
    }
    declarations.filterIsInstance<KtProperty>().firstOrNull { it.name == name }?.let { property ->
        return KotlinPropertyInfo(
            typeReference = property.typeReference,
            context = property,
            sourceKind = if (property.isComputedProperty()) {
                ViewModelStateSourceKind.COMPUTED
            } else {
                ViewModelStateSourceKind.INPUT
            },
        )
    }
    return null
}

private fun KtProperty.isComputedProperty(): Boolean {
    if (initializer != null) return true
    val getter = getter ?: return false
    return getter.bodyExpression != null || getter.equalsToken == null
}

private fun org.jetbrains.kotlin.psi.KtTypeReference.toNestedTypeNode(context: com.intellij.psi.PsiElement): TypeNode? {
    val typeText = text
    val shortName = typeText.substringBefore("<").substringAfterLast(".")
    if (shortName.isBlank()) return null
    val ktClass = (context.containingFile as? KtFile)
        ?.declarations
        ?.filterIsInstance<KtClass>()
        ?.firstOrNull { it.name == shortName }
    if (ktClass != null) return TypeNode.KotlinType(ktClass)
    val psiClass = PsiShortNamesCache.getInstance(context.project)
        .getClassesByName(shortName, GlobalSearchScope.projectScope(context.project))
        .firstOrNull() ?: return null
    return TypeNode.JavaType(psiClass)
}

private fun String.toTypeNode(context: com.intellij.psi.PsiElement): TypeNode? {
    val shortName = substringBefore("<").substringAfterLast(".")
    if (shortName.isBlank()) return null
    val ktClass = (context.containingFile as? KtFile)
        ?.declarations
        ?.filterIsInstance<KtClass>()
        ?.firstOrNull { it.name == shortName }
    if (ktClass != null) return TypeNode.KotlinType(ktClass)
    val psiClass = PsiShortNamesCache.getInstance(context.project)
        .getClassesByName(shortName, GlobalSearchScope.projectScope(context.project))
        .firstOrNull() ?: return null
    return TypeNode.JavaType(psiClass)
}

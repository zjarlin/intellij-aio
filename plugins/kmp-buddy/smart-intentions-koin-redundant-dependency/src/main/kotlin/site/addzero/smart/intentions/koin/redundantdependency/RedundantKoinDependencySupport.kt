package site.addzero.smart.intentions.koin.redundantdependency

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.smart.intentions.core.SmartPsiWriteSupport

internal object RedundantKoinDependencySupport {
    fun findCandidate(parameter: KtParameter): RedundantKoinDependencyCandidate? {
        val ownerClass = parameter.getStrictParentOfType<KtClass>() ?: return null
        if (ownerClass.primaryConstructorParameters.none { it == parameter }) {
            return null
        }
        val targetName = parameter.name ?: return null
        val targetType = resolveTypeElement(parameter) ?: return null
        val references = collectTargetReferences(ownerClass, parameter)
        if (references.isEmpty()) {
            return null
        }

        val matches = ownerClass.primaryConstructorParameters.asSequence()
            .filter { it != parameter }
            .mapNotNull { carrier ->
                val carrierName = carrier.name ?: return@mapNotNull null
                val accessors = resolveReadableAccessors(carrier)
                val accessor = accessors.singleOrNull { it.typeElement == targetType } ?: return@mapNotNull null
                RedundantKoinDependencyMatch(
                    redundantParameter = parameter,
                    carrierParameter = carrier,
                    carrierAccessText = if (carrier.hasValOrVar()) {
                        "this.$carrierName"
                    } else {
                        carrierName
                    },
                    accessorName = accessor.name,
                )
            }
            .toList()
        if (matches.size != 1) {
            return null
        }

        return RedundantKoinDependencyCandidate(
            ownerClass = ownerClass,
            redundantParameter = parameter,
            replacement = matches.single(),
            references = references,
            displayTargetName = targetName,
        )
    }

    private fun collectTargetReferences(ownerClass: KtClass, parameter: KtParameter): List<KtNameReferenceExpression> {
        val targetName = parameter.name ?: return emptyList()
        return ownerClass.collectDescendantsOfType<KtNameReferenceExpression>()
            .asSequence()
            .filter { it.getReferencedName() == targetName }
            .filter { isReferenceToParameterMember(it, ownerClass, parameter) }
            .distinctBy { it.textRange.startOffset }
            .toList()
    }

    private fun isReferenceToParameterMember(
        reference: KtNameReferenceExpression,
        ownerClass: KtClass,
        parameter: KtParameter,
    ): Boolean {
        val resolved = reference.mainReference.resolve() ?: return false
        if (resolved == parameter) {
            return true
        }
        return when (resolved) {
            is KtParameter -> resolved.name == parameter.name && resolved.getStrictParentOfType<KtClass>() == ownerClass
            is KtProperty -> resolved.name == parameter.name && resolved.getStrictParentOfType<KtClass>() == ownerClass
            is PsiField -> resolved.name == parameter.name && isSameContainingClass(resolved.containingClass, ownerClass)
            is PsiMethod -> resolved.name == "get${targetAccessorSuffix(parameter.name)}" &&
                isSameContainingClass(resolved.containingClass, ownerClass)
            else -> false
        }
    }

    private fun isSameContainingClass(psiClass: PsiClass?, ownerClass: KtClass): Boolean {
        val ownerName = ownerClass.name ?: return false
        val ownerQualifiedName = ownerClass.fqName?.asString()
        return psiClass?.qualifiedName == ownerQualifiedName || psiClass?.name == ownerName
    }

    private fun targetAccessorSuffix(parameterName: String?): String {
        val safeName = parameterName.orEmpty()
        return safeName.replaceFirstChar { it.uppercaseChar() }
    }

    private fun resolveReadableAccessors(parameter: KtParameter): List<ResolvedAccessor> {
        resolveKtClass(parameter)?.let { return resolveKotlinAccessors(it) }
        resolvePsiClass(parameter)?.let { return resolveJavaAccessors(it) }
        return emptyList()
    }

    private fun resolveKtClass(parameter: KtParameter): KtClass? {
        val typeElement = parameter.typeReference?.typeElement as? KtUserType ?: return null
        val resolved = typeElement.referenceExpression?.mainReference?.resolve()
        if (resolved is KtClass) {
            return resolved
        }
        val shortName = typeElement.referencedName ?: return null
        return parameter.containingKtFile.declarations
            .filterIsInstance<KtClass>()
            .firstOrNull { it.name == shortName }
    }

    private fun resolvePsiClass(parameter: KtParameter): PsiClass? {
        val typeText = parameter.typeReference?.text ?: return null
        val shortName = typeText.substringBefore("<").substringAfterLast(".")
        if (shortName.isBlank()) {
            return null
        }
        return PsiShortNamesCache.getInstance(parameter.project)
            .getClassesByName(shortName, GlobalSearchScope.projectScope(parameter.project))
            .firstOrNull()
    }

    private fun resolveKotlinAccessors(klass: KtClass): List<ResolvedAccessor> {
        val accessors = mutableListOf<ResolvedAccessor>()
        klass.primaryConstructorParameters.forEach { ctorParam ->
            if (!ctorParam.hasValOrVar()) {
                return@forEach
            }
            if (!isReadableVisibility(ctorParam)) {
                return@forEach
            }
            val accessorName = ctorParam.name ?: return@forEach
            val typeElement = resolveTypeElement(ctorParam) ?: return@forEach
            accessors += ResolvedAccessor(accessorName, typeElement)
        }
        klass.declarations.filterIsInstance<KtProperty>().forEach { property ->
            if (!isReadableVisibility(property)) {
                return@forEach
            }
            val accessorName = property.name ?: return@forEach
            val typeElement = resolveTypeElement(property) ?: return@forEach
            accessors += ResolvedAccessor(accessorName, typeElement)
        }
        return accessors
    }

    private fun resolveJavaAccessors(psiClass: PsiClass): List<ResolvedAccessor> {
        return psiClass.allFields.asSequence()
            .filter { field ->
                !field.hasModifierProperty(PsiModifier.PRIVATE) &&
                    !field.hasModifierProperty(PsiModifier.PROTECTED)
            }
            .mapNotNull { field ->
                val typeElement = field.type.canonicalText.substringBefore("<").substringAfterLast('.')
                    .takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                ResolvedAccessor(field.name, typeElement)
            }
            .toList()
    }

    private fun resolveTypeElement(parameter: KtParameter): String? {
        val typeElement = parameter.typeReference?.typeElement as? KtUserType ?: return null
        val resolved = typeElement.referenceExpression?.mainReference?.resolve()
        return when (resolved) {
            is KtClassOrObject -> resolved.fqName?.asString() ?: resolved.name
            is PsiClass -> resolved.qualifiedName ?: resolved.name
            else -> typeElement.referencedName
        }
    }

    private fun resolveTypeElement(property: KtProperty): String? {
        val typeElement = property.typeReference?.typeElement as? KtUserType ?: return null
        val resolved = typeElement.referenceExpression?.mainReference?.resolve()
        return when (resolved) {
            is KtClassOrObject -> resolved.fqName?.asString() ?: resolved.name
            is PsiClass -> resolved.qualifiedName ?: resolved.name
            else -> typeElement.referencedName
        }
    }

    private fun isReadableVisibility(parameter: KtParameter): Boolean {
        return !parameter.hasModifier(KtTokens.PRIVATE_KEYWORD) && !parameter.hasModifier(KtTokens.PROTECTED_KEYWORD)
    }

    private fun isReadableVisibility(property: KtProperty): Boolean {
        return !property.hasModifier(KtTokens.PRIVATE_KEYWORD) && !property.hasModifier(KtTokens.PROTECTED_KEYWORD)
    }
}

internal data class RedundantKoinDependencyCandidate(
    val ownerClass: KtClass,
    val redundantParameter: KtParameter,
    val replacement: RedundantKoinDependencyMatch,
    val references: List<KtNameReferenceExpression>,
    val displayTargetName: String,
) {
    fun apply(project: Project) {
        val psiFactory = KtPsiFactory(project)
        SmartPsiWriteSupport.runWriteCommand(project, "移除冗余依赖注入参数") {
            references.asReversed().forEach { reference ->
                if (!reference.isValid) {
                    return@forEach
                }
                val replacementText = replacement.renderedAccess()
                val replacementExpression = psiFactory.createExpression(replacementText)
                val parentDot = reference.parent as? KtDotQualifiedExpression
                val target = if (parentDot?.selectorExpression == reference && parentDot.receiverExpression is KtThisExpression) {
                    parentDot
                } else {
                    reference
                }
                target.replace(replacementExpression)
            }
            if (redundantParameter.isValid) {
                findAdjacentComma(redundantParameter, forward = true)?.delete()
                    ?: findAdjacentComma(redundantParameter, forward = false)?.delete()
                redundantParameter.delete()
            }
        }
    }
}

internal data class RedundantKoinDependencyMatch(
    val redundantParameter: KtParameter,
    val carrierParameter: KtParameter,
    val carrierAccessText: String,
    val accessorName: String,
) {
    fun renderedAccess(): String {
        return "$carrierAccessText.$accessorName"
    }
}

private data class ResolvedAccessor(
    val name: String,
    val typeElement: String,
)

private fun findAdjacentComma(parameter: KtParameter, forward: Boolean): PsiElement? {
    var sibling = if (forward) parameter.nextSibling else parameter.prevSibling
    while (sibling != null) {
        if (sibling is PsiWhiteSpace) {
            sibling = if (forward) sibling.nextSibling else sibling.prevSibling
            continue
        }
        return sibling.takeIf { it.text == "," }
    }
    return null
}

package site.addzero.composebuddy.features.koincollection

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

object KoinCollectionInjectionSupport {
    private const val koinAnnotationPackage = "org.koin.core.annotation"

    private val koinComponentAnnotations = mapOf(
        "Single" to "$koinAnnotationPackage.Single",
        "Factory" to "$koinAnnotationPackage.Factory",
        "Singleton" to "$koinAnnotationPackage.Singleton",
    )

    fun findCollectionInjectionNavigation(componentClass: KtClass): KoinCollectionInjectionNavigation? {
        val anchor = componentClass.koinComponentAnnotationAnchor() ?: return null
        val interfaces = componentClass.koinBoundInterfaces()
        if (interfaces.isEmpty()) {
            return null
        }

        val targets = interfaces
            .flatMap { interfaceClass -> findCollectionInjectionTargets(interfaceClass) }
            .distinctBy { target ->
                val filePath = target.containingFile.virtualFile?.path ?: target.containingFile.name
                filePath to target.textRange.startOffset
            }
        if (targets.isEmpty()) {
            return null
        }

        return KoinCollectionInjectionNavigation(
            anchor = anchor,
            interfaceNames = interfaces.mapNotNull { interfaceClass -> interfaceClass.name }.distinct().sorted(),
            targets = targets,
        )
    }

    fun findCollectionInjectionTargets(interfaceClass: KtClass): List<PsiElement> {
        if (!interfaceClass.isInterface()) {
            return emptyList()
        }

        return ignoreBrokenPsiOrIndex {
            ReferencesSearch.search(interfaceClass, GlobalSearchScope.projectScope(interfaceClass.project))
                .findAll()
                .asSequence()
                .mapNotNull { reference -> reference.element as? KtNameReferenceExpression }
                .mapNotNull { reference -> reference.collectionInjectionTarget() }
                .distinctBy { target ->
                    val filePath = target.containingFile.virtualFile?.path ?: target.containingFile.name
                    filePath to target.textRange.startOffset
                }
                .sortedWith(compareBy(
                    { target -> target.containingFile.virtualFile?.path ?: target.containingFile.name },
                    { target -> target.textRange.startOffset },
                ))
                .toList()
        } ?: emptyList()
    }

    private fun KtClass.koinComponentAnnotationAnchor(): PsiElement? {
        return annotationEntries
            .firstOrNull { annotation -> annotation.isKoinComponentAnnotation() }
            ?.typeReference
            ?.let { typeReference ->
                (typeReference.typeElement as? KtUserType)?.referenceExpression ?: typeReference
            }
    }

    private fun KtClass.koinBoundInterfaces(): List<KtClass> {
        return (implementedInterfaces() + annotationBoundInterfaces())
            .distinctBy { interfaceClass ->
                interfaceClass.fqName?.asString() ?: "${interfaceClass.containingFile.virtualFile?.path}:${interfaceClass.name}"
            }
    }

    private fun KtClass.implementedInterfaces(): List<KtClass> {
        return superTypeListEntries.mapNotNull { entry ->
            val userType = entry.typeReference?.typeElement as? KtUserType ?: return@mapNotNull null
            val resolved = userType.referenceExpression?.mainReference?.resolve()
            (resolved as? KtClass)?.takeIf { resolvedClass -> resolvedClass.isInterface() }
        }
    }

    private fun KtClass.annotationBoundInterfaces(): List<KtClass> {
        return annotationEntries
            .filter { annotation -> annotation.isKoinComponentAnnotation() }
            .flatMap { annotation ->
                annotation.valueArgumentList
                    ?.collectDescendantsOfType<KtNameReferenceExpression>()
                    ?.mapNotNull { reference ->
                        (reference.mainReference.resolve() as? KtClass)
                            ?.takeIf { resolvedClass -> resolvedClass.isInterface() }
                    }
                    .orEmpty()
            }
    }

    private fun KtNameReferenceExpression.collectionInjectionTarget(): PsiElement? {
        val listTypeReference = listTypeReferenceWhenSingleTypeArgument()
        if (listTypeReference?.isKoinConstructorParameterType() == true) {
            return listTypeReference
        }

        return getAllCallWhenSingleTypeArgument()
            ?.takeIf { callExpression -> callExpression.isKoinGetAllCall() }
            ?.navigationTarget()
    }

    private fun KtNameReferenceExpression.listTypeReferenceWhenSingleTypeArgument(): KtTypeReference? {
        val innerUserType = parent as? KtUserType ?: return null
        if (innerUserType.referenceExpression != this) {
            return null
        }

        val innerTypeReference = innerUserType.parent as? KtTypeReference ?: return null
        val typeProjection = innerTypeReference.parent as? KtTypeProjection ?: return null
        val argumentList = typeProjection.parent as? KtTypeArgumentList ?: return null
        if (argumentList.arguments.size != 1) {
            return null
        }

        val outerUserType = argumentList.parent as? KtUserType ?: return null
        if (outerUserType.typeArgumentList != argumentList || outerUserType.referencedName != "List") {
            return null
        }

        return outerUserType.parent as? KtTypeReference
    }

    private fun KtNameReferenceExpression.getAllCallWhenSingleTypeArgument(): KtCallExpression? {
        val innerUserType = parent as? KtUserType ?: return null
        if (innerUserType.referenceExpression != this) {
            return null
        }

        val innerTypeReference = innerUserType.parent as? KtTypeReference ?: return null
        val typeProjection = innerTypeReference.parent as? KtTypeProjection ?: return null
        val argumentList = typeProjection.parent as? KtTypeArgumentList ?: return null
        if (argumentList.arguments.size != 1) {
            return null
        }

        val callExpression = argumentList.parent as? KtCallExpression ?: return null
        if (callExpression.typeArgumentList != argumentList) {
            return null
        }

        val calleeName = (callExpression.calleeExpression as? KtNameReferenceExpression)
            ?.getReferencedName()
        return callExpression.takeIf { calleeName == "getAll" }
    }

    private fun KtCallExpression.isKoinGetAllCall(): Boolean {
        val qualifiedExpression = parent as? KtDotQualifiedExpression ?: return false
        if (qualifiedExpression.selectorExpression != this) {
            return false
        }

        val receiverText = qualifiedExpression.receiverExpression.text
        return receiverText.endsWith("getKoin()") ||
            receiverText.endsWith(".koin") ||
            receiverText == "koin" ||
            receiverText.contains("KoinPlatform.getKoin()")
    }

    private fun KtCallExpression.navigationTarget(): PsiElement {
        val qualifiedExpression = parent as? KtDotQualifiedExpression
        return qualifiedExpression
            ?.takeIf { expression -> expression.selectorExpression == this }
            ?: this
    }

    private fun KtTypeReference.isKoinConstructorParameterType(): Boolean {
        val parameter = parent as? KtParameter ?: return false
        val primaryConstructor = parameter.getStrictParentOfType<KtPrimaryConstructor>() ?: return false
        val ownerClass = primaryConstructor.getStrictParentOfType<KtClass>() ?: return false
        return ownerClass.annotationEntries.any { annotation -> annotation.isKoinComponentAnnotation() }
    }

    private fun KtAnnotationEntry.isKoinComponentAnnotation(): Boolean {
        val typeReference = typeReference ?: return false
        val typeText = typeReference.text
        if (typeText in koinComponentAnnotations.values) {
            return true
        }

        val typeElement = typeReference.typeElement as? KtUserType ?: return false
        val fqName = koinComponentAnnotations[typeElement.referencedName] ?: return false
        return containingKtFile.hasImportFor(fqName)
    }

    private fun KtFile.hasImportFor(fqName: String): Boolean {
        return importDirectives.any { directive ->
            directive.isDirectImportOf(fqName) || directive.isWildcardImportOf(koinAnnotationPackage)
        }
    }

    private fun KtImportDirective.isDirectImportOf(fqName: String): Boolean {
        return !isAllUnder &&
            aliasName == null &&
            importedFqName?.asString() == fqName
    }

    private fun KtImportDirective.isWildcardImportOf(packageName: String): Boolean {
        return isAllUnder &&
            aliasName == null &&
            importedFqName?.asString() == packageName
    }
}

data class KoinCollectionInjectionNavigation(
    val anchor: PsiElement,
    val interfaceNames: List<String>,
    val targets: List<PsiElement>,
)

private inline fun <T> ignoreBrokenPsiOrIndex(action: () -> T): T? {
    return try {
        action()
    } catch (exception: ProcessCanceledException) {
        throw exception
    } catch (_: Throwable) {
        null
    }
}

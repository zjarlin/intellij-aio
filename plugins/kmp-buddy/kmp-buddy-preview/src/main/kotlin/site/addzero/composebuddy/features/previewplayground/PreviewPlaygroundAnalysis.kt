package site.addzero.composebuddy.features.previewplayground

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import site.addzero.composebuddy.preview.support.PreviewComposePsiSupport
import site.addzero.composebuddy.support.ComposePreviewSupport

data class PreviewPlaygroundAnalysisResult(
    val function: KtNamedFunction,
    val sampleExpressionOverrides: Map<String, String> = emptyMap(),
    val wrappers: List<PreviewPlaygroundWrapper> = emptyList(),
)

data class PreviewPlaygroundWrapper(
    val openLine: String,
    val closeLine: String,
)

object PreviewPlaygroundAnalysis {
    private const val KYANT_BACKDROP_FQ_NAME = "com.kyant.backdrop.Backdrop"
    private const val KYANT_BACKDROP_PACKAGE = "com.kyant.backdrop"

    fun analyze(function: KtNamedFunction): PreviewPlaygroundAnalysisResult? {
        if (!PreviewComposePsiSupport.isComposable(function)) {
            return null
        }
        if (function.name.isNullOrBlank()) {
            return null
        }
        val sampleExpressionOverrides = liquidGlassSampleOverrides(function)
        if (!ComposePreviewSupport.canRenderQuickPreview(function, sampleExpressionOverrides)) {
            return null
        }
        val wrappers = if (sampleExpressionOverrides.isEmpty()) {
            emptyList()
        } else {
            listOf(PreviewPlaygroundWrapper(openLine = "LiquidGlassSceneRoot {", closeLine = "}"))
        }
        return PreviewPlaygroundAnalysisResult(
            function = function,
            sampleExpressionOverrides = sampleExpressionOverrides,
            wrappers = wrappers,
        )
    }

    private fun liquidGlassSampleOverrides(function: KtNamedFunction): Map<String, String> {
        if (!hasLiquidGlassPreviewContext(function)) {
            return emptyMap()
        }
        return function.valueParameters
            .filter { isKyantBackdropParameter(it) }
            .mapNotNull { parameter ->
                val name = parameter.name ?: return@mapNotNull null
                name to "LocalLiquidBackdrop.current"
            }
            .toMap()
    }

    private fun hasLiquidGlassPreviewContext(function: KtNamedFunction): Boolean {
        return hasSamePackageTopLevelName(function, fileName = "LiquidGlassSceneRoot.kt", name = "LiquidGlassSceneRoot") &&
            hasSamePackageTopLevelName(function, fileName = "LocalLiquidBackdrop.kt", name = "LocalLiquidBackdrop")
    }

    private fun hasSamePackageTopLevelName(
        function: KtNamedFunction,
        fileName: String,
        name: String,
    ): Boolean {
        val sourceFile = function.containingKtFile
        val packageName = sourceFile.packageFqName.asString()
        if (sourceFile.hasTopLevelDeclaration(name)) {
            return true
        }
        val psiManager = PsiManager.getInstance(function.project)
        return FilenameIndex.getVirtualFilesByName(
            function.project,
            fileName,
            GlobalSearchScope.projectScope(function.project),
        )
            .mapNotNull { virtualFile -> psiManager.findFile(virtualFile) as? KtFile }
            .any { file ->
                file.packageFqName.asString() == packageName && file.hasTopLevelDeclaration(name)
            }
    }

    private fun isKyantBackdropParameter(parameter: KtParameter): Boolean {
        val typeReference = parameter.typeReference ?: return false
        val typeText = typeReference.text.substringBefore("<").trim().removeSuffix("?")
        if (typeText == KYANT_BACKDROP_FQ_NAME) {
            return true
        }
        if (typeText != "Backdrop") {
            return false
        }
        return importsKyantBackdrop(parameter) || resolvesToKyantBackdrop(typeReference)
    }

    private fun importsKyantBackdrop(parameter: KtParameter): Boolean {
        return parameter.containingKtFile.importDirectives.any { directive ->
            val importPath = directive.importPath ?: return@any false
            importPath.pathStr == KYANT_BACKDROP_FQ_NAME ||
                (importPath.isAllUnder && importPath.pathStr == KYANT_BACKDROP_PACKAGE)
        }
    }

    private fun resolvesToKyantBackdrop(typeReference: KtTypeReference): Boolean {
        val userType = typeReference.typeElement as? KtUserType ?: return false
        val resolved = runCatching {
            userType.referenceExpression?.mainReference?.resolve()
        }.getOrNull()
        val qualifiedName = when (resolved) {
            is KtClassOrObject -> resolved.fqName?.asString()
            is PsiClass -> resolved.qualifiedName
            else -> null
        }
        return qualifiedName == KYANT_BACKDROP_FQ_NAME
    }
}

private fun KtFile.hasTopLevelDeclaration(name: String): Boolean {
    return declarations
        .filterIsInstance<KtNamedDeclaration>()
        .any { it.name == name }
}

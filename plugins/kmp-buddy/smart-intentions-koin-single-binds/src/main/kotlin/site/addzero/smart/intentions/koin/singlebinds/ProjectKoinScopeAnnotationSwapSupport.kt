package site.addzero.smart.intentions.koin.singlebinds

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.ImportPath
import site.addzero.smart.intentions.core.SmartPsiWriteSupport

internal object ProjectKoinScopeAnnotationSwapSupport {
    private const val KOIN_SINGLE_FQ_NAME = "org.koin.core.annotation.Single"
    private const val KOIN_SINGLETON_FQ_NAME = "org.koin.core.annotation.Singleton"

    val singleToSingleton = SwapSpec(
        sourceShortName = "Single",
        sourceFqName = KOIN_SINGLE_FQ_NAME,
        targetShortName = "Singleton",
        targetFqName = KOIN_SINGLETON_FQ_NAME,
        scanTitle = "扫描项目中的 @Single",
        commandName = "将项目中的 @Single 替换为 @Singleton",
    )

    val singletonToSingle = SwapSpec(
        sourceShortName = "Singleton",
        sourceFqName = KOIN_SINGLETON_FQ_NAME,
        targetShortName = "Single",
        targetFqName = KOIN_SINGLE_FQ_NAME,
        scanTitle = "扫描项目中的 @Singleton",
        commandName = "将项目中的 @Singleton 替换为 @Single",
    )

    fun isApplicable(annotation: KtAnnotationEntry, spec: SwapSpec): Boolean {
        return isTargetKoinAnnotation(annotation, spec)
    }

    fun apply(project: Project, spec: SwapSpec) {
        val files = ProgressManager.getInstance().runProcessWithProgressSynchronously<List<KtFile>, RuntimeException>(
            {
                ReadAction.compute<List<KtFile>, Throwable> {
                    collectTargetFiles(project, ProgressManager.getInstance().progressIndicator, spec)
                }
            },
            spec.scanTitle,
            true,
            project,
        )
        if (files.isEmpty()) {
            return
        }
        val psiFactory = KtPsiFactory(project)
        SmartPsiWriteSupport.runWriteCommand(project, spec.commandName) {
            files.forEach { file ->
                if (!file.isValid) {
                    return@forEach
                }
                replaceAnnotations(file, psiFactory, spec)
                replaceImports(file, psiFactory, spec)
            }
        }
    }

    private fun collectTargetFiles(
        project: Project,
        indicator: ProgressIndicator?,
        spec: SwapSpec,
    ): List<KtFile> {
        val psiManager = PsiManager.getInstance(project)
        val files = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))
        indicator?.isIndeterminate = false
        val total = files.size.coerceAtLeast(1)
        return files
            .asSequence()
            .mapIndexed { index, virtualFile ->
                indicator?.checkCanceled()
                indicator?.fraction = index.toDouble() / total.toDouble()
                psiManager.findFile(virtualFile) as? KtFile
            }
            .filterNotNull()
            .filter { file -> hasApplicableAnnotation(file, spec) }
            .toList()
    }

    private fun hasApplicableAnnotation(file: KtFile, spec: SwapSpec): Boolean {
        return file.collectDescendantsOfType<KtAnnotationEntry>().any { annotation ->
            isTargetKoinAnnotation(annotation, spec)
        }
    }

    private fun replaceImports(file: KtFile, psiFactory: KtPsiFactory, spec: SwapSpec) {
        val sourceImports = file.importDirectives.filter { directive ->
            isDirectImportOf(directive, spec.sourceFqName)
        }
        if (sourceImports.isEmpty()) {
            return
        }
        var hasTargetImport = hasDirectImport(file, spec.targetFqName)
        sourceImports.forEach { directive ->
            if (!directive.isValid) {
                return@forEach
            }
            if (hasTargetImport) {
                directive.delete()
                return@forEach
            }
            val targetDirective = psiFactory.createImportDirective(
                ImportPath(FqName(spec.targetFqName), false),
            )
            directive.replace(targetDirective)
            hasTargetImport = true
        }
    }

    private fun replaceAnnotations(file: KtFile, psiFactory: KtPsiFactory, spec: SwapSpec) {
        val annotations = file.collectDescendantsOfType<KtAnnotationEntry>().filter { annotation ->
            isTargetKoinAnnotation(annotation, spec)
        }
        annotations.forEach { annotation ->
            val typeReference = annotation.typeReference ?: return@forEach
            if (!annotation.isValid || !typeReference.isValid) {
                return@forEach
            }
            val replacementText = renderReplacementTypeText(typeReference.text, spec)
            val replacementAnnotation = psiFactory.createAnnotationEntry(
                annotation.text.replaceFirst(typeReference.text, replacementText),
            )
            annotation.replace(replacementAnnotation)
        }
    }

    private fun renderReplacementTypeText(typeText: String, spec: SwapSpec): String {
        if (typeText == spec.sourceFqName) {
            return spec.targetFqName
        }
        if (typeText == spec.sourceShortName) {
            return spec.targetShortName
        }
        return typeText.removeSuffix(spec.sourceShortName) + spec.targetShortName
    }

    private fun isTargetKoinAnnotation(annotation: KtAnnotationEntry, spec: SwapSpec): Boolean {
        val typeReference = annotation.typeReference ?: return false
        if (typeReference.text == spec.sourceFqName) {
            return true
        }
        val typeElement = typeReference.typeElement as? KtUserType ?: return false
        if (typeElement.referencedName != spec.sourceShortName) {
            return false
        }
        return hasDirectImport(annotation.containingKtFile, spec.sourceFqName)
    }

    private fun hasDirectImport(file: KtFile, fqName: String): Boolean {
        return file.importDirectives.any { directive ->
            isDirectImportOf(directive, fqName)
        }
    }

    private fun isDirectImportOf(directive: KtImportDirective, fqName: String): Boolean {
        return !directive.isAllUnder &&
            directive.aliasName == null &&
            directive.importedFqName?.asString() == fqName
    }
}

internal data class SwapSpec(
    val sourceShortName: String,
    val sourceFqName: String,
    val targetShortName: String,
    val targetFqName: String,
    val scanTitle: String,
    val commandName: String,
)

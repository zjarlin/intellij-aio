package site.addzero.smart.intentions.koin.singlebinds

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.smart.intentions.core.SmartPsiWriteSupport

internal object ProjectSingleBindsSupport {
    private const val KOIN_SINGLE_FQ_NAME = "org.koin.core.annotation.Single"
    private const val BINDS_ARGUMENT_NAME = "binds"

    fun isApplicable(annotation: KtAnnotationEntry): Boolean {
        return isKoinSingleAnnotation(annotation) && findBindsArgument(annotation) != null
    }

    fun apply(project: Project) {
        val annotations = ProgressManager.getInstance().runProcessWithProgressSynchronously<List<KtAnnotationEntry>, RuntimeException>(
            {
                ReadAction.compute<List<KtAnnotationEntry>, Throwable> {
                    collectProjectAnnotations(project, ProgressManager.getInstance().progressIndicator)
                }
            },
            "扫描 @Single binds",
            true,
            project,
        )
        if (annotations.isEmpty()) {
            return
        }
        SmartPsiWriteSupport.runWriteCommand(project, "删除项目中的 @Single binds") {
            annotations.forEach { annotation ->
                removeBindsArgument(annotation)
            }
        }
    }

    private fun collectProjectAnnotations(project: Project, indicator: ProgressIndicator?): List<KtAnnotationEntry> {
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
            .flatMap { file ->
                file.collectDescendantsOfType<KtAnnotationEntry>().asSequence()
            }
            .filter { annotation -> isApplicable(annotation) }
            .toList()
    }

    private fun isKoinSingleAnnotation(annotation: KtAnnotationEntry): Boolean {
        val typeReference = annotation.typeReference ?: return false
        if (typeReference.text == KOIN_SINGLE_FQ_NAME) {
            return true
        }
        val typeElement = typeReference.typeElement as? KtUserType ?: return false
        val shortName = typeElement.referencedName
        if (shortName != "Single") {
            return false
        }
        return hasDirectKoinSingleImport(annotation.containingKtFile)
    }

    private fun hasDirectKoinSingleImport(file: KtFile): Boolean {
        return file.importDirectives.any { directive ->
            !directive.isAllUnder &&
                directive.aliasName == null &&
                directive.importedFqName?.asString() == KOIN_SINGLE_FQ_NAME
        }
    }

    private fun findBindsArgument(annotation: KtAnnotationEntry): KtValueArgument? {
        return annotation.valueArguments
            .filterIsInstance<KtValueArgument>()
            .firstOrNull { argument ->
                argument.getArgumentName()?.asName?.identifier == BINDS_ARGUMENT_NAME
            }
    }

    private fun removeBindsArgument(annotation: KtAnnotationEntry) {
        val argument = findBindsArgument(annotation) ?: return
        val argumentList = annotation.valueArgumentList ?: return
        if (argumentList.arguments.size == 1) {
            argumentList.delete()
            return
        }
        findAdjacentComma(argument, forward = true)?.delete()
            ?: findAdjacentComma(argument, forward = false)?.delete()
        argument.delete()
    }
}

private fun findAdjacentComma(argument: KtValueArgument, forward: Boolean): PsiElement? {
    var sibling = if (forward) argument.nextSibling else argument.prevSibling
    while (sibling != null) {
        if (sibling is PsiWhiteSpace) {
            sibling = if (forward) sibling.nextSibling else sibling.prevSibling
            continue
        }
        return sibling.takeIf { it.text == "," }
    }
    return null
}

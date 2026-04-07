package site.addzero.smart.intentions.kotlin.redundantexplicittype

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.smart.intentions.core.SmartIntentionsMessages
import site.addzero.smart.intentions.core.SmartPsiWriteSupport

internal object ProjectRedundantExplicitTypeSupport {
    fun apply(project: Project) {
        val propertyPointers = ProgressManager.getInstance().runProcessWithProgressSynchronously<List<SmartPsiElementPointer<KtProperty>>, RuntimeException>(
            {
                ReadAction.compute<List<SmartPsiElementPointer<KtProperty>>, Throwable> {
                    collectProjectProperties(project, ProgressManager.getInstance().progressIndicator)
                }
            },
            "扫描冗余显式类型",
            true,
            project,
        )
        if (propertyPointers.isEmpty()) {
            return
        }
        SmartPsiWriteSupport.runWriteCommand(project, SmartIntentionsMessages.REMOVE_PROJECT_REDUNDANT_EXPLICIT_TYPE) {
            propertyPointers.asSequence()
                .mapNotNull { pointer -> pointer.element }
                .filter { property -> property.isValid && property.typeReference != null }
                .forEach { property ->
                    RedundantExplicitTypeSupport.apply(property)
                }
        }
    }

    private fun collectProjectProperties(
        project: Project,
        indicator: ProgressIndicator?,
    ): List<SmartPsiElementPointer<KtProperty>> {
        val psiManager = PsiManager.getInstance(project)
        val pointerManager = SmartPointerManager.getInstance(project)
        val files = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))
        indicator?.isIndeterminate = false
        val total = files.size.coerceAtLeast(1)
        return files.asSequence()
            .mapIndexed { index, virtualFile ->
                indicator?.checkCanceled()
                indicator?.fraction = index.toDouble() / total.toDouble()
                psiManager.findFile(virtualFile) as? KtFile
            }
            .filterNotNull()
            .flatMap { file ->
                file.collectDescendantsOfType<KtProperty>().asSequence()
            }
            .filter { property -> RedundantExplicitTypeSupport.isApplicable(property) }
            .map { property -> pointerManager.createSmartPsiElementPointer(property) }
            .toList()
    }
}

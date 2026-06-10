package site.addzero.smart.intentions.kotlin.shortenqualifiedname

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.KtFile
import site.addzero.smart.intentions.core.SmartIntentionsMessages
import site.addzero.smart.intentions.core.SmartPsiWriteSupport

internal object ProjectShortenQualifiedNameSupport {
    fun applyInBackground(
        project: Project,
        onResult: (ProjectShortenQualifiedNameResult) -> Unit = {},
    ) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            onResult(apply(project))
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            SmartIntentionsMessages.SHORTEN_PROJECT_QUALIFIED_NAMES,
            true,
        ) {
            private var result: ProjectShortenQualifiedNameResult? = null

            override fun run(indicator: ProgressIndicator) {
                result = apply(project, indicator)
            }

            override fun onSuccess() {
                result?.let(onResult)
            }
        })
    }

    fun apply(project: Project): ProjectShortenQualifiedNameResult {
        return apply(project, ProgressManager.getInstance().progressIndicator)
    }

    fun apply(
        project: Project,
        indicator: ProgressIndicator?,
    ): ProjectShortenQualifiedNameResult {
        val files = ReadAction.compute<List<VirtualFile>, Throwable> {
            collectProjectKotlinFiles(project, indicator)
        }
        if (files.isEmpty()) {
            return ProjectShortenQualifiedNameResult(scannedFiles = 0, changedFiles = 0)
        }

        var changedFiles = 0
        indicator?.isIndeterminate = false
        indicator?.text = "缩短 Kotlin 全限定名"

        val psiManager = PsiManager.getInstance(project)
        val shortenReferences = ShortenReferencesFacility.getInstance()
        val total = files.size.coerceAtLeast(1)
        files.forEachIndexed { index, virtualFile ->
            indicator?.checkCanceled()
            indicator?.fraction = index.toDouble() / total.toDouble()
            indicator?.text2 = virtualFile.path

            if (shortenFile(project, psiManager, shortenReferences, virtualFile)) {
                changedFiles += 1
            }
        }
        indicator?.fraction = 1.0
        indicator?.text2 = null

        return ProjectShortenQualifiedNameResult(
            scannedFiles = files.size,
            changedFiles = changedFiles,
        )
    }

    private fun collectProjectKotlinFiles(
        project: Project,
        indicator: ProgressIndicator?,
    ): List<VirtualFile> {
        indicator?.isIndeterminate = true
        indicator?.text = "收集 Kotlin 文件"
        indicator?.text2 = null
        indicator?.checkCanceled()
        val files = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))
            .sortedBy { file -> file.path }
        indicator?.checkCanceled()
        return files
    }

    private fun shortenFile(
        project: Project,
        psiManager: PsiManager,
        shortenReferences: ShortenReferencesFacility,
        virtualFile: VirtualFile,
    ): Boolean {
        var changed = false
        val action = {
            SmartPsiWriteSupport.runWriteCommand(project, SmartIntentionsMessages.SHORTEN_PROJECT_QUALIFIED_NAMES) {
                val file = psiManager.findFile(virtualFile) as? KtFile
                if (file == null || !file.isValid || file.textLength == 0) {
                    return@runWriteCommand
                }

                val before = file.text
                shortenReferences.shorten(file, TextRange(0, file.textLength))
                changed = file.text != before
            }
        }

        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            action()
        } else {
            application.invokeAndWait(action, ModalityState.any())
        }
        return changed
    }
}

internal data class ProjectShortenQualifiedNameResult(
    val scannedFiles: Int,
    val changedFiles: Int,
)

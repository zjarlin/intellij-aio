package site.addzero.smart.intentions.kotlin.shortenqualifiedname

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
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
    fun apply(project: Project): ProjectShortenQualifiedNameResult {
        val files = ProgressManager.getInstance().runProcessWithProgressSynchronously<List<VirtualFile>, RuntimeException>(
            {
                ReadAction.compute<List<VirtualFile>, Throwable> {
                    collectProjectKotlinFiles(project, ProgressManager.getInstance().progressIndicator)
                }
            },
            "扫描 Kotlin 全限定名",
            true,
            project,
        )
        if (files.isEmpty()) {
            return ProjectShortenQualifiedNameResult(scannedFiles = 0, changedFiles = 0)
        }

        var changedFiles = 0
        SmartPsiWriteSupport.runWriteCommand(project, SmartIntentionsMessages.SHORTEN_PROJECT_QUALIFIED_NAMES) {
            val psiManager = PsiManager.getInstance(project)
            val shortenReferences = ShortenReferencesFacility.getInstance()
            files.asSequence()
                .mapNotNull { virtualFile -> psiManager.findFile(virtualFile) as? KtFile }
                .filter { file -> file.isValid && file.textLength > 0 }
                .forEach { file ->
                    val before = file.text
                    shortenReferences.shorten(file, TextRange(0, file.textLength))
                    if (file.text != before) {
                        changedFiles += 1
                    }
                }
        }

        return ProjectShortenQualifiedNameResult(
            scannedFiles = files.size,
            changedFiles = changedFiles,
        )
    }

    private fun collectProjectKotlinFiles(
        project: Project,
        indicator: ProgressIndicator?,
    ): List<VirtualFile> {
        val files = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))
            .sortedBy { file -> file.path }
        indicator?.isIndeterminate = false
        val total = files.size.coerceAtLeast(1)
        files.forEachIndexed { index, _ ->
            indicator?.checkCanceled()
            indicator?.fraction = index.toDouble() / total.toDouble()
        }
        return files
    }
}

internal data class ProjectShortenQualifiedNameResult(
    val scannedFiles: Int,
    val changedFiles: Int,
)

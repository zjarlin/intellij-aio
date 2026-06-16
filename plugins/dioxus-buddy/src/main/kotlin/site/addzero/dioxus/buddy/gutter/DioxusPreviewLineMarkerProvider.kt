package site.addzero.dioxus.buddy.gutter

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import site.addzero.dioxus.buddy.model.DioxusPreviewResolver
import site.addzero.dioxus.buddy.runner.DioxusPreviewRunner

class DioxusPreviewLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        val files = elements
            .map { it.containingFile }
            .distinctBy { it.virtualFile?.path ?: it.name }

        files.forEach { file ->
            ignoreBrokenPsiOrIndex {
                collectFileMarkers(file, result)
            }
        }
    }

    private fun collectFileMarkers(
        file: PsiFile,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        val project = file.project
        val virtualFile = file.virtualFile ?: return
        if (!DioxusPreviewResolver.isRustFile(virtualFile)) return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val targets = DioxusPreviewResolver.resolveTargets(project, virtualFile, document.text)
        targets.forEach { target ->
            val anchor = file.findElementAt(target.parsed.nameOffset) ?: return@forEach
            result += createPreviewLineMarker(anchor, document, target.parsed.nameOffset, target.functionName)
        }
    }

    private fun createPreviewLineMarker(
        anchor: PsiElement,
        document: Document,
        nameOffset: Int,
        functionName: String,
    ): LineMarkerInfo<PsiElement> {
        val tooltip = "Open Dioxus preview for $functionName"
        val navigationHandler = GutterIconNavigationHandler<PsiElement> { _, element ->
            val project = element.project
            val file = element.containingFile?.virtualFile ?: return@GutterIconNavigationHandler
            val currentDocument = FileDocumentManager.getInstance().getDocument(file) ?: document
            val parsed = DioxusPreviewResolver.resolveTargets(project, file, currentDocument.text)
                .firstOrNull { it.functionName == functionName }
                ?: return@GutterIconNavigationHandler
            DioxusPreviewRunner.run(project, parsed)
        }

        return LineMarkerInfo(
            anchor,
            TextRange(nameOffset, nameOffset + functionName.length),
            DioxusBuddyIcons.Preview,
            { tooltip },
            navigationHandler,
            com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.RIGHT,
            { tooltip },
        )
    }
}

private inline fun ignoreBrokenPsiOrIndex(action: () -> Unit) {
    try {
        action()
    } catch (exception: ProcessCanceledException) {
        throw exception
    } catch (_: Throwable) {
    }
}

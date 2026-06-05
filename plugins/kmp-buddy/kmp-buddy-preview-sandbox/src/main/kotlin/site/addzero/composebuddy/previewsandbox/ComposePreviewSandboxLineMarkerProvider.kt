package site.addzero.composebuddy.previewsandbox

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class ComposePreviewSandboxLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        val leafElements = elements.toHashSet()
        val files = elements
            .mapNotNull { it.containingFile as? KtFile }
            .distinctBy { it.virtualFile?.path ?: it.name }

        files.forEach { file ->
            ignoreBrokenPsiOrIndex {
                file.declarations
                    .filterIsInstance<KtNamedFunction>()
                    .filter(ComposePreviewSandboxSupport::isPreviewFunction)
                    .mapNotNull { function -> function.nameIdentifier }
                    .filter { anchor -> anchor in leafElements }
                    .forEach { anchor ->
                        result += createPreviewSandboxLineMarker(anchor)
                    }
            }
        }
    }

    private fun createPreviewSandboxLineMarker(anchor: PsiElement): LineMarkerInfo<PsiElement> {
        val tooltip = "Open KMP Buddy isolated Compose preview panel"
        val navigationHandler = GutterIconNavigationHandler<PsiElement> { _, element ->
            ComposePreviewSandboxLauncher.open(element)
        }

        return LineMarkerInfo(
            anchor,
            anchor.textRange,
            ComposePreviewSandboxIcons.PluginIcon,
            { tooltip },
            navigationHandler,
            GutterIconRenderer.Alignment.RIGHT,
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

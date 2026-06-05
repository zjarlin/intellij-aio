package site.addzero.composebuddy.previewsandbox

import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedFunction

object ComposePreviewSandboxSupport {
    fun isPreviewFunction(function: KtNamedFunction): Boolean {
        if (isPreviewSandboxFile(function)) {
            return false
        }
        return function.annotationEntries.any(ComposePreviewSandboxSupport::isPreviewAnnotation)
    }

    fun isPreviewAnnotation(annotation: KtAnnotationEntry): Boolean {
        val shortName = annotation.shortName?.asString()
        if (shortName == "Preview") {
            return true
        }

        val target = annotation.text
            .removePrefix("@")
            .substringBefore("(")
            .trim()
        return target == "androidx.compose.ui.tooling.preview.Preview" || target.endsWith(".Preview")
    }

    private fun isPreviewSandboxFile(function: KtNamedFunction): Boolean {
        val path = function.containingKtFile.virtualFile?.path ?: return false
        return path.contains("/.kmp-buddy/preview-sandbox/")
    }
}

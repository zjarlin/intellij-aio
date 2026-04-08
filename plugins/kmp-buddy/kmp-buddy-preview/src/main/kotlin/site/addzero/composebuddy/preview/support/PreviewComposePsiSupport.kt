package site.addzero.composebuddy.preview.support

import org.jetbrains.kotlin.psi.KtNamedFunction

object PreviewComposePsiSupport {
    fun isComposable(function: KtNamedFunction): Boolean {
        return function.annotationEntries.any { entry ->
            entry.shortName?.asString() == "Composable" || entry.text.endsWith(".Composable")
        }
    }
}

package site.addzero.composebuddy.run

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class KmpCommonMainStartupLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        val leafElements = elements.toHashSet()
        val files = elements
            .mapNotNull { it.containingFile as? KtFile }
            .distinctBy { it.virtualFile?.path ?: it.name }

        files.forEach { file ->
            val virtualFile = file.virtualFile ?: return@forEach
            if (!KmpStartupTargetResolver.isCommonMainSourceFile(virtualFile)) {
                return@forEach
            }

            val startupAnchors = file.declarations
                .filterIsInstance<KtNamedFunction>()
                .filter(KmpCommonMainStartupLineMarkerProvider::isCommonMainStartupFunction)
                .mapNotNull { function -> function.nameIdentifier }
                .filter { anchor -> anchor in leafElements }
            if (startupAnchors.isEmpty()) {
                return@forEach
            }

            val runTarget = ignoreBrokenPsiOrIndex {
                KmpStartupTargetResolver.resolve(file.project, virtualFile)
            } ?: return@forEach

            startupAnchors.forEach { anchor ->
                result += createRunLineMarker(anchor, runTarget)
            }
        }
    }

    private fun createRunLineMarker(
        anchor: PsiElement,
        runTarget: KmpStartupRunTarget,
    ): LineMarkerInfo<PsiElement> {
        val tooltip = "Run KMP app ${runTarget.fullTaskName}"
        val navigationHandler = GutterIconNavigationHandler<PsiElement> { _, element ->
            KmpStartupRunConfigurationSupport.run(element.project, runTarget)
        }

        return LineMarkerInfo(
            anchor,
            anchor.textRange,
            AllIcons.Actions.Execute,
            { tooltip },
            navigationHandler,
            GutterIconRenderer.Alignment.LEFT,
            { tooltip },
        )
    }

    companion object {
        fun isCommonMainStartupFunction(function: KtNamedFunction): Boolean {
            return function.isTopLevel &&
                function.name == "App" &&
                function.valueParameters.isEmpty() &&
                function.hasComposableAnnotation()
        }
    }
}

private fun KtNamedFunction.hasComposableAnnotation(): Boolean {
    return annotationEntries.any { annotation ->
        annotation.shortName?.asString() == "Composable"
    }
}

private inline fun <T> ignoreBrokenPsiOrIndex(action: () -> T?): T? {
    return try {
        action()
    } catch (exception: ProcessCanceledException) {
        throw exception
    } catch (_: Throwable) {
        null
    }
}

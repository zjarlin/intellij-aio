package site.addzero.composebuddy.features.koincollection

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

class KoinCollectionInjectionLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        val leafElements = elements.toHashSet()
        val files = elements
            .mapNotNull { element -> element.containingFile as? KtFile }
            .distinctBy { file -> file.virtualFile?.path ?: file.name }

        files.forEach { file ->
            ignoreBrokenPsiOrIndex {
                file.collectDescendantsOfType<KtClass>()
                    .forEach { componentClass ->
                        val navigation = KoinCollectionInjectionSupport
                            .findCollectionInjectionNavigation(componentClass)
                            ?: return@forEach
                        if (navigation.anchor !in leafElements) {
                            return@forEach
                        }

                        result += createLineMarker(navigation)
                    }
            }
        }
    }

    private fun createLineMarker(
        navigation: KoinCollectionInjectionNavigation,
    ): LineMarkerInfo<PsiElement> {
        val targetLabel = navigation.interfaceNames.joinToString(", ")
        return NavigationGutterIconBuilder
            .create(AllIcons.Nodes.Interface)
            .setTargets(navigation.targets)
            .setTooltipText("Go to Koin collection injections for $targetLabel")
            .createLineMarkerInfo(navigation.anchor)
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

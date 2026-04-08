package site.addzero.idfixer

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

/**
 * 为预编译脚本插件文件提供查看使用处的 gutter 图标。
 */
class PrecompiledScriptPluginUsagesLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        val leafElements = elements.toHashSet()
        val files = elements
            .mapNotNull { it.containingFile as? KtFile }
            .distinctBy { it.virtualFile?.path ?: it.name }

        files.forEach { file ->
            collectDefinitionMarker(file, leafElements, result)
            collectUsageMarkers(file, leafElements, result)
        }
    }

    private fun collectDefinitionMarker(
        file: KtFile,
        leafElements: Set<PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        val virtualFile = file.virtualFile ?: return
        val pluginInfo = PrecompiledScriptPluginUsagesSupport.resolveCurrentPluginInfo(file.project, virtualFile)
            ?: return
        if (!PrecompiledScriptPluginUsagesSupport.hasUsages(file, pluginInfo)) {
            return
        }
        val anchor = file.findDefinitionMarkerAnchor(leafElements) ?: return

        result += LineMarkerInfo(
            anchor,
            anchor.textRange,
            AllIcons.Gutter.ImplementedMethod,
            {
                GradleBuddyBundle.message(
                    "line.marker.precompiled.plugin.usages.tooltip",
                    pluginInfo.fullyQualifiedId
                )
            },
            GutterIconNavigationHandler<PsiElement> { _, element ->
                val editor = FileEditorManager.getInstance(element.project).selectedTextEditor
                PrecompiledScriptPluginUsagesSupport.showUsagesAsync(
                    project = element.project,
                    editor = editor,
                    sourceFile = virtualFile,
                    pluginInfo = pluginInfo
                )
            },
            GutterIconRenderer.Alignment.LEFT
        ) {
            pluginInfo.fullyQualifiedId
        }
    }

    private fun collectUsageMarkers(
        file: KtFile,
        leafElements: Set<PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        file.collectDescendantsOfType<KtCallExpression>()
            .forEach { callExpression ->
                val resolvedCall = PrecompiledScriptPluginUsagesSupport.resolvePluginIdCall(callExpression)
                    ?: return@forEach
                val pluginInfo = PrecompiledScriptPluginUsagesSupport.resolvePluginInfoById(file.project, resolvedCall.pluginId)
                    ?: return@forEach
                val currentUsage = PrecompiledScriptPluginUsagesSupport.createUsage(file, resolvedCall)
                    ?: return@forEach
                val otherUsages = PrecompiledScriptPluginUsagesSupport.findUsages(file.project, pluginInfo.file, pluginInfo)
                    .filterNot { usage ->
                        usage.file == currentUsage.file && usage.offset == currentUsage.offset
                    }
                if (otherUsages.isEmpty()) {
                    return@forEach
                }
                val anchor = resolvedCall.stringExpression.firstLeaf()
                    ?.takeIf { it in leafElements }
                    ?: return@forEach

                result += LineMarkerInfo(
                    anchor,
                    anchor.textRange,
                    AllIcons.Gutter.ImplementedMethod,
                    {
                        GradleBuddyBundle.message(
                            "line.marker.precompiled.plugin.other.usages.tooltip",
                            pluginInfo.fullyQualifiedId
                        )
                    },
                    GutterIconNavigationHandler<PsiElement> { _, element ->
                        val editor = FileEditorManager.getInstance(element.project).selectedTextEditor
                        PrecompiledScriptPluginUsagesSupport.showOtherUsagesAsync(
                            project = element.project,
                            editor = editor,
                            pluginInfo = pluginInfo,
                            currentUsage = currentUsage
                        )
                    },
                    GutterIconRenderer.Alignment.LEFT
                ) {
                    "${pluginInfo.fullyQualifiedId}#${currentUsage.offset}"
                }
            }
    }

    private fun KtFile.findDefinitionMarkerAnchor(leafElements: Set<PsiElement>): PsiElement? {
        packageDirective?.packageKeyword?.takeIf { it in leafElements }?.let { return it }

        collectDescendantsOfType<KtCallExpression>()
            .firstOrNull { it.calleeExpression?.text == "plugins" }
            ?.calleeExpression
            ?.firstLeaf()
            ?.takeIf { it in leafElements }
            ?.let { return it }

        return collectDescendantsOfType<KtCallExpression>()
            .firstNotNullOfOrNull { callExpression ->
                callExpression.calleeExpression?.firstLeaf()?.takeIf { it in leafElements }
            }
    }

    private fun PsiElement.firstLeaf(): PsiElement? {
        var current: PsiElement = this
        while (current.firstChild != null) {
            current = current.firstChild
        }
        return current.takeIf { it is LeafPsiElement }
    }
}

package site.addzero.idfixer

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
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
            val virtualFile = file.virtualFile ?: return@forEach
            val pluginInfo = PrecompiledScriptPluginUsagesSupport.resolveCurrentPluginInfo(file.project, virtualFile)
                ?: return@forEach
            val anchor = file.findMarkerAnchor(leafElements) ?: return@forEach

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
    }

    private fun KtFile.findMarkerAnchor(leafElements: Set<PsiElement>): PsiElement? {
        packageDirective?.packageKeyword?.takeIf { it in leafElements }?.let { return it }

        collectDescendantsOfType<KtCallExpression>()
            .firstOrNull { it.calleeExpression?.text == "plugins" }
            ?.calleeExpression
            ?.takeIf { it in leafElements }
            ?.let { return it }

        return collectDescendantsOfType<KtCallExpression>()
            .firstNotNullOfOrNull { callExpression ->
                callExpression.calleeExpression?.takeIf { it in leafElements }
            }
    }
}

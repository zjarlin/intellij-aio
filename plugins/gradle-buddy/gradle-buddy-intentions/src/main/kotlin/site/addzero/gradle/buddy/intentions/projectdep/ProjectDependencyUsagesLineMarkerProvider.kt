package site.addzero.gradle.buddy.intentions.projectdep

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

/**
 * 在模块 build.gradle.kts 顶部显示“谁引用了当前模块”的跳转图标。
 */
class ProjectDependencyUsagesLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        val leafElements = elements.toHashSet()
        val files = elements
            .mapNotNull { it.containingFile as? KtFile }
            .distinctBy { it.virtualFile?.path ?: it.name }

        files.forEach { file ->
            collectModuleUsageMarker(file, leafElements, result)
        }
    }

    private fun collectModuleUsageMarker(
        file: KtFile,
        leafElements: Set<PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        val virtualFile = file.virtualFile ?: return
        if (virtualFile.name != "build.gradle.kts") {
            return
        }

        val moduleUsages = ProjectDependencyUsagesSupport.findUsagesForBuildFile(file.project, virtualFile) ?: return
        if (moduleUsages.usages.isEmpty()) {
            return
        }

        val anchor = file.findMarkerAnchor(leafElements) ?: return
        result += LineMarkerInfo(
            anchor,
            anchor.textRange,
            AllIcons.Gutter.ImplementedMethod,
            {
                GradleBuddyBundle.message(
                    "line.marker.project.dependency.usages.tooltip",
                    moduleUsages.module.path,
                    moduleUsages.usages.size
                )
            },
            GutterIconNavigationHandler<PsiElement> { _, element ->
                val editor = FileEditorManager.getInstance(element.project).selectedTextEditor
                ProjectDependencyUsagesSupport.presentUsages(
                    project = element.project,
                    editor = editor,
                    module = moduleUsages.module,
                    usages = moduleUsages.usages
                )
            },
            GutterIconRenderer.Alignment.LEFT
        ) {
            "${moduleUsages.module.rootDir.path}#${moduleUsages.module.path}#incoming-project-dependencies"
        }
    }

    private fun KtFile.findMarkerAnchor(leafElements: Set<PsiElement>): PsiElement? {
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

package site.addzero.kcloud.idea

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * 生成的ktorfit跳转
 * @author zjarlin
 * @date 2026/03/29
 * @constructor 创建[ControllerApiSourceLineMarkerProvider]
 */
class ControllerApiSourceLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo<*>>) {
        val declaration = element.getStrictParentOfType<KtClass>() ?: return
        if (element != declaration.nameIdentifier) {
            return
        }

        val sourceRef = declaration.docComment
            ?.findSourceReference()
            ?: return
        val targets = resolveTargets(element.project, sourceRef)
        if (targets.isEmpty()) {
            return
        }

        val builder = NavigationGutterIconBuilder
            .create(AllIcons.Actions.Back)
            .setTargets(targets)
            .setTooltipText("Jump to source file ${sourceRef.fileName}")

        result += builder.createLineMarkerInfo(element)
    }

    private fun resolveTargets(project: Project, sourceRef: SourceFileRef): Collection<PsiFile> {
        val candidates = FilenameIndex.getVirtualFilesByName(
            project,
            sourceRef.fileName,
            GlobalSearchScope.projectScope(project),
        )

        return ContainerUtil.mapNotNull(candidates) { virtualFile ->
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: return@mapNotNull null
            if (psiFile.packageFqName.asString() == sourceRef.packageName) psiFile else null
        }
    }
}

private data class SourceFileRef(
    val packageName: String,
    val fileName: String,
)

private val sourceFilePattern = Regex("""原始文件:\s*([A-Za-z0-9_.]+)\.kt""")

private fun KDoc.findSourceReference(): SourceFileRef? {
    val match = sourceFilePattern.find(text) ?: return null
    val qualifiedPath = match.groupValues[1]
    val packageName = qualifiedPath.substringBeforeLast('.', missingDelimiterValue = "")
    val simpleName = qualifiedPath.substringAfterLast('.', missingDelimiterValue = "")
    if (packageName.isBlank() || simpleName.isBlank()) {
        return null
    }
    return SourceFileRef(
        packageName = packageName,
        fileName = "$simpleName.kt",
    )
}

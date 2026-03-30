package site.addzero.kcloud.idea

import com.intellij.codeInsight.daemon.LineMarkerInfo
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
import org.jetbrains.kotlin.lexer.KtTokens.PRIVATE_KEYWORD
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Ktorfit 生成接口与原始路由文件之间的双向跳转。
 */
class ControllerApiSourceLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        collectGeneratedToSourceMarkers(elements, result)
        collectSourceToGeneratedMarkers(elements, result)
    }

    private fun collectGeneratedToSourceMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        val leafElements = elements.toHashSet()
        val files = elements
            .mapNotNull { it.containingFile as? KtFile }
            .distinctBy { it.virtualFile?.path ?: it.name }

        files.forEach { file ->
            file.declarations
                .filterIsInstance<KtClass>()
                .forEach { declaration ->
                    val markerAnchor = declaration.nameIdentifier ?: return@forEach
                    if (markerAnchor !in leafElements) {
                        return@forEach
                    }

                    val sourceRef = declaration.docComment?.findSourceReference() ?: return@forEach
                    val targets = resolveFiles(file.project, sourceRef)
                    if (targets.isEmpty()) {
                        return@forEach
                    }

                    result += NavigationGutterIconBuilder
                        .create(AllIcons.Actions.Back)
                        .setTargets(targets)
                        .setTooltipText("Jump to source file ${sourceRef.fileName}")
                        .createLineMarkerInfo(markerAnchor)
                }
        }
    }

    private fun collectSourceToGeneratedMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        val leafElements = elements.toHashSet()
        val sourceFiles = elements
            .mapNotNull { it.containingFile as? KtFile }
            .distinctBy { it.virtualFile?.path ?: it.name }

        sourceFiles.forEach { containingFile ->
            val fileRef = containingFile.toSourceFileRef() ?: return@forEach
            val generatedDeclarations = resolveGeneratedDeclarations(
                project = containingFile.project,
                sourceRef = fileRef,
            )
            if (generatedDeclarations.isEmpty()) {
                return@forEach
            }

            val navigationTargets = generatedDeclarations.map { declaration ->
                declaration.nameIdentifier ?: declaration
            }
            containingFile.declarations
                .filterIsInstance<KtNamedFunction>()
                .filter(KtNamedFunction::isTopLevel)
                .filterNot(KtNamedFunction::hasPrivateModifier)
                .forEach { declaration ->
                    val markerAnchor = declaration.funKeyword ?: declaration.nameIdentifier ?: declaration.firstChild ?: return@forEach
                    if (markerAnchor !in leafElements) {
                        return@forEach
                    }
                    result += NavigationGutterIconBuilder
                        .create(AllIcons.Actions.Forward)
                        .setTargets(navigationTargets)
                        .setTooltipText("Jump to generated Ktorfit API")
                        .createLineMarkerInfo(markerAnchor)
                }
        }
    }

    private fun resolveFiles(
        project: Project,
        sourceRef: SourceFileRef,
    ): Collection<PsiFile> {
        val candidates = FilenameIndex.getVirtualFilesByName(
            project,
            sourceRef.fileName,
            GlobalSearchScope.projectScope(project),
        )

        return ContainerUtil.mapNotNull(candidates) { virtualFile ->
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: return@mapNotNull null
            if (psiFile.packageFqName.asString() == sourceRef.packageName) {
                psiFile
            } else {
                null
            }
        }
    }

    private fun resolveGeneratedDeclarations(
        project: Project,
        sourceRef: SourceFileRef,
    ): Collection<KtClass> {
        val searchScope = GlobalSearchScope.projectScope(project)
        val ktFiles = FilenameIndex.getAllFilesByExt(project, "kt", searchScope)
        return ContainerUtil.mapNotNull(ktFiles) { virtualFile ->
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: return@mapNotNull null
            psiFile.declarations
                .filterIsInstance<KtClass>()
                .firstOrNull { declaration ->
                    declaration.docComment?.findSourceReference() == sourceRef
                }
        }
    }
}

private fun KtNamedFunction.hasPrivateModifier(): Boolean = hasModifier(PRIVATE_KEYWORD)

private data class SourceFileRef(
    val packageName: String,
    val fileName: String,
)

private val sourceFilePattern = Regex("""原始文件:\s*([A-Za-z0-9_.]+)\.kt""")

private fun KDoc.findSourceReference(): SourceFileRef? {
    val match = sourceFilePattern.find(text) ?: return null
    val qualifiedPath = match.groupValues[1]
    return qualifiedPath.toSourceFileRef()
}

private fun KtFile.toSourceFileRef(): SourceFileRef? {
    val fileName = virtualFile?.name ?: name
    if (!fileName.endsWith(".kt")) {
        return null
    }
    val packageName = packageFqName.asString()
    if (packageName.isBlank()) {
        return null
    }
    return SourceFileRef(
        packageName = packageName,
        fileName = fileName,
    )
}

private fun String.toSourceFileRef(): SourceFileRef? {
    val packageName = substringBeforeLast('.', missingDelimiterValue = "")
    val simpleName = substringAfterLast('.', missingDelimiterValue = "")
    if (packageName.isBlank() || simpleName.isBlank()) {
        return null
    }
    return SourceFileRef(
        packageName = packageName,
        fileName = "$simpleName.kt",
    )
}

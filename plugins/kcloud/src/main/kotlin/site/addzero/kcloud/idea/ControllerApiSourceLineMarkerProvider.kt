package site.addzero.kcloud.idea

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
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

                    val sourceRef = ignoreBrokenPsiOrIndex {
                        declaration.docComment?.findSourceReference()
                    } ?: return@forEach
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

            containingFile.declarations
                .filterIsInstance<KtNamedFunction>()
                .filter(KtNamedFunction::isTopLevel)
                .filterNot(KtNamedFunction::hasPrivateModifier)
                .forEach { declaration ->
                    val markerAnchor = declaration.preferredSourceMarkerAnchor(leafElements) ?: return@forEach
                    if (markerAnchor !in leafElements) {
                        return@forEach
                    }
                    result += NavigationGutterIconBuilder
                        .create(AllIcons.Actions.Forward)
                        .setTargets(generatedDeclarations)
                        .setTooltipText("Jump to generated Ktorfit API")
                        .createLineMarkerInfo(markerAnchor)
                }
        }
    }

    private fun resolveFiles(
        project: Project,
        sourceRef: SourceFileRef,
    ): Collection<OpenFileDescriptor> {
        val candidates = FilenameIndex.getVirtualFilesByName(
            project,
            sourceRef.fileName,
            GlobalSearchScope.projectScope(project),
        )

        return ContainerUtil.mapNotNull(candidates) { virtualFile ->
            ignoreBrokenPsiOrIndex {
                val fileText = VfsUtilCore.loadText(virtualFile)
                if (fileText.packageName() == sourceRef.packageName) {
                    OpenFileDescriptor(project, virtualFile, 0)
                } else {
                    null
                }
            }
        }
    }

    private fun resolveGeneratedDeclarations(
        project: Project,
        sourceRef: SourceFileRef,
    ): Collection<OpenFileDescriptor> {
        val searchScope = GlobalSearchScope.projectScope(project)
        val directCandidates = FilenameIndex.getVirtualFilesByName(
            project,
            sourceRef.generatedApiFileName(),
            searchScope,
        )
        val candidates = if (directCandidates.isNotEmpty()) {
            directCandidates
        } else {
            FilenameIndex.getAllFilesByExt(project, "kt", searchScope)
                .filter { virtualFile ->
                    virtualFile.name.endsWith("Api.kt")
                }
        }
        return ContainerUtil.mapNotNull(candidates) { virtualFile ->
            val requiresExactFileName = directCandidates.isNotEmpty()
            if (!virtualFile.isGeneratedApiCandidateFor(sourceRef, requiresExactFileName)) {
                return@mapNotNull null
            }
            ignoreBrokenPsiOrIndex {
                val fileText = VfsUtilCore.loadText(virtualFile)
                OpenFileDescriptor(
                    project,
                    virtualFile,
                    fileText.generatedApiDeclarationOffset(),
                )
            }
        }
    }
}

private fun KtNamedFunction.hasPrivateModifier(): Boolean = hasModifier(PRIVATE_KEYWORD)

private fun KtNamedFunction.preferredSourceMarkerAnchor(
    leafElements: Set<PsiElement>,
): PsiElement? {
    val nameIdentifier = nameIdentifier
    if (nameIdentifier != null && nameIdentifier in leafElements) {
        return nameIdentifier
    }

    val funKeyword = funKeyword
    if (funKeyword != null && funKeyword in leafElements) {
        return funKeyword
    }

    return nameIdentifier ?: funKeyword ?: firstChild
}

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

private val packagePattern = Regex("""(?m)^\s*package\s+([A-Za-z0-9_.]+)""")
private val interfacePattern = Regex("""\binterface\s+[A-Za-z0-9_]+""")

private fun String.packageName(): String? {
    return packagePattern.find(this)?.groupValues?.getOrNull(1)
}

private fun String.generatedApiDeclarationOffset(): Int {
    return interfacePattern.find(this)?.range?.first ?: 0
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

private fun SourceFileRef.generatedApiFileName(): String {
    return fileName.removeSuffix(".kt") + "Api.kt"
}

private fun VirtualFile.isGeneratedApiCandidateFor(
    sourceRef: SourceFileRef,
    requiresExactFileName: Boolean,
): Boolean {
    if (!isValid || !path.contains("/generated/")) {
        return false
    }
    if (requiresExactFileName && name != sourceRef.generatedApiFileName()) {
        return false
    }

    val fileSourceRef = runCatching {
        VfsUtilCore.loadText(this)
            .let(sourceFilePattern::find)
            ?.groupValues
            ?.getOrNull(1)
            ?.toSourceFileRef()
    }.getOrNull()

    return fileSourceRef == sourceRef
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

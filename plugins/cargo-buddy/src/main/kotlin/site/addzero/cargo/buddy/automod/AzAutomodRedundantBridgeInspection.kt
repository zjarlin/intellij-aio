package site.addzero.cargo.buddy.automod

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.IntentionAndQuickFixAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import org.rust.ide.inspections.RsLocalInspectionTool
import org.rust.lang.core.psi.RsFile
import site.addzero.cargo.buddy.model.CargoCrateResolver
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name

class AzAutomodRedundantBridgeInspection : RsLocalInspectionTool() {
    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WEAK_WARNING

    override fun getGroupDisplayName(): String = "Cargo Buddy"

    override fun getDisplayName(): String = "Redundant az-automod bridge file"

    override fun getShortName(): String = "AzAutomodRedundantBridge"

    override fun checkFile(
        file: PsiFile,
        manager: InspectionManager,
        isOnTheFly: Boolean,
    ): Array<ProblemDescriptor>? {
        val rsFile = file as? RsFile ?: return null
        val virtualFile = rsFile.virtualFile ?: return null
        if (virtualFile.isDirectory || virtualFile.extension != "rs") return null

        val project = rsFile.project
        val cargoCrate = CargoCrateResolver.resolveForFile(project, virtualFile) ?: return null
        val manifestDirectory = Paths.get(cargoCrate.rootPath)
        val filePath = Paths.get(virtualFile.path)
        val rootSources = rootSources(manifestDirectory)
        if (rootSources.isEmpty()) return null

        val redundant = AzAutomodBridgeAnalyzer.redundantBridge(
            file = filePath,
            source = rsFile.text,
            manifestDirectory = manifestDirectory,
            rootSources = rootSources,
        ) ?: return null

        val message = buildString {
            append("This az-automod bridge file is covered by ")
            append(redundant.rootSourcePath.fileName)
            append(" automod::dir!(\"")
            append(redundant.rootMacroPath)
            append("\")")
            if (redundant.visibilityChanges) {
                append("; deleting it uses parent visibility ")
                append(redundant.rootVisibility.ifBlank { "private" })
                append(" instead of ")
                append(redundant.bridgeVisibility.ifBlank { "private" })
            }
        }

        return arrayOf(
            manager.createProblemDescriptor(
                rsFile,
                message,
                isOnTheFly,
                arrayOf<LocalQuickFix>(DeleteAzAutomodBridgeFileQuickFix()),
                ProblemHighlightType.WEAK_WARNING,
            ),
        )
    }

    private fun rootSources(manifestDirectory: Path): List<RootSource> {
        val candidates = listOf(
            manifestDirectory.resolve("src/lib.rs"),
            manifestDirectory.resolve("src/main.rs"),
        )
        return candidates.mapNotNull { path ->
            if (!Files.isRegularFile(path)) return@mapNotNull null
            RootSource(path, Files.readAllBytes(path).toString(Charsets.UTF_8))
        }
    }
}

private class DeleteAzAutomodBridgeFileQuickFix : IntentionAndQuickFixAction() {
    override fun getName(): String = "Delete redundant az-automod bridge file"

    override fun getFamilyName(): String = "Delete redundant az-automod bridge file"

    override fun applyFix(
        project: Project,
        file: PsiFile?,
        editor: com.intellij.openapi.editor.Editor?,
    ) {
        val virtualFile = file?.virtualFile ?: return
        if (!virtualFile.isValid || virtualFile.isDirectory) return
        virtualFile.delete(this)
        LocalFileSystem.getInstance().refreshIoFiles(listOf(java.io.File(virtualFile.path)))
    }
}

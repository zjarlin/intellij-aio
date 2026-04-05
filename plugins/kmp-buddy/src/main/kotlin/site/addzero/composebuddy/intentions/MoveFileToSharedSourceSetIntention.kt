package site.addzero.composebuddy.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.psi.KtFile
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.settings.ComposeBuddySettingsService
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

class MoveFileToSharedSourceSetIntention : PsiElementBaseIntentionAction(), DumbAware {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getText(): String = ComposeBuddyBundle.message("intention.move.file.to.shared")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val file = element.containingFile as? KtFile ?: return false
        return buildMovePlan(project, file) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = element.containingFile as? KtFile ?: return
        val plan = buildMovePlan(project, file)
        if (plan == null) {
            showError(project, editor, ComposeBuddyBundle.message("refactor.move.file.to.shared.error"))
            return
        }
        val targetDirectory = createTargetDirectory(project, plan.targetDirectoryPath)
        if (targetDirectory == null) {
            showError(project, editor, ComposeBuddyBundle.message("refactor.move.file.to.shared.error.directory"))
            return
        }
        MoveFilesOrDirectoriesProcessor(
            project,
            arrayOf(file),
            targetDirectory,
            false,
            false,
            null,
            null,
        ).run()
    }

    private fun createTargetDirectory(project: Project, path: Path): PsiDirectory? {
        return WriteCommandAction.writeCommandAction(project)
            .withName(ComposeBuddyBundle.message("command.move.file.to.shared"))
            .compute<PsiDirectory?, RuntimeException> {
                val virtualDirectory = VfsUtil.createDirectoryIfMissing(path.toString()) ?: return@compute null
                PsiManager.getInstance(project).findDirectory(virtualDirectory)
            }
    }

    private fun buildMovePlan(project: Project, file: KtFile): MovePlan? {
        val virtualFile = file.virtualFile ?: return null
        if (!virtualFile.isInLocalFileSystem) {
            return null
        }
        val projectFileIndex = ProjectFileIndex.getInstance(project)
        val sourceRoot = projectFileIndex.getSourceRootForFile(virtualFile) ?: return null
        val contentRoot = projectFileIndex.getContentRootForFile(virtualFile) ?: return null
        if (!isMainKotlinSourceRoot(contentRoot.path, sourceRoot.path)) {
            return null
        }
        val relativeTargetPath = ComposeBuddySettingsService.getInstance().state.sharedMoveTargetRelativePath.trim()
        if (relativeTargetPath.isEmpty()) {
            return null
        }
        val configuredPath = try {
            Paths.get(relativeTargetPath)
        } catch (_: InvalidPathException) {
            return null
        }
        if (configuredPath.isAbsolute) {
            return null
        }
        val sourceRootPath = Paths.get(sourceRoot.path).normalize()
        val currentFilePath = Paths.get(virtualFile.path).normalize()
        if (!currentFilePath.startsWith(sourceRootPath)) {
            return null
        }
        val targetSourceRootPath = Paths.get(contentRoot.path).resolve(configuredPath).normalize()
        if (currentFilePath.startsWith(targetSourceRootPath)) {
            return null
        }
        val relativeFilePath = sourceRootPath.relativize(currentFilePath)
        val targetFilePath = targetSourceRootPath.resolve(relativeFilePath).normalize()
        if (targetFilePath == currentFilePath) {
            return null
        }
        if (Files.exists(targetFilePath)) {
            return null
        }
        val targetDirectoryPath = targetFilePath.parent ?: return null
        return MovePlan(targetDirectoryPath = targetDirectoryPath)
    }

    private fun isMainKotlinSourceRoot(contentRootPath: String, sourceRootPath: String): Boolean {
        val relativeSourceRoot = try {
            Paths.get(contentRootPath).normalize().relativize(Paths.get(sourceRootPath).normalize())
        } catch (_: IllegalArgumentException) {
            return false
        }
        return SOURCE_ROOT_PATTERN.matches(relativeSourceRoot.toString().replace('\\', '/'))
    }

    private fun showError(project: Project, editor: Editor?, message: String) {
        CommonRefactoringUtil.showErrorHint(
            project,
            editor,
            message,
            ComposeBuddyBundle.message("refactor.move.file.to.shared.title"),
            null,
        )
    }

    private data class MovePlan(
        val targetDirectoryPath: Path,
    )

    companion object {
        private val SOURCE_ROOT_PATTERN = Regex("""src/[^/]+Main/kotlin""")
    }
}

package site.addzero.composeblocks.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.idea.KotlinFileType
import site.addzero.composeblocks.editor.ComposeBlocksFileEditorProvider
import site.addzero.composeblocks.managed.ManagedComposeSourceGenerator
import site.addzero.composeblocks.model.ManagedComposeDocument
import site.addzero.composeblocks.model.ManagedComposeKind

abstract class NewManagedComposeBlocksFileAction(
    private val kind: ManagedComposeKind,
) : DumbAwareAction(
    "New Compose Blocks ${kind.presentableName}",
    "Create a managed Compose Blocks ${kind.presentableName.lowercase()} file",
    AllIcons.FileTypes.UiForm,
) {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val view = event.getData(LangDataKeys.IDE_VIEW) ?: return
        val directory = view.directories.firstOrNull() ?: return

        val requestedName = Messages.showInputDialog(
            project,
            "Name the new ${kind.presentableName.lowercase()} composable:",
            "Compose Blocks ${kind.presentableName}",
            Messages.getQuestionIcon(),
            "Sample${kind.suffix}",
            null,
        ) ?: return

        val managedDocument = createManagedDocument(directory, requestedName)
        val fileName = "${managedDocument.composableName}.kt"
        if (directory.findFile(fileName) != null) {
            Messages.showErrorDialog(
                project,
                "A file named $fileName already exists in ${directory.virtualFile.presentableUrl}.",
                "Compose Blocks ${kind.presentableName}",
            )
            return
        }

        val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
            fileName,
            KotlinFileType.INSTANCE,
            ManagedComposeSourceGenerator.generate(managedDocument),
        )

        WriteCommandAction.runWriteCommandAction(project, "Create Compose Blocks ${kind.presentableName}", null, Runnable {
            val created = directory.add(psiFile) as? PsiFile ?: return@Runnable
            ComposeBlocksFileEditorProvider.openComposeBlocks(project, created.virtualFile)
        })
    }

    override fun update(event: AnActionEvent) {
        val enabled = event.project != null && event.getData(LangDataKeys.IDE_VIEW)?.directories?.isNotEmpty() == true
        event.presentation.isEnabledAndVisible = enabled
    }

    private fun createManagedDocument(
        directory: PsiDirectory,
        requestedName: String,
    ): ManagedComposeDocument {
        var nextId = 1
        val packageName = inferPackageName(directory)
        val normalizedName = requestedName
            .trim()
            .ifBlank { kind.suffix }
            .replace(Regex("[^A-Za-z0-9_]"), "")
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        return ManagedComposeDocument.create(
            kind = kind,
            packageName = packageName,
            baseName = normalizedName,
        ) { "block-${nextId++}" }
    }

    private fun inferPackageName(directory: PsiDirectory): String {
        val path = directory.virtualFile.path
        val packagePath = when {
            "/src/commonMain/kotlin/" in path -> path.substringAfter("/src/commonMain/kotlin/")
            "/src/jvmMain/kotlin/" in path -> path.substringAfter("/src/jvmMain/kotlin/")
            "/src/main/kotlin/" in path -> path.substringAfter("/src/main/kotlin/")
            "/src/main/java/" in path -> path.substringAfter("/src/main/java/")
            "/kotlin/" in path -> path.substringAfter("/kotlin/")
            "/java/" in path -> path.substringAfter("/java/")
            else -> ""
        }
        return packagePath.trim('/').replace('/', '.')
    }
}

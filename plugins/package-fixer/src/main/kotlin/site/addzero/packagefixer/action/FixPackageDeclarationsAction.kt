package site.addzero.packagefixer.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiParserFacade
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

class FixPackageDeclarationsAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        val enabled = project != null && !files.isNullOrEmpty() && files.all { it.isDirectory }
        e.presentation.isEnabledAndVisible = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedDirs = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.filter { it.isDirectory }.orEmpty()
        if (selectedDirs.isEmpty()) return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Fixing package declarations",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = "Scanning files..."

                val filesToProcess = collectJavaKotlinFiles(selectedDirs, indicator)
                if (filesToProcess.isEmpty()) {
                    notify(project, "No Java/Kotlin files found under selected directories.", NotificationType.INFORMATION)
                    return
                }

                val psiManager = PsiManager.getInstance(project)
                val javaDirectoryService = JavaDirectoryService.getInstance()
                val total = filesToProcess.size
                var processed = 0
                var updated = 0
                var skippedNoPackage = 0
                var skippedUnsupported = 0

                for (file in filesToProcess) {
                    indicator.checkCanceled()
                    processed++
                    indicator.fraction = processed.toDouble() / total.toDouble()
                    indicator.text = "Fixing package declarations ($processed/$total)..."

                    val expectedPackage = ReadAction.compute<String?, Throwable> {
                        val parentDir = file.parent ?: return@compute null
                        val psiDirectory = psiManager.findDirectory(parentDir) ?: return@compute null
                        javaDirectoryService.getPackage(psiDirectory)?.qualifiedName
                    }

                    if (expectedPackage == null) {
                        skippedNoPackage++
                        continue
                    }

                    val fileKind = ReadAction.compute<FileKind?, Throwable> {
                        val psiFile = psiManager.findFile(file) ?: return@compute null
                        when (psiFile) {
                            is PsiJavaFile -> FileKind.Java(psiFile.packageName)
                            is KtFile -> {
                                if (psiFile.isScript()) null else FileKind.Kotlin(psiFile.packageFqName.asString())
                            }
                            else -> null
                        }
                    }

                    when (fileKind) {
                        null -> {
                            skippedUnsupported++
                            continue
                        }
                        is FileKind.Java -> {
                            if (fileKind.currentPackage == expectedPackage) continue
                            val updatedNow = updateJavaPackage(project, psiManager, file, expectedPackage)
                            if (updatedNow) updated++
                        }
                        is FileKind.Kotlin -> {
                            if (fileKind.currentPackage == expectedPackage) continue
                            val updatedNow = updateKotlinPackage(project, psiManager, file, expectedPackage)
                            if (updatedNow) updated++
                        }
                    }
                }

                val message = buildSummaryMessage(updated, total, skippedNoPackage, skippedUnsupported)
                val type = if (updated > 0) NotificationType.INFORMATION else NotificationType.WARNING
                notify(project, message, type)
            }
        })
    }

    private fun updateJavaPackage(
        project: Project,
        psiManager: PsiManager,
        file: VirtualFile,
        expectedPackage: String
    ): Boolean {
        var updated = false
        WriteCommandAction.runWriteCommandAction(project, "Fix Package Declaration", null, Runnable {
            val psiFile = psiManager.findFile(file) as? PsiJavaFile ?: return@Runnable
            if (!psiFile.isValid) return@Runnable
            psiFile.setPackageName(expectedPackage)
            updated = true
        })
        return updated
    }

    private fun updateKotlinPackage(
        project: Project,
        psiManager: PsiManager,
        file: VirtualFile,
        expectedPackage: String
    ): Boolean {
        var updated = false
        WriteCommandAction.runWriteCommandAction(project, "Fix Package Declaration", null, Runnable {
            val psiFile = psiManager.findFile(file) as? KtFile ?: return@Runnable
            if (!psiFile.isValid || psiFile.isScript()) return@Runnable
            if (expectedPackage.isEmpty()) {
                psiFile.packageDirective?.delete()
            } else {
                val factory = KtPsiFactory(project)
                val newDirective = factory.createFile("package $expectedPackage").packageDirective
                    ?: return@Runnable
                val existingDirective = psiFile.packageDirective
                if (existingDirective != null) {
                    existingDirective.replace(newDirective)
                } else {
                    val whitespace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n\n")
                    val fileAnnotations = psiFile.fileAnnotationList
                    if (fileAnnotations != null) {
                        val inserted = psiFile.addAfter(newDirective, fileAnnotations)
                        psiFile.addAfter(whitespace, inserted)
                    } else {
                        val firstChild = psiFile.firstChild
                        if (firstChild != null) {
                            val inserted = psiFile.addBefore(newDirective, firstChild)
                            psiFile.addAfter(whitespace, inserted)
                        } else {
                            psiFile.add(newDirective)
                            psiFile.add(whitespace)
                        }
                    }
                }
            }
            updated = true
        })
        return updated
    }

    private fun collectJavaKotlinFiles(
        roots: List<VirtualFile>,
        indicator: ProgressIndicator
    ): List<VirtualFile> {
        val result = LinkedHashSet<VirtualFile>()
        for (root in roots) {
            VfsUtilCore.iterateChildrenRecursively(root, null) { file ->
                indicator.checkCanceled()
                if (!file.isDirectory) {
                    val ext = file.extension?.lowercase()
                    if (ext == "java" || ext == "kt") {
                        result.add(file)
                    }
                }
                true
            }
        }
        return result.toList()
    }

    private fun buildSummaryMessage(
        updated: Int,
        total: Int,
        skippedNoPackage: Int,
        skippedUnsupported: Int
    ): String {
        val parts = mutableListOf<String>()
        parts.add("Package declarations fixed: $updated")
        parts.add("Files scanned: $total")
        if (skippedNoPackage > 0) {
            parts.add("Skipped (no source-root package): $skippedNoPackage")
        }
        if (skippedUnsupported > 0) {
            parts.add("Skipped (unsupported files): $skippedUnsupported")
        }
        return parts.joinToString(" | ")
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Package Fixer")
            .createNotification(message, type)
            .notify(project)
    }

    private sealed class FileKind {
        data class Java(val currentPackage: String) : FileKind()
        data class Kotlin(val currentPackage: String) : FileKind()
    }
}

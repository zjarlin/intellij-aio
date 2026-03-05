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
import com.intellij.psi.PsiImportList
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiParserFacade
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
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
                var updatedPackage = 0
                var updatedImports = 0
                var skippedNoPackage = 0
                var skippedUnsupported = 0

                // 第一阶段：收集所有包名变化映射
                indicator.text = "Collecting package mappings..."
                val packageMapping = collectPackageMapping(filesToProcess, psiManager, javaDirectoryService)

                if (packageMapping.isEmpty()) {
                    notify(project, "No package declarations need to be fixed.", NotificationType.INFORMATION)
                    return
                }

                // 计算前缀替换规则
                val prefixReplacements = computePrefixReplacements(packageMapping)

                // 第二阶段：修复每个文件
                for (file in filesToProcess) {
                    indicator.checkCanceled()
                    processed++
                    indicator.fraction = processed.toDouble() / total.toDouble()
                    indicator.text = "Fixing packages and imports ($processed/$total)..."

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
                            is PsiJavaFile -> FileKind.Java(
                                psiFile.packageName,
                                psiFile.importList?.allImportStatements?.map { it.text } ?: emptyList()
                            )
                            is KtFile -> FileKind.Kotlin(
                                psiFile.packageFqName.asString(),
                                psiFile.importList?.imports?.map { it.text } ?: emptyList()
                            )
                            else -> null
                        }
                    }

                    when (fileKind) {
                        null -> {
                            skippedUnsupported++
                            continue
                        }
                        is FileKind.Java -> {
                            // 修复 package 声明
                            if (fileKind.currentPackage != expectedPackage) {
                                val updatedNow = updateJavaPackage(project, psiManager, file, expectedPackage)
                                if (updatedNow) updatedPackage++
                            }
                            // 修复 import 语句
                            val importsUpdated = fixJavaImports(project, psiManager, file, prefixReplacements)
                            if (importsUpdated) updatedImports++
                        }
                        is FileKind.Kotlin -> {
                            // 修复 package 声明
                            if (fileKind.currentPackage != expectedPackage) {
                                val updatedNow = updateKotlinPackage(project, psiManager, file, expectedPackage)
                                if (updatedNow) updatedPackage++
                            }
                            // 修复 import 语句
                            val importsUpdated = fixKotlinImports(project, psiManager, file, prefixReplacements)
                            if (importsUpdated) updatedImports++
                        }
                    }
                }

                val message = buildSummaryMessage(
                    updatedPackage, updatedImports, total, skippedNoPackage, skippedUnsupported
                )
                val type = if (updatedPackage > 0 || updatedImports > 0) NotificationType.INFORMATION else NotificationType.WARNING
                notify(project, message, type)
            }
        })
    }

    /**
     * 收集所有文件的包名映射（旧包名 -> 新包名）
     */
    private fun collectPackageMapping(
        files: List<VirtualFile>,
        psiManager: PsiManager,
        javaDirectoryService: JavaDirectoryService
    ): Map<String, String> {
        val mapping = mutableMapOf<String, String>()

        ReadAction.run<Throwable> {
            for (file in files) {
                val parentDir = file.parent ?: continue
                val psiDirectory = psiManager.findDirectory(parentDir) ?: continue
                val expectedPackage = javaDirectoryService.getPackage(psiDirectory)?.qualifiedName ?: continue

                val psiFile = psiManager.findFile(file) ?: continue
                val currentPackage = when (psiFile) {
                    is PsiJavaFile -> psiFile.packageName
                    is KtFile -> psiFile.packageFqName.asString()
                    else -> continue
                }

                if (currentPackage.isNotEmpty() && currentPackage != expectedPackage) {
                    mapping[currentPackage] = expectedPackage
                }
            }
        }

        return mapping
    }

    /**
     * 计算前缀替换规则
     * 例如：com.zjarlin.musiclib.api.bilibili -> site.addzero.network.call.musiclib.api.bilibili
     * 共同后缀：musiclib.api.bilibili
     * 替换规则：com.zjarlin.musiclib -> site.addzero.network.call.musiclib
     */
    private fun computePrefixReplacements(packageMapping: Map<String, String>): List<Pair<String, String>> {
        val replacements = mutableListOf<Pair<String, String>>()

        for ((oldPkg, newPkg) in packageMapping) {
            val commonSuffix = findCommonSuffix(oldPkg, newPkg)
            if (commonSuffix.isNotEmpty()) {
                val oldPrefix = oldPkg.removeSuffix(commonSuffix).removeSuffix(".")
                val newPrefix = newPkg.removeSuffix(commonSuffix).removeSuffix(".")
                if (oldPrefix.isNotEmpty() && newPrefix.isNotEmpty() && oldPrefix != newPrefix) {
                    // 添加精确的前缀替换规则（带.确保是包名边界）
                    val oldWithDot = "$oldPrefix."
                    val newWithDot = "$newPrefix."
                    if (!replacements.any { it.first == oldWithDot }) {
                        replacements.add(oldWithDot to newWithDot)
                    }
                }
            }
        }

        // 按前缀长度降序排列，确保最长的前缀先被替换
        return replacements.sortedByDescending { it.first.length }
    }

    /**
     * 找出两个字符串的共同后缀
     */
    private fun findCommonSuffix(oldPkg: String, newPkg: String): String {
        val oldParts = oldPkg.split(".")
        val newParts = newPkg.split(".")

        var commonParts = mutableListOf<String>()
        var i = 1
        while (i <= oldParts.size && i <= newParts.size) {
            if (oldParts[oldParts.size - i] == newParts[newParts.size - i]) {
                commonParts.add(0, oldParts[oldParts.size - i])
                i++
            } else {
                break
            }
        }

        return commonParts.joinToString(".")
    }

    /**
     * 修复 Java 文件的 import 语句
     */
    private fun fixJavaImports(
        project: Project,
        psiManager: PsiManager,
        file: VirtualFile,
        prefixReplacements: List<Pair<String, String>>
    ): Boolean {
        var anyReplaced = false

        WriteCommandAction.runWriteCommandAction(project, "Fix Java Imports", null, Runnable {
            val psiFile = psiManager.findFile(file) as? PsiJavaFile ?: return@Runnable
            if (!psiFile.isValid) return@Runnable

            val importList = psiFile.importList ?: return@Runnable

            // 收集需要替换的 import
            val importsToReplace = mutableListOf<Pair<com.intellij.psi.PsiImportStatementBase, String>>()

            for (importStatement in importList.allImportStatements) {
                val importText = importStatement.text
                val newImport = applyReplacements(importText, prefixReplacements)
                if (newImport != importText) {
                    importsToReplace.add(importStatement to newImport)
                }
            }

            // 执行替换
            for ((oldImport, newImportText) in importsToReplace) {
                try {
                    val newImportStatement = PsiParserFacade.getInstance(project)
                        .createWhiteSpaceFromText(newImportText + "\n")
                    oldImport.replace(newImportStatement)
                    anyReplaced = true
                } catch (e: Exception) {
                    // 如果替换失败，尝试直接修改文本
                    val document = com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(psiFile)
                    if (document != null) {
                        val startOffset = oldImport.textRange.startOffset
                        val endOffset = oldImport.textRange.endOffset
                        document.replaceString(startOffset, endOffset, newImportText)
                        anyReplaced = true
                    }
                }
            }
        })

        return anyReplaced
    }

    /**
     * 修复 Kotlin 文件的 import 语句
     */
    private fun fixKotlinImports(
        project: Project,
        psiManager: PsiManager,
        file: VirtualFile,
        prefixReplacements: List<Pair<String, String>>
    ): Boolean {
        var anyReplaced = false

        WriteCommandAction.runWriteCommandAction(project, "Fix Kotlin Imports", null, Runnable {
            val psiFile = psiManager.findFile(file) as? KtFile ?: return@Runnable
            if (!psiFile.isValid) return@Runnable

            val factory = KtPsiFactory(project)

            // 收集需要替换的 import
            val importsToReplace = mutableListOf<Pair<KtImportDirective, String>>()

            for (importDirective in psiFile.importList?.imports ?: emptyList()) {
                val importText = importDirective.text
                val newImport = applyReplacements(importText, prefixReplacements)
                if (newImport != importText) {
                    importsToReplace.add(importDirective to newImport)
                }
            }

            // 执行替换
            for ((oldImport, newImportText) in importsToReplace) {
                try {
                    val newImportDirective = factory.createFile(newImportText).importList?.imports?.firstOrNull()
                    if (newImportDirective != null) {
                        oldImport.replace(newImportDirective)
                        anyReplaced = true
                    }
                } catch (e: Exception) {
                    // 静默失败，不中断流程
                }
            }
        })

        return anyReplaced
    }

    /**
     * 应用替换规则到文本
     */
    private fun applyReplacements(text: String, replacements: List<Pair<String, String>>): String {
        var result = text
        for ((oldPrefix, newPrefix) in replacements) {
            // 匹配 import 语句中的包名
            val importRegex = "(import\\s+(?:static\\s+)?)\\Q$oldPrefix\\E".toRegex()
            if (importRegex.containsMatchIn(result)) {
                result = importRegex.replace(result, "$1$newPrefix")
            }
        }
        return result
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
            if (!psiFile.isValid) return@Runnable
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
                    if (ext == "java" || ext == "kt" || ext == "kts") {
                        result.add(file)
                    }
                }
                true
            }
        }
        return result.toList()
    }

    private fun buildSummaryMessage(
        updatedPackage: Int,
        updatedImports: Int,
        total: Int,
        skippedNoPackage: Int,
        skippedUnsupported: Int
    ): String {
        val parts = mutableListOf<String>()
        parts.add("Packages fixed: $updatedPackage")
        parts.add("Imports fixed: $updatedImports")
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
        data class Java(val currentPackage: String, val imports: List<String>) : FileKind()
        data class Kotlin(val currentPackage: String, val imports: List<String>) : FileKind()
    }
}

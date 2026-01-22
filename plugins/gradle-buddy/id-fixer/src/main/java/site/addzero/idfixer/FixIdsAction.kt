package site.addzero.idfixer

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.kotlin.psi.KtFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.command.WriteCommandAction
import org.jetbrains.kotlin.psi.*

class FixIdsAction : AnAction() {

    override fun update(e: AnActionEvent) {
        // The action is visible only when a single directory is selected
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        e.presentation.isEnabledAndVisible = psiElement is PsiDirectory
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            Messages.showErrorDialog("Cannot get project.", "Error")
            return
        }

        val selectedDirectory = e.getData(CommonDataKeys.PSI_ELEMENT) as? PsiDirectory
        if (selectedDirectory == null) {
            Messages.showErrorDialog("Please select a directory.", "Error")
            return
        }

        val shortIdToFqIdMap = mutableMapOf<String, String>()

        // Step 1 & 2: Scan convention plugins and build the map
        selectedDirectory.acceptChildren(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitFile(file: PsiFile) {
                if (file.name.endsWith(".gradle.kts")) {
                    val ktFile = file as? KtFile ?: return
                    val shortId = file.name.removeSuffix(".gradle.kts")

                    // Determine the package name
                    // Option 1: Look for an explicit 'package' declaration
                    val packageDirective = ktFile.packageDirective
                    val declaredPackage = packageDirective?.qualifiedName

                    val fqId = if (!declaredPackage.isNullOrEmpty()) {
                        "$declaredPackage.$shortId"
                    } else {
                        // Option 2: Infer from directory structure relative to a source root
                        // This is more complex and requires finding the source root first.
                        // For now, let's assume if there's no package directive, it's just the shortId.
                        // Based on the user's example, it will always be relative to `src/main/kotlin`
                        // in the `build-logic` directory.
                        val sourceRoot = findSourceRoot(selectedDirectory)
                        val relativePath = if (sourceRoot != null) {
                            VfsUtilCore.getRelativePath(file.virtualFile, sourceRoot.virtualFile, '/')
                        } else {
                            VfsUtilCore.getRelativePath(file.virtualFile, selectedDirectory.virtualFile, '/')
                        }
                        // Example: kmp/platform/kmp-core.gradle.kts -> kmp.platform.kmp-core
                        val inferredPackage = relativePath?.substringBeforeLast('/')?.replace('/', '.')
                        if (!inferredPackage.isNullOrEmpty() && inferredPackage != shortId) { // Avoid "my-plugin.my-plugin"
                            "$inferredPackage.$shortId"
                        } else {
                            shortId
                        }
                    }
                    shortIdToFqIdMap[shortId] = fqId
                }
                super.visitFile(file)
            }
        })

        if (shortIdToFqIdMap.isEmpty()) {
            Messages.showInfoMessage("No convention plugin files found in the selected directory.", "Gradle ID Fixer")
            return
        }

        Messages.showInfoMessage("Found convention plugins: $shortIdToFqIdMap", "Gradle ID Fixer")

        // Step 3: Find all build.gradle.kts files in the entire project.
        val buildGradleKtsVirtualFiles = FilenameIndex.getVirtualFilesByName(
            "build.gradle.kts",
            GlobalSearchScope.projectScope(project)
        )

        var replacementsMade = 0
        val psiManager = com.intellij.psi.PsiManager.getInstance(project)

        // Step 4 & 5: Iterate through each build.gradle.kts file and perform replacements
        for (virtualFile in buildGradleKtsVirtualFiles) {
            val psiFile = psiManager.findFile(virtualFile) as? KtFile ?: continue
            // Perform replacements within a WriteCommandAction
            WriteCommandAction.runWriteCommandAction(project) {
                val fileReplacements = replacePluginIdsInFile(psiFile, shortIdToFqIdMap)
                replacementsMade += fileReplacements
            }
        }

        if (replacementsMade > 0) {
            Messages.showInfoMessage("Successfully made $replacementsMade plugin ID replacements.", "Gradle ID Fixer")
        } else {
            Messages.showInfoMessage("No plugin ID replacements were needed.", "Gradle ID Fixer")
        }
    }

    // New helper function to perform replacements in a single file
    private fun replacePluginIdsInFile(file: KtFile, idMap: Map<String, String>): Int {
        var count = 0
        file.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                // Look for 'plugins { ... }' block
                if (element is KtCallExpression && element.calleeExpression?.text == "plugins") {
                    val lambdaArgument = element.lambdaArguments.firstOrNull()?.getLambdaExpression()
                    lambdaArgument?.bodyExpression?.acceptChildren(object : PsiRecursiveElementWalkingVisitor() {
                        override fun visitElement(childElement: PsiElement) {
                            // Look for 'id("...")' calls
                            if (childElement is KtCallExpression && childElement.calleeExpression?.text == "id") {
                                val literalArgument = childElement.valueArguments.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression
                                val shortIdExpression = literalArgument?.entries?.firstOrNull() as? KtLiteralStringTemplateEntry
                                val shortId = shortIdExpression?.text

                                if (shortId != null && idMap.containsKey(shortId)) {
                                    val fqId = idMap[shortId]!!
                                    // Create a new string literal element for the fully qualified ID
                                    val newLiteral = KtPsiFactory(file.project).createStringTemplate(fqId)

                                    // Replace the old literal with the new one
                                    literalArgument.replace(newLiteral)
                                    count++
                                }
                            }
                            super.visitElement(childElement)
                        }
                    })
                }
                super.visitElement(element)
            }
        })
        return count
    }

    private fun findSourceRoot(directory: PsiDirectory): PsiDirectory? {
        var current: PsiDirectory? = directory
        while (current != null) {
            if (current.name == "src") {
                val mainDir = current.findSubdirectory("main")
                if (mainDir != null) {
                    val kotlinDir = mainDir.findSubdirectory("kotlin")
                    if (kotlinDir != null) {
                        return kotlinDir
                    }
                    val javaDir = mainDir.findSubdirectory("java")
                    if (javaDir != null) {
                        return javaDir
                    }
                }
            }
            current = current.parentDirectory
        }
        return null
    }
}

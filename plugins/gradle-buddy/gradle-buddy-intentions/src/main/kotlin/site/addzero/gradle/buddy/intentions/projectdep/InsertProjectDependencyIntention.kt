package site.addzero.gradle.buddy.intentions.projectdep

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiFile

/**
 * Alt+Enter intention: insert `implementation(project(":nearby:module"))` inside a `dependencies {}` block.
 *
 * Lists all project modules sorted by tree path distance from the current module,
 * with distance indicator like `gradle-buddy-core [↕2]`.
 */
class InsertProjectDependencyIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getFamilyName(): String = "Gradle buddy"

    override fun getText(): String = "(Gradle Buddy) Insert project dependency"

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("选择临近模块并插入 implementation(project(\"...\")) 依赖声明。按目录树距离排序。")
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null) return false
        if (!file.name.endsWith(".gradle.kts")) return false
        // Check cursor is inside a dependencies { } block
        return isInsideDependenciesBlock(file, editor.caretModel.offset)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (editor == null) return

        val basePath = project.basePath ?: return
        val currentModulePath = detectModulePathFromFile(file, basePath) ?: return
        val allModules = scanAllModules(basePath)
            .filter { it != currentModulePath } // exclude self

        if (allModules.isEmpty()) return

        // Sort by tree distance
        val sorted = allModules.map { modulePath ->
            val dist = ModulePathDistance.distance(currentModulePath, modulePath)
            ModuleCandidate(modulePath, dist)
        }.sortedBy { it.distance }

        // Show popup with search enabled
        val step = object : BaseListPopupStep<ModuleCandidate>("Select Project Dependency", sorted) {
            override fun getTextFor(value: ModuleCandidate): String {
                val shortName = value.path.substringAfterLast(':')
                return "$shortName [↕${value.distance}]  ${value.path}"
            }

            override fun onChosen(selectedValue: ModuleCandidate, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    insertDependency(project, editor, selectedValue.path)
                }
                return FINAL_CHOICE
            }

            override fun isSpeedSearchEnabled(): Boolean = true

            override fun getIndexedString(value: ModuleCandidate): String {
                // Allow searching by both short name and full path
                return value.path
            }
        }

        JBPopupFactory.getInstance()
            .createListPopup(step)
            .showInBestPositionFor(editor)
    }

    private fun insertDependency(project: Project, editor: Editor, modulePath: String) {
        val depLine = "implementation(project(\"$modulePath\"))"
        val document = editor.document
        val offset = editor.caretModel.offset
        val lineNumber = document.getLineNumber(offset)
        val lineEndOffset = document.getLineEndOffset(lineNumber)

        // Detect indentation from current line
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val currentLineText = document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, lineEndOffset))
        val indent = currentLineText.takeWhile { it.isWhitespace() }

        val textToInsert = "\n$indent$depLine"

        WriteCommandAction.writeCommandAction(project).withName("Insert Project Dependency").run<Throwable> {
            document.insertString(lineEndOffset, textToInsert)
            editor.caretModel.moveToOffset(lineEndOffset + textToInsert.length)
        }
    }

    /**
     * Checks if the offset is inside a `dependencies { ... }` block.
     * Uses a simple heuristic: walking backwards from offset looking for "dependencies" followed by "{".
     */
    private fun isInsideDependenciesBlock(file: PsiFile, offset: Int): Boolean {
        val text = file.text
        if (offset > text.length) return false

        // Find matching braces — count open/close braces backwards
        var braceDepth = 0
        var i = offset - 1
        while (i >= 0) {
            when (text[i]) {
                '}' -> braceDepth++
                '{' -> {
                    if (braceDepth > 0) {
                        braceDepth--
                    } else {
                        // Found the opening brace that contains our offset
                        // Check if the text before it is "dependencies"
                        val before = text.substring(0, i).trimEnd()
                        return before.endsWith("dependencies")
                    }
                }
            }
            i--
        }
        return false
    }

    /**
     * Detect the Gradle module path from a PsiFile.
     */
    private fun detectModulePathFromFile(file: PsiFile, basePath: String): String? {
        val vFile = file.virtualFile ?: return null
        if (!vFile.path.startsWith(basePath)) return null

        var dir = vFile.parent
        while (dir != null && dir.path.startsWith(basePath)) {
            if (dir.findChild("build.gradle.kts") != null || dir.findChild("build.gradle") != null) {
                val rel = dir.path.removePrefix(basePath).trimStart('/')
                return if (rel.isEmpty()) ":" else ":${rel.replace('/', ':')}"
            }
            dir = dir.parent
        }
        return null
    }

    /**
     * Scan the project for all Gradle modules by finding build.gradle.kts / build.gradle files.
     */
    private fun scanAllModules(basePath: String): List<String> {
        val baseDir = LocalFileSystem.getInstance()
            .findFileByPath(basePath) ?: return emptyList()

        val modules = mutableListOf<String>()
        collectModules(baseDir, basePath, modules)
        return modules
    }

    private fun collectModules(dir: VirtualFile, basePath: String, result: MutableList<String>) {
        VfsUtilCore.visitChildrenRecursively(dir, object : VirtualFileVisitor<Void>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (!file.isDirectory) return true

                // Skip hidden directories and build directories
                val name = file.name
                if (name.startsWith(".") || name == "build" || name == "node_modules" || name == "buildSrc") {
                    return false // Skip this directory and its children
                }

                val hasBuildFile = file.findChild("build.gradle.kts") != null || file.findChild("build.gradle") != null
                if (hasBuildFile) {
                    val rel = file.path.removePrefix(basePath).trimStart('/')
                    val modulePath = if (rel.isEmpty()) ":" else ":${rel.replace('/', ':')}"
                    result.add(modulePath)
                }

                return true // Continue visiting children
            }
        })
    }

    private data class ModuleCandidate(val path: String, val distance: Int)
}

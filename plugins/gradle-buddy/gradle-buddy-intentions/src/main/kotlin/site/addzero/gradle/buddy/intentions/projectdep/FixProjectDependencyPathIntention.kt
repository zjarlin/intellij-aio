package site.addzero.gradle.buddy.intentions.projectdep

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Alt+Enter intention: fix broken `implementation(project(":...:leaf"))` by searching real modules via leaf name.
 *
 * Example:
 *   implementation(project(":project:ksp:jimmer-ksp-ext"))  (not exists)
 *   -> implementation(project(":project:compiler:jimmer-ksp-ext"))
 */
class FixProjectDependencyPathIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getFamilyName(): String = "Gradle buddy"

    override fun getText(): String = "(Gradle Buddy) Fix project dependency path (by module name)"

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("使用 project(\":...\") 的末级模块名，在工程中搜索真实模块路径并替换。")
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null) return false
        if (!file.name.endsWith(".gradle.kts")) return false

        val offset = editor.caretModel.offset
        if (!isInsideDependenciesBlock(file, offset)) return false

        val element = file.findElementAt(offset) ?: return false
        return findProjectPathString(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (editor == null) return

        val basePath = project.basePath ?: return
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return
        val stringExpr = findProjectPathString(element) ?: return
        val currentPath = extractLiteralString(stringExpr)?.trim() ?: return
        if (!currentPath.startsWith(":")) return

        val leafName = currentPath.substringAfterLast(':').trim()
        if (leafName.isEmpty()) return

        val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(stringExpr)
        val currentModulePath = detectModulePathFromFile(file, basePath)

        val allModules = scanAllModules(basePath)
        val matched = allModules
            .asSequence()
            .filter { it.substringAfterLast(':') == leafName }
            .filter { it != currentPath }
            .toList()

        if (matched.isEmpty()) {
            notify(project, "No matching module", "Cannot find module named \"$leafName\" in this project.", NotificationType.WARNING)
            return
        }

        val candidates = if (currentModulePath != null) {
            matched.map { ModuleCandidate(it, ModulePathDistance.distance(currentModulePath, it)) }
                .sortedWith(compareBy<ModuleCandidate> { it.distance }.thenBy { it.path })
        } else {
            matched.map { ModuleCandidate(it, 0) }
                .sortedBy { it.path }
        }

        if (candidates.size == 1) {
            replaceString(project, pointer, candidates.first().path)
            return
        }

        val step = object : BaseListPopupStep<ModuleCandidate>("Select Correct Module Path", candidates) {
            override fun getTextFor(value: ModuleCandidate): String {
                return if (currentModulePath != null) {
                    "${value.path.substringAfterLast(':')} [↕${value.distance}]  ${value.path}"
                } else {
                    "${value.path.substringAfterLast(':')}  ${value.path}"
                }
            }

            override fun onChosen(selectedValue: ModuleCandidate, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    replaceString(project, pointer, selectedValue.path)
                }
                return FINAL_CHOICE
            }

            override fun isSpeedSearchEnabled(): Boolean = true

            override fun getIndexedString(value: ModuleCandidate): String {
                return value.path
            }
        }

        JBPopupFactory.getInstance()
            .createListPopup(step)
            .showInBestPositionFor(editor)
    }

    private fun replaceString(project: Project, pointer: SmartPsiElementPointer<KtStringTemplateExpression>, newPath: String) {
        val element = pointer.element ?: return
        val psiFile = element.containingFile
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return
        val range = element.textRange

        WriteCommandAction.writeCommandAction(project)
            .withName("Fix Project Dependency Path")
            .run<Throwable> {
                document.replaceString(range.startOffset, range.endOffset, "\"$newPath\"")
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }
    }

    private fun findProjectPathString(element: PsiElement): KtStringTemplateExpression? {
        val stringExpr = element.parentOfType<KtStringTemplateExpression>(true) ?: return null

        // Only support literal strings (no interpolation) for safe replacement/search.
        if (extractLiteralString(stringExpr) == null) return null

        val call = stringExpr.parentOfType<KtCallExpression>(true) ?: return null
        if (call.calleeExpression?.text != "project") return null

        return stringExpr
    }

    private fun extractLiteralString(expr: KtStringTemplateExpression): String? {
        if (expr.entries.any { it !is KtLiteralStringTemplateEntry }) return null
        return expr.entries.joinToString(separator = "") { it.text }
    }

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(title, content, type)
            .notify(project)
    }

    /**
     * Checks if the offset is inside a `dependencies { ... }` block.
     * Uses a simple heuristic: walking backwards from offset looking for "dependencies" followed by "{".
     */
    private fun isInsideDependenciesBlock(file: PsiFile, offset: Int): Boolean {
        val text = file.text
        if (offset > text.length) return false

        var braceDepth = 0
        var i = offset - 1
        while (i >= 0) {
            when (text[i]) {
                '}' -> braceDepth++
                '{' -> {
                    if (braceDepth > 0) {
                        braceDepth--
                    } else {
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

                val name = file.name
                if (name.startsWith(".") || name == "build" || name == "node_modules" || name == "buildSrc") {
                    return false
                }

                val hasBuildFile = file.findChild("build.gradle.kts") != null || file.findChild("build.gradle") != null
                if (hasBuildFile) {
                    val rel = file.path.removePrefix(basePath).trimStart('/')
                    val modulePath = if (rel.isEmpty()) ":" else ":${rel.replace('/', ':')}"
                    result.add(modulePath)
                }

                return true
            }
        })
    }

    private data class ModuleCandidate(val path: String, val distance: Int)
}

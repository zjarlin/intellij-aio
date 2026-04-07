package site.addzero.gradle.buddy.intentions.projectdep

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

/**
 * 当 project() 指向不存在的模块时，允许直接移除整条依赖声明。
 */
class RemoveBrokenProjectDependencyIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.remove.broken.project.dependency")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            GradleBuddyBundle.message("intention.remove.broken.project.dependency.preview")
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null || !file.name.endsWith(".gradle.kts") || file.name == "settings.gradle.kts") {
            return false
        }
        return findBrokenDependencyTarget(project, file, editor.caretModel.offset) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (editor == null) {
            return
        }

        val target = findBrokenDependencyTarget(project, file, editor.caretModel.offset) ?: return
        val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(target.dependencyCall)

        WriteCommandAction.writeCommandAction(project)
            .withName(GradleBuddyBundle.message("intention.remove.broken.project.dependency.command"))
            .run<Throwable> {
                removeDependency(pointer, project)
            }
    }

    private fun removeDependency(pointer: SmartPsiElementPointer<KtCallExpression>, project: Project) {
        val dependencyCall = pointer.element ?: return
        val psiFile = dependencyCall.containingFile ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return

        val deleteRange = expandToWholeLines(document, dependencyCall.textRange)
        document.deleteString(deleteRange.startOffset, deleteRange.endOffset)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    private fun expandToWholeLines(document: Document, range: TextRange): TextRange {
        val safeEnd = (range.endOffset - 1).coerceAtLeast(range.startOffset)
        val startLine = document.getLineNumber(range.startOffset)
        val endLine = document.getLineNumber(safeEnd)
        val startOffset = document.getLineStartOffset(startLine)
        val endOffset = if (endLine + 1 < document.lineCount) {
            document.getLineStartOffset(endLine + 1)
        } else {
            document.getLineEndOffset(endLine)
        }
        return TextRange(startOffset, endOffset)
    }

    private fun findBrokenDependencyTarget(project: Project, file: PsiFile, offset: Int): BrokenDependencyTarget? {
        val element = file.findElementAt(offset) ?: return null
        return generateSequence(element) { it.parent }
            .filterIsInstance<KtCallExpression>()
            .firstNotNullOfOrNull { resolveBrokenDependencyTarget(project, it) }
    }

    private fun resolveBrokenDependencyTarget(project: Project, dependencyCall: KtCallExpression): BrokenDependencyTarget? {
        val configType = dependencyCall.calleeExpression?.text?.trim().orEmpty()
        if (configType.isBlank() || !isInsideDependenciesBlock(dependencyCall)) {
            return null
        }

        val argumentExpression = dependencyCall.valueArguments.singleOrNull()?.getArgumentExpression() ?: return null
        return when (argumentExpression) {
            is KtCallExpression -> resolveBrokenProjectCall(project, dependencyCall, argumentExpression)
            is KtDotQualifiedExpression -> resolveBrokenProjectAccessor(project, dependencyCall, argumentExpression)
            else -> null
        }
    }

    private fun resolveBrokenProjectCall(
        project: Project,
        dependencyCall: KtCallExpression,
        projectCall: KtCallExpression
    ): BrokenDependencyTarget? {
        if (projectCall.calleeExpression?.text != "project") {
            return null
        }

        val stringExpression = projectCall.valueArguments.singleOrNull()?.getArgumentExpression() as? KtStringTemplateExpression
            ?: return null
        val modulePath = extractLiteralString(stringExpression)?.trim() ?: return null
        if (!modulePath.startsWith(":") || modulePath == ":") {
            return null
        }
        if (ProjectModuleResolver.findByProjectPath(project, modulePath) != null) {
            return null
        }

        return BrokenDependencyTarget(dependencyCall)
    }

    private fun resolveBrokenProjectAccessor(
        project: Project,
        dependencyCall: KtCallExpression,
        expression: KtDotQualifiedExpression
    ): BrokenDependencyTarget? {
        val topExpression = findTopDotExpression(expression)
        val accessor = topExpression.text.trim()
        if (!PROJECTS_ACCESSOR_REGEX.matches(accessor)) {
            return null
        }
        if (!isDependencyLikeArgument(topExpression)) {
            return null
        }
        if (ProjectModuleResolver.findByTypeSafeAccessor(project, accessor) != null) {
            return null
        }

        return BrokenDependencyTarget(dependencyCall)
    }

    private fun extractLiteralString(expression: KtStringTemplateExpression): String? {
        if (expression.entries.any { it !is KtLiteralStringTemplateEntry }) {
            return null
        }
        return expression.entries.joinToString(separator = "") { it.text }
    }

    private fun findTopDotExpression(expression: KtDotQualifiedExpression): KtDotQualifiedExpression {
        var current = expression
        while (current.parent is KtDotQualifiedExpression) {
            current = current.parent as KtDotQualifiedExpression
        }
        return current
    }

    private fun isDependencyLikeArgument(expression: KtDotQualifiedExpression): Boolean {
        val callExpression = expression.parent as? KtCallExpression ?: return false
        return callExpression.valueArguments.singleOrNull()?.getArgumentExpression() == expression
    }

    private fun isInsideDependenciesBlock(element: com.intellij.psi.PsiElement): Boolean {
        var current: com.intellij.psi.PsiElement? = element
        while (current != null) {
            if (current is KtCallExpression && current.calleeExpression?.text == "dependencies") {
                return true
            }
            current = current.parent
        }
        return false
    }

    private data class BrokenDependencyTarget(
        val dependencyCall: KtCallExpression
    )

    private companion object {
        val PROJECTS_ACCESSOR_REGEX = Regex("""^projects\.[A-Za-z0-9_.]+$""")
    }
}

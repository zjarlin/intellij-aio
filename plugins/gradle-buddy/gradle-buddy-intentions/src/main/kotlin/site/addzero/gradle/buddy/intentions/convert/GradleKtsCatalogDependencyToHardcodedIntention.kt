package site.addzero.gradle.buddy.intentions.convert

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression
import site.addzero.gradle.buddy.intentions.catalog.VersionCatalogDependencyHelper

/**
 * 将版本目录引用转换为硬编码依赖字符串
 *
 * 支持格式: implementation(libs.xxx.yyy)
 */
class GradleKtsCatalogDependencyToHardcodedIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle Buddy"

    override fun getText(): String = "(Gradle Buddy) Convert catalog reference to hardcoded dependency"

    override fun startInWriteAction(): Boolean = true

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("将版本目录引用转换为硬编码依赖字符串。")
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!file.name.endsWith(".gradle.kts")) return false
        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset) ?: return false
        return findCatalogDependency(project, element, showWarning = false) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (!file.name.endsWith(".gradle.kts")) return
        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset) ?: return
        val info = findCatalogDependency(project, element, showWarning = true) ?: return

        val dependencyString = "${info.groupId}:${info.artifactId}:${info.version}"
        val newDeclaration = "${info.configuration}(\"$dependencyString\")"

        WriteCommandAction.runWriteCommandAction(project) {
            val callExpr = info.callExpression
            val startOffset = callExpr.textOffset
            val endOffset = startOffset + callExpr.textLength
            val document = file.viewProvider.document ?: return@runWriteCommandAction
            document.replaceString(startOffset, endOffset, newDeclaration)
        }
    }

    private fun findCatalogDependency(
        project: Project,
        element: PsiElement,
        showWarning: Boolean
    ): CatalogDependencyInfo? {
        val callExpr = element.parentOfType<KtCallExpression>(true) ?: return null
        val configuration = callExpr.calleeExpression?.text ?: return null
        if (!isDependencyConfiguration(configuration)) return null

        val firstArg = callExpr.valueArguments.firstOrNull() ?: return null
        val argText = firstArg.getArgumentExpression()?.text ?: return null
        val accessor = extractAccessor(argText) ?: return null

        val resolved = VersionCatalogDependencyHelper.findCatalogDependencyByAccessor(project, accessor)
        if (resolved == null) {
            if (showWarning) {
            Messages.showWarningDialog(
                project,
                "Could not resolve catalog reference: libs.$accessor",
                "Convert Failed"
            )
            }
            return null
        }

        val info = resolved.second
        return CatalogDependencyInfo(
            callExpression = callExpr,
            configuration = configuration,
            groupId = info.groupId,
            artifactId = info.artifactId,
            version = info.currentVersion
        )
    }

    private fun extractAccessor(text: String): String? {
        val trimmed = text.trim()
        val match = Regex("""^libs\.([A-Za-z0-9_.]+?)(?:\.get\(\))?$""").find(trimmed)
        return match?.groupValues?.get(1)
    }

    private fun isDependencyConfiguration(callee: String): Boolean {
        val dependencyConfigurations = setOf(
            "implementation", "api", "compileOnly", "runtimeOnly",
            "testImplementation", "testApi", "testCompileOnly", "testRuntimeOnly",
            "androidTestImplementation", "androidTestApi", "androidTestCompileOnly", "androidTestRuntimeOnly",
            "debugImplementation", "releaseImplementation", "kapt", "ksp",
            "annotationProcessor", "lintChecks"
        )
        return callee in dependencyConfigurations
    }

    private data class CatalogDependencyInfo(
        val callExpression: KtCallExpression,
        val configuration: String,
        val groupId: String,
        val artifactId: String,
        val version: String
    )
}

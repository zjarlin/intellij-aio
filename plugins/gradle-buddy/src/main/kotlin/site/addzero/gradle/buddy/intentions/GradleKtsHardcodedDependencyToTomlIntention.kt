package site.addzero.gradle.buddy.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Gradle KTS Hardcoded Dependency to TOML Intention
 *
 * This intention action allows converting hardcoded dependency strings in build.gradle.kts files
 * to TOML format and replaces the original hardcoded string with a version catalog reference.
 *
 * Priority: HIGH - 在硬编码依赖声明上时优先显示此intention
 *
 * @description Converts the hardcoded dependency string to TOML format and replaces with version catalog reference.
 */
class GradleKtsHardcodedDependencyToTomlIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle buddy"

    override fun getText(): String = "Convert hardcoded dependency to TOML format"

    override fun startInWriteAction(): Boolean = true

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("Converts hardcoded dependency to TOML format.")
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!file.name.endsWith(".gradle.kts")) return false

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset)
        return element != null && detectHardcodedDependency(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (!file.name.endsWith(".gradle.kts")) return

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset) ?: return

        val dependencyInfo = detectHardcodedDependency(element) ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            convertHardcodedDependencyToToml(file, dependencyInfo)
        }
    }

    private fun convertHardcodedDependencyToToml(file: PsiFile, info: HardcodedDependencyInfo) {
        val document = file.viewProvider.document ?: return
        val text = document.text

        // 生成 TOML 格式的库声明
        val libraryName = generateLibraryName(info)
        val tomlFormat = generateTomlFormat(info, libraryName)

        // 替换原始硬编码依赖
        val startOffset = text.indexOf(info.originalText, info.approximateOffset.coerceAtLeast(0))
        if (startOffset >= 0) {
            val endOffset = startOffset + info.originalText.length
            val catalogReference = "libs.$libraryName"
            document.replaceString(startOffset, endOffset, catalogReference)

            // 在文件末尾添加 TOML 格式注释
            val comment = "\n\n# Add to libs.versions.toml:\n# $tomlFormat"
            document.insertString(document.textLength, comment)
        }
    }

    private fun generateLibraryName(info: HardcodedDependencyInfo): String {
        // 根据 group ID 和 artifact ID 生成库名
        val groupPart = info.groupId.split(".").last()
        val artifactPart = info.artifactId.split("-").joinToString("") { part ->
            part.replaceFirstChar { it.uppercase() }
        }
        return "${groupPart}${artifactPart}"
    }

    private fun generateTomlFormat(info: HardcodedDependencyInfo, libraryName: String): String {
        val versionKey = "${libraryName.lowercase()}-version"

        return """[versions]
$versionKey = "${info.version}"

[libraries]
$libraryName = { group = "${info.groupId}", name = "${info.artifactId}", version.ref = "$versionKey" }"""
    }

    private fun detectHardcodedDependency(element: PsiElement): HardcodedDependencyInfo? {
        var current: PsiElement? = element

        // 向上遍历查找 KtCallExpression
        while (current != null) {
            if (current is KtCallExpression) {
                val callExpression = current

                // 检查是否是依赖声明
                if (isDependencyDeclaration(callExpression)) {
                    return extractDependencyInfo(callExpression)
                }
            }
            current = current.parent
        }

        return null
    }

    private fun isDependencyDeclaration(callExpr: KtCallExpression): Boolean {
        val callee = callExpr.calleeExpression?.text
        val dependencyKeywords = setOf(
            "implementation", "api", "compileOnly", "runtimeOnly", "testImplementation",
            "testApi", "testCompileOnly", "testRuntimeOnly", "androidTestImplementation",
            "androidTestApi", "androidTestCompileOnly", "androidTestRuntimeOnly",
            "debugImplementation", "releaseImplementation"
        )
        return callee in dependencyKeywords
    }

    private fun extractDependencyInfo(callExpr: KtCallExpression): HardcodedDependencyInfo? {
        val text = callExpr.text

        // 匹配格式: implementation("group:artifact:version")
        val dependencyPattern = Regex("""(\w+)\s*\(\s*["']([^:]+):([^:]+):([^"']+)["']\s*\)""")
        val match = dependencyPattern.find(text)
        if (match != null) {
            val (_, config, groupId, artifactId, version) = match.destructured
            return HardcodedDependencyInfo(
                groupId = groupId,
                artifactId = artifactId,
                version = version,
                configuration = config,
                originalText = match.value,
                approximateOffset = callExpr.textOffset
            )
        }

        return null
    }

    private data class HardcodedDependencyInfo(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val configuration: String,
        val originalText: String,
        val approximateOffset: Int
    )
}
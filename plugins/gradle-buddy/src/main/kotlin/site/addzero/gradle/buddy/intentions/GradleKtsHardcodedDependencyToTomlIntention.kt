package site.addzero.gradle.buddy.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
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
class GradleKtsHardcodedDependencyToTomlIntention : PsiElementBaseIntentionAction(), IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle buddy"

    override fun getText(): String = "Convert hardcoded dependency to TOML format"

    override fun startInWriteAction(): Boolean = true

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("Converts hardcoded dependency to TOML format.")
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val file = element.containingFile ?: return false
        val fileName = file.name

        return when {
            fileName.endsWith(".gradle.kts") -> detectHardcodedDependency(element) != null
            else -> false
        }
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = element.containingFile ?: return

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
        val tomlFormat = generateTomlLibraryFormat(libraryName, info)
        val replacement = "libs.$libraryName"

        // 1. 在当前行之后插入 TOML 格式的注释
        val currentLine = IntentionUtils.getLineNumber(document, info.element.textOffset)
        val lineEnd = document.getLineEndOffset(currentLine)
        val nextLineStart = if (currentLine + 1 < document.lineCount) {
            document.getLineStartOffset(currentLine + 1)
        } else {
            lineEnd
        }

        // 2. 替换硬编码的依赖字符串为版本目录引用
        val oldText = info.fullMatch
        val newText = oldText.replace("\"${info.groupId}:${info.artifactId}:${info.version}\"", replacement)

        val startOffset = text.indexOf(oldText, info.approximateOffset.coerceAtLeast(0))
        if (startOffset >= 0) {
            // 3. 先替换依赖声明
            document.replaceString(startOffset, startOffset + oldText.length, newText)

            // 4. 再插入 TOML 格式注释
            val insertText = if (lineEnd < nextLineStart && document.getText(TextRange(lineEnd, nextLineStart)).trim().isEmpty()) {
                "\n// TOML format:\n// $tomlFormat"
            } else {
                "\n\n// TOML format:\n// $tomlFormat"
            }

            // 由于前面已经修改了文档，需要重新计算位置
            val updatedLineEnd = document.getLineEndOffset(currentLine)
            document.insertString(updatedLineEnd, insertText)
        }
    }

    private fun generateLibraryName(info: HardcodedDependencyInfo): String {
        // 生成合适的库名称，例如：tool-curl -> tool-curl
        // 或者：com.squareup.okhttp3 -> okhttp
        val baseName = when {
            info.artifactId.startsWith("tool-") -> info.artifactId
            info.groupId.contains("okhttp") -> "okhttp"
            info.groupId.contains("jackson") -> when {
                info.artifactId.contains("kotlin") -> "jackson-kotlin"
                else -> "jackson"
            }
            info.artifactId.length < 15 -> info.artifactId
            else -> {
                // 截取 artifactId 的第一部分
                info.artifactId.split("-").first()
            }
        }

        return baseName.replace(".", "-")
    }

    private fun generateTomlLibraryFormat(libraryName: String, info: HardcodedDependencyInfo): String {
        return "[libraries.$libraryName]\ngroup = \"${info.groupId}\"\nname = \"${info.artifactId}\"\nversion = \"${info.version}\""
    }

    private fun detectHardcodedDependency(element: PsiElement): HardcodedDependencyInfo? {
        // 查找包含当前元素的 dependencies 或 implementation 等块
        var current: PsiElement? = element
        while (current != null) {
            // 检查是否在依赖块内
            if (isInDependenciesBlock(current)) {
                // 查找具体的硬编码依赖声明
                return findHardcodedDependency(element)
            }
            current = current.parent
        }

        return null
    }

    private fun isInDependenciesBlock(element: PsiElement): Boolean {
        val text = element.text
        val parentText = element.parent?.text ?: ""

        return text.contains("dependencies") ||
               text.contains("implementation") ||
               text.contains("api") ||
               text.contains("compileOnly") ||
               text.contains("runtimeOnly") ||
               text.contains("testImplementation") ||
               parentText.contains("dependencies") ||
               parentText.contains("implementation") ||
               parentText.contains("api") ||
               parentText.contains("compileOnly") ||
               parentText.contains("runtimeOnly") ||
               parentText.contains("testImplementation")
    }

    private fun findHardcodedDependency(element: PsiElement): HardcodedDependencyInfo? {
        // 向上查找 KtCallExpression
        var current: PsiElement? = element
        while (current != null) {
            if (current is KtCallExpression) {
                val callee = current.calleeExpression?.text
                if (callee in listOf("implementation", "api", "compileOnly", "runtimeOnly", "testImplementation")) {
                    val firstArgument = current.valueArguments.firstOrNull()
                    val argumentText = firstArgument?.getArgumentExpression()?.text

                    if (argumentText != null && argumentText.contains(":") && argumentText.count { it == ':' } >= 2) {
                        // 解析 "group:artifact:version" 格式
                        val dependencyPattern = Regex("""["']([^:]+):([^:]+):([^"']+)["']""")
                        val match = dependencyPattern.find(argumentText)

                        if (match != null) {
                            val (_, groupId, artifactId, version) = match.destructured
                            return HardcodedDependencyInfo(
                                groupId = groupId,
                                artifactId = artifactId,
                                version = version,
                                fullMatch = argumentText,
                                approximateOffset = current.textOffset - 100,
                                element = current,
                                configuration = callee ?: "implementation"
                            )
                        }
                    }
                }
            }
            current = current.parent
        }

        // 如果没有找到 call expression，尝试从文本中解析
        val lineText = IntentionUtils.getLineText(element)

        // 格式: implementation("group:artifact:version")
        val dependencyPattern = Regex("""(\w+)\s*\(\s*["']([^:]+):([^:]+):([^"']+)["']\s*\)""")
        val match = dependencyPattern.find(lineText)

        if (match != null) {
            val (_, configuration, groupId, artifactId, version) = match.destructured
            return HardcodedDependencyInfo(
                groupId = groupId,
                artifactId = artifactId,
                version = version,
                fullMatch = match.value,
                approximateOffset = element.textOffset - 100,
                element = element,
                configuration = configuration
            )
        }

        return null
    }

    
    private data class HardcodedDependencyInfo(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val fullMatch: String,
        val approximateOffset: Int,
        val element: PsiElement,
        val configuration: String
    )
}
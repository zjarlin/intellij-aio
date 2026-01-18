package site.addzero.gradle.buddy.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * 将硬编码依赖转换为版本目录格式的意图操作
 *
 * 此意图操作允许将 build.gradle.kts 文件中的硬编码依赖字符串
 * 转换为 TOML 版本目录格式，并用版本目录引用替换原始硬编码字符串。
 *
 * 优先级：高 - 在硬编码依赖声明时优先显示此意图操作
 *
 * @description 将硬编码依赖字符串转换为 TOML 格式并用版本目录引用替换
 */
class GradleKtsHardcodedDependencyToTomlIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle Buddy"

    override fun getText(): String = "将依赖转换为版本目录格式 (TOML)"

    override fun startInWriteAction(): Boolean = true

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("将硬编码依赖转换为使用版本目录引用。")
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!file.name.endsWith(".gradle.kts")) return false

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset)
        return element != null && findHardcodedDependency(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (!file.name.endsWith(".gradle.kts")) return

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset) ?: return

        val dependencyInfo = findHardcodedDependency(element) ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            convertToVersionCatalog(file, dependencyInfo)
        }
    }

    // 查找硬编码依赖
    private fun findHardcodedDependency(element: PsiElement): DependencyInfo? {
        // 向上查找 KtCallExpression
        val callExpr = element.parentOfType<KtCallExpression>(true) ?: return null

        // 检查是否是依赖声明
        val callee = callExpr.calleeExpression?.text ?: return null
        if (!isDependencyConfiguration(callee)) return null

        // 提取依赖信息
        return extractDependencyInfo(callExpr, callee)
    }

    // 检查是否是依赖配置
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

    // 提取依赖信息
    private fun extractDependencyInfo(callExpr: KtCallExpression, configuration: String): DependencyInfo? {
        // 获取第一个参数（依赖字符串）
        val firstArg = callExpr.valueArguments.firstOrNull() ?: return null

        // 提取字符串内容
        val dependencyString = when (val arg = firstArg.getArgumentExpression()) {
            is KtStringTemplateExpression -> arg.text.trim('"', '\'')
            else -> return null
        }

        // 解析依赖字符串格式
        // 支持格式:
        // 1. "group:artifact:version"
        // 2. "group:artifact:version@classifier"
        // 3. "group:artifact:version:extension@classifier"
        val parts = dependencyString.split(":")
        if (parts.size < 3) return null

        val groupId = parts[0]
        val artifactId = parts[1]
        var version = parts[2]
        var classifier: String? = null
        var extension: String? = null

        // 处理扩展和分类器
        if (parts.size > 3) {
            if (parts[3].contains("@")) {
                val extParts = parts[3].split("@")
                extension = extParts[0]
                classifier = extParts.getOrNull(1)
            } else {
                extension = parts[3]
                if (parts.size > 4 && parts[4].startsWith("@")) {
                    classifier = parts[4].substring(1)
                }
            }
        }

        // 检查是否已经是版本目录引用
        if (dependencyString.startsWith("libs.") || dependencyString.contains(".libs.")) {
            return null
        }

        return DependencyInfo(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            classifier = classifier,
            extension = extension,
            configuration = configuration,
            originalText = callExpr.text,
            element = callExpr
        )
    }

    // 转换为版本目录格式
    private fun convertToVersionCatalog(file: PsiFile, info: DependencyInfo) {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return

        // 生成版本目录中的名称
        val libraryKey = generateLibraryKey(info.groupId, info.artifactId)
        val versionKey = generateVersionKey(info.groupId, info.artifactId)

        // 生成 TOML 格式
        val tomlContent = generateTomlContent(info, libraryKey, versionKey)

        // 生成新的依赖声明
        val dependencyRef = when {
            info.classifier != null && info.extension != null -> {
                "libs.$libraryKey(\"${info.extension}\", \"${info.classifier}\")"
            }
            info.classifier != null -> {
                "libs.$libraryKey(classifier = \"${info.classifier}\")"
            }
            info.extension != null -> {
                "libs.$libraryKey(\"${info.extension}\")"
            }
            else -> {
                "libs.$libraryKey"
            }
        }

        // 生成完整的依赖声明
        val newDeclaration = "${info.configuration}($dependencyRef)"

        // 替换原始声明
        val startOffset = info.element.textOffset
        val endOffset = startOffset + info.element.text.length
        document.replaceString(startOffset, endOffset, newDeclaration)

        // 在文件末尾添加 TOML 配置注释
        val comment = "\n\n// 添加到 gradle/libs.versions.toml:\n$tomlContent"
        document.insertString(document.textLength, comment)
    }

    // 生成库键名
    private fun generateLibraryKey(groupId: String, artifactId: String): String {
        return artifactId
            .replace(".", "-")
            .replace("_", "-")
            .lowercase()
    }

    // 生成版本键名
    private fun generateVersionKey(groupId: String, artifactId: String): String {
        // 直接使用 artifactId 的 kebab-case 格式
        val libraryKey = generateLibraryKey(groupId, artifactId)
        return "${libraryKey}-version"
    }

    // 生成 TOML 内容
    private fun generateTomlContent(info: DependencyInfo, libraryKey: String, versionKey: String): String {
        val builder = StringBuilder()

        // Versions section
        builder.appendLine("[versions]")
        builder.appendLine("$versionKey = \"${info.version}\"")

        // Libraries section
        builder.appendLine()
        builder.appendLine("[libraries]")
        builder.append("$libraryKey = { group = \"${info.groupId}\", name = \"${info.artifactId}\", version.ref = \"$versionKey\"")

        // 如果有分类器，添加它
        if (info.classifier != null) {
            builder.appendLine(", classifier = \"${info.classifier}\"")
        }

        // 关闭库声明
        builder.appendLine(" }")

        return builder.toString()
    }

    private data class DependencyInfo(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val classifier: String?,
        val extension: String?,
        val configuration: String,
        val originalText: String,
        val element: PsiElement
    )
}

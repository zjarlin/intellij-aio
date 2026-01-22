package site.addzero.gradle.buddy.intentions.convert

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import java.io.File

/**
 * 将硬编码依赖转换为版本目录格式的意图操作
 *
 * 此意图操作允许将 build.gradle.kts 文件中的硬编码依赖字符串
 * 潬换为 TOML 版本目录格式，并自动合并到 libs.versions.toml 文件中。
 *
 * 优先级：高 - 在硬编码依赖声明时优先显示此意图操作
 *
 * @description 将硬编码依赖字符串转换为 TOML 格式并合并到版本目录文件
 */
class GradleKtsHardcodedDependencyToTomlIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle Buddy"

    override fun getText(): String = "(gradle-buddy)将依赖转换为版本目录格式 (TOML)"

    override fun startInWriteAction(): Boolean = true

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("将硬编码依赖转换为使用版本目录引用并合并到 libs.versions.toml。")
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

    // 转换为版本目录格式并合并到 libs.versions.toml
    private fun convertToVersionCatalog(file: PsiFile, info: DependencyInfo) {
        val project = file.project
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return

        // 生成版本目录中的名称
        val libraryKey = generateLibraryKey(info.groupId, info.artifactId)
        val versionKey = generateVersionKey(info.groupId, info.artifactId)

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

        WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(startOffset, endOffset, newDeclaration)

            // 合并到 libs.versions.toml
            mergeToVersionCatalog(project, info, libraryKey, versionKey)
        }
    }

    // 合并到版本目录文件
    private fun mergeToVersionCatalog(project: Project, info: DependencyInfo, libraryKey: String, versionKey: String) {
        val catalogPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        val basePath = project.basePath ?: return
        val catalogFile = File(basePath, catalogPath)

        // 确保目录存在
        val catalogDir = catalogFile.parentFile
        if (!catalogDir.exists()) {
            catalogDir.mkdirs()
        }

        // 解析现有内容
        val existingContent = if (catalogFile.exists()) {
            parseVersionCatalog(catalogFile.readText())
        } else {
            VersionCatalogContent(versions = mutableMapOf(), libraries = mutableMapOf())
        }

        // 添加新的版本和库
        existingContent.versions[versionKey] = info.version
        existingContent.libraries[libraryKey] = LibraryEntry(
            alias = libraryKey,
            groupId = info.groupId,
            artifactId = info.artifactId,
            versionRef = versionKey,
            classifier = info.classifier
        )

        // 写入文件
        writeVersionCatalog(catalogFile, existingContent)

        // 刷新虚拟文件系统
        LocalFileSystem.getInstance().refreshAndFindFileByPath(catalogFile.absolutePath)
    }

    // 解析版本目录文件
    private fun parseVersionCatalog(content: String): VersionCatalogContent {
        val versions = mutableMapOf<String, String>()
        val libraries = mutableMapOf<String, LibraryEntry>()

        var inVersions = false
        var inLibraries = false

        content.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed == "[versions]" -> {
                    inVersions = true; inLibraries = false
                }

                trimmed == "[libraries]" -> {
                    inVersions = false; inLibraries = true
                }

                trimmed.startsWith("[") -> {
                    inVersions = false; inLibraries = false
                }

                inVersions && trimmed.contains("=") -> {
                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size == 2) {
                        val name = parts[0].trim()
                        val version = parts[1].trim().removeSurrounding("\"")
                        versions[name] = version
                    }
                }

                inLibraries && trimmed.contains("=") -> {
                    val aliasMatch = Regex("""^([\w-]+)\s*=""").find(trimmed)
                    val groupMatch = Regex("""group\s*=\s*"([^"]+)"""").find(trimmed)
                    val nameMatch = Regex("""name\s*=\s*"([^"]+)"""").find(trimmed)
                    val versionRefMatch = Regex("""version\.ref\s*=\s*"([^"]+)"""").find(trimmed)
                    val classifierMatch = Regex("""classifier\s*=\s*"([^"]+)"""").find(trimmed)

                    if (aliasMatch != null && groupMatch != null && nameMatch != null) {
                        val alias = aliasMatch.groupValues[1]
                        libraries[alias] = LibraryEntry(
                            alias = alias,
                            groupId = groupMatch.groupValues[1],
                            artifactId = nameMatch.groupValues[1],
                            versionRef = versionRefMatch?.groupValues?.get(1) ?: alias,
                            classifier = classifierMatch?.groupValues?.get(1)
                        )
                    }
                }
            }
        }

        return VersionCatalogContent(versions, libraries)
    }

    // 写入版本目录文件
    private fun writeVersionCatalog(file: File, content: VersionCatalogContent) {
        val tomlBuilder = StringBuilder()

        // Versions section
        tomlBuilder.appendLine("[versions]")
        content.versions.toSortedMap().forEach { (name, version) ->
            tomlBuilder.appendLine("$name = \"$version\"")
        }

        // Libraries section
        tomlBuilder.appendLine()
        tomlBuilder.appendLine("[libraries]")
        content.libraries.toSortedMap().forEach { (alias, entry) ->
            tomlBuilder.append("$alias = { group = \"${entry.groupId}\", name = \"${entry.artifactId}\", version.ref = \"${entry.versionRef}\"")

            // 如果有分类器，添加它
            if (entry.classifier != null) {
                tomlBuilder.appendLine(", classifier = \"${entry.classifier}\"")
            }

            tomlBuilder.appendLine(" }")
        }

        file.writeText(tomlBuilder.toString())
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
        val libraryKey = generateLibraryKey(groupId, artifactId)
        return "${libraryKey}-version"
    }

    // 版本目录内容
    data class VersionCatalogContent(
        val versions: MutableMap<String, String>,
        val libraries: MutableMap<String, LibraryEntry>
    )

    // 库条目
    data class LibraryEntry(
        val alias: String,
        val groupId: String,
        val artifactId: String,
        val versionRef: String,
        val classifier: String? = null
    )

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

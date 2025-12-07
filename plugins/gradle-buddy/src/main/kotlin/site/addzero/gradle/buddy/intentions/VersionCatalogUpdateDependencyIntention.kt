package site.addzero.gradle.buddy.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import site.addzero.gradle.buddy.util.StringUtils.toCamelCaseByDelimiters
import site.addzero.gradle.buddy.util.StringUtils.toKebabCase
import site.addzero.network.call.maven.util.MavenCentralSearchUtil

/**
 * Version Catalog Update Dependency Intention
 *
 * 在 libs.versions.toml 文件中升级依赖版本
 * 支持格式:
 * - junit-jupiter-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "jupiter" }
 * - junit-jupiter-api = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "jupiter" }
 * - junit-jupiter-api = { module = "org.junit.jupiter:junit-jupiter-api", version = "5.10.0" }
 * 
 * Priority: HIGH - 在版本目录文件中优先显示此intention
 */
class VersionCatalogUpdateDependencyIntention : PsiElementBaseIntentionAction(), IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle buddy"

    override fun getText(): String = "Update dependency to latest version"

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("Fetches the latest version from Maven Central and updates the version catalog dependency.")
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val file = element.containingFile ?: return false
        return file.name == "libs.versions.toml" && detectCatalogDependency(element) != null
    }


    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = element.containingFile ?: return
        val dependencyInfo = detectCatalogDependency(element) ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Fetching latest version...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val latestVersion = runCatching {
                    MavenCentralSearchUtil.getLatestVersion(dependencyInfo.groupId, dependencyInfo.artifactId)
                }.getOrNull()

                ApplicationManager.getApplication().invokeLater {
                    if (latestVersion == null) {
                        Messages.showWarningDialog(
                            project,
                            "Could not find latest version for ${dependencyInfo.groupId}:${dependencyInfo.artifactId}",
                            "Update Failed"
                        )
                        return@invokeLater
                    }

                    val currentVersion = dependencyInfo.resolvedVersion
                    if (latestVersion == currentVersion) {
                        Messages.showInfoMessage(
                            project,
                            "Already at latest version: $latestVersion",
                            "No Update Needed"
                        )
                        return@invokeLater
                    }

                    WriteCommandAction.runWriteCommandAction(project) {
                        updateVersion(file, dependencyInfo, latestVersion)
                    }
                }
            }
        })
    }

    private fun updateVersion(file: PsiFile, info: CatalogDependencyInfo, newVersion: String) {
        val document = file.viewProvider.document ?: return
        val text = document.text

        when (info.versionType) {
            VersionType.VERSION_REF -> {
                // 查找并更新 [versions] 部分中的版本
                val versionRef = info.versionRef ?: return
                updateVersionRef(document, text, versionRef, newVersion)
            }
            VersionType.INLINE_VERSION -> {
                // 直接替换行内版本
                val oldLine = info.fullLine
                val newLine = oldLine.replace(
                    Regex("""version\s*=\s*"[^"]+""""),
                    """version = "$newVersion""""
                )
                val lineStart = text.indexOf(oldLine)
                if (lineStart >= 0) {
                    document.replaceString(lineStart, lineStart + oldLine.length, newLine)
                }
            }
            VersionType.NEEDS_VERSION_REF -> {
                // 需要创建 version.ref
                val generatedRef = generateVersionRef(info.groupId, info.artifactId)

                // 1. 在 [versions] 部分添加新版本
                addVersionToVersionsSection(document, text, generatedRef, newVersion)

                // 2. 更新库定义，添加 version.ref
                val updatedText = document.text
                updateLibraryWithVersionRef(document, updatedText, info, generatedRef)
            }
        }
    }

    private fun updateVersionRef(document: com.intellij.openapi.editor.Document, text: String, versionRef: String, newVersion: String) {
        // 在 [versions] 部分查找并更新版本
        val versionPattern = Regex("""(?m)^$versionRef\s*=\s*"[^"]+"""")
        val match = versionPattern.find(text)
        if (match != null) {
            val newLine = """$versionRef = "$newVersion""""
            document.replaceString(match.range.first, match.range.last + 1, newLine)
        }
    }

    private fun addVersionToVersionsSection(document: com.intellij.openapi.editor.Document, text: String, versionRef: String, version: String) {
        // 查找 [versions] 部分的末尾
        val versionsStart = text.indexOf("[versions]")
        if (versionsStart < 0) return

        // 查找下一个 section 或文件末尾
        val nextSectionPattern = Regex("""\n\s*\[""")
        val nextSection = nextSectionPattern.find(text, versionsStart + 10)
        val insertPos = nextSection?.range?.first ?: text.length

        // 找到合适的插入位置（在最后一个版本定义之后）
        val newLine = "\n$versionRef = \"$version\""
        document.insertString(insertPos, newLine)
    }

    private fun updateLibraryWithVersionRef(document: com.intellij.openapi.editor.Document, text: String, info: CatalogDependencyInfo, versionRef: String) {
        val oldLine = info.fullLine
        val lineStart = text.indexOf(oldLine)
        if (lineStart < 0) return

        val newLine = when {
            // module = "group:artifact" 格式，添加 version.ref
            info.hasModule -> {
                val modulePattern = Regex("""module\s*=\s*"[^"]+"""")
                val moduleMatch = modulePattern.find(oldLine) ?: return
                val beforeModule = oldLine.substring(0, moduleMatch.range.first)
                val afterModule = oldLine.substring(moduleMatch.range.last + 1).trimEnd().removeSuffix("}")
                """$beforeModule${moduleMatch.value}, version.ref = "$versionRef" }"""
            }
            // group/name 格式，添加 version.ref
            else -> {
                val closingBrace = oldLine.lastIndexOf("}")
                if (closingBrace < 0) return
                val beforeBrace = oldLine.substring(0, closingBrace).trimEnd()
                val needsComma = !beforeBrace.endsWith(",")
                """$beforeBrace${if (needsComma) "," else ""} version.ref = "$versionRef" }"""
            }
        }

        document.replaceString(lineStart, lineStart + oldLine.length, newLine)
    }

    private fun detectCatalogDependency(element: PsiElement): CatalogDependencyInfo? {
        val lineText = getLineText(element)
        if (lineText.isBlank() || lineText.trimStart().startsWith("#")) return null

        // 跳过 [versions], [plugins], [bundles] 等 section 标题
        if (lineText.trim().startsWith("[")) return null

        val file = element.containingFile ?: return null
        val fullText = file.text

        // 解析 module = "group:artifact" 格式
        val modulePattern = Regex("""module\s*=\s*"([^:]+):([^"]+)"""")
        val moduleMatch = modulePattern.find(lineText)
        if (moduleMatch != null) {
            val groupId = moduleMatch.groupValues[1]
            val artifactId = moduleMatch.groupValues[2]
            return parseVersionInfo(lineText, fullText, groupId, artifactId, hasModule = true)
        }

        // 解析 group = "...", name = "..." 格式
        val groupPattern = Regex("""group\s*=\s*"([^"]+)"""")
        val namePattern = Regex("""name\s*=\s*"([^"]+)"""")
        val groupMatch = groupPattern.find(lineText)
        val nameMatch = namePattern.find(lineText)

        if (groupMatch != null && nameMatch != null) {
            val groupId = groupMatch.groupValues[1]
            val artifactId = nameMatch.groupValues[1]
            return parseVersionInfo(lineText, fullText, groupId, artifactId, hasModule = false)
        }

        return null
    }

    private fun parseVersionInfo(
        lineText: String,
        fullText: String,
        groupId: String,
        artifactId: String,
        hasModule: Boolean
    ): CatalogDependencyInfo {
        // 检查 version.ref
        val versionRefPattern = Regex("""version\.ref\s*=\s*"([^"]+)"""")
        val versionRefMatch = versionRefPattern.find(lineText)

        // 检查内联 version
        val inlineVersionPattern = Regex("""version\s*=\s*"([^"]+)"""")
        val inlineVersionMatch = inlineVersionPattern.find(lineText)

        return when {
            versionRefMatch != null -> {
                val versionRef = versionRefMatch.groupValues[1]
                val resolvedVersion = resolveVersionRef(fullText, versionRef)
                CatalogDependencyInfo(
                    groupId = groupId,
                    artifactId = artifactId,
                    versionRef = versionRef,
                    resolvedVersion = resolvedVersion,
                    versionType = VersionType.VERSION_REF,
                    fullLine = lineText,
                    hasModule = hasModule
                )
            }
            inlineVersionMatch != null -> {
                val version = inlineVersionMatch.groupValues[1]
                CatalogDependencyInfo(
                    groupId = groupId,
                    artifactId = artifactId,
                    versionRef = null,
                    resolvedVersion = version,
                    versionType = VersionType.INLINE_VERSION,
                    fullLine = lineText,
                    hasModule = hasModule
                )
            }
            else -> {
                // 没有版本信息，需要添加 version.ref
                CatalogDependencyInfo(
                    groupId = groupId,
                    artifactId = artifactId,
                    versionRef = null,
                    resolvedVersion = null,
                    versionType = VersionType.NEEDS_VERSION_REF,
                    fullLine = lineText,
                    hasModule = hasModule
                )
            }
        }
    }

    private fun resolveVersionRef(fullText: String, versionRef: String): String? {
        val pattern = Regex("""(?m)^$versionRef\s*=\s*"([^"]+)"""")
        return pattern.find(fullText)?.groupValues?.get(1)
    }

    private fun generateVersionRef(groupId: String, artifactId: String): String {
        val parts = groupId.split(".")
        val lastPart = parts.lastOrNull() ?: groupId

        return when {
            artifactId.startsWith(lastPart) -> artifactId.substringBefore("-").lowercase()
            else -> (parts + artifactId.toCamelCaseByDelimiters().toKebabCase()).joinToString("-")
        }
    }

    private fun getLineText(element: PsiElement): String {
        val file = element.containingFile ?: return ""
        val document = file.viewProvider.document ?: return ""
        val offset = element.textOffset
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        return document.getText(TextRange(lineStart, lineEnd))
    }

    private data class CatalogDependencyInfo(
        val groupId: String,
        val artifactId: String,
        val versionRef: String?,
        val resolvedVersion: String?,
        val versionType: VersionType,
        val fullLine: String,
        val hasModule: Boolean
    )

    private enum class VersionType {
        VERSION_REF,      // 使用 version.ref = "xxx"
        INLINE_VERSION,   // 使用 version = "1.0.0"
        NEEDS_VERSION_REF // 没有版本，需要添加
    }
}

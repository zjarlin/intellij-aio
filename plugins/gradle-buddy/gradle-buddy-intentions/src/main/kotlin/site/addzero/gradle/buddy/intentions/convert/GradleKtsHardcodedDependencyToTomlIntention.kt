package site.addzero.gradle.buddy.intentions.convert

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
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

    override fun getText(): String = "(Gradle Buddy) Convert dependency to version catalog (TOML)"

    override fun startInWriteAction(): Boolean = false

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
        val versionRefFromVar = extractVersionRef(dependencyInfo.version)

        // 如果版本已经是变量引用，直接转换
        if (versionRefFromVar != null) {
            WriteCommandAction.runWriteCommandAction(project) {
                convertToVersionCatalog(file, dependencyInfo, versionRefFromVar, null)
            }
            return
        }

        // 解析现有的版本目录，查找版本号匹配的候选项
        val catalogPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        val basePath = project.basePath ?: return
        val catalogFile = File(basePath, catalogPath)
        val existingContent = if (catalogFile.exists()) {
            parseVersionCatalog(catalogFile.readText())
        } else {
            VersionCatalogContent(versions = mutableMapOf(), libraries = mutableMapOf())
        }

        // 查找版本号完全匹配的已有 version 条目
        val matchingVersionEntries = existingContent.versions.filter { (_, v) -> v == dependencyInfo.version }

        if (matchingVersionEntries.isEmpty()) {
            // 没有匹配的版本条目，直接创建新的
            WriteCommandAction.runWriteCommandAction(project) {
                convertToVersionCatalog(file, dependencyInfo, null, null)
            }
        } else {
            // 有匹配的版本条目，弹出选择对话框
            val newVersionKey = generateVersionKey(dependencyInfo.groupId, dependencyInfo.artifactId)
            val createNewLabel = "✚ 创建新版本变量: $newVersionKey = \"${dependencyInfo.version}\""
            val items = matchingVersionEntries.map { (key, ver) -> "$key = \"$ver\"" } + createNewLabel

            if (editor == null) {
                // 无编辑器时回退到直接创建
                WriteCommandAction.runWriteCommandAction(project) {
                    convertToVersionCatalog(file, dependencyInfo, null, null)
                }
                return
            }

            JBPopupFactory.getInstance()
                .createPopupChooserBuilder(items)
                .setTitle("选择版本引用 (version.ref)")
                .setAdText("检测到 ${matchingVersionEntries.size} 个已有版本定义匹配 \"${dependencyInfo.version}\"")
                .setItemChosenCallback { chosen ->
                    val selectedVersionKey = if (chosen == createNewLabel) {
                        null // 创建新的
                    } else {
                        chosen.substringBefore(" =").trim()
                    }
                    WriteCommandAction.runWriteCommandAction(project) {
                        convertToVersionCatalog(file, dependencyInfo, null, selectedVersionKey)
                    }
                }
                .createPopup()
                .showInBestPositionFor(editor)
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

    /**
     * 转换为版本目录格式并合并到 libs.versions.toml
     *
     * @param versionRefFromVar 如果版本字符串本身就是 libs.versions.xxx 变量引用，则为提取出的 ref 名称
     * @param selectedVersionKey 用户从弹窗中选择的已有版本 key；为 null 表示需要创建新的版本条目
     */
    private fun convertToVersionCatalog(
        file: PsiFile,
        info: DependencyInfo,
        versionRefFromVar: String?,
        selectedVersionKey: String?
    ) {
        val project = file.project
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return

        val catalogPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        val basePath = project.basePath ?: return
        val catalogFile = File(basePath, catalogPath)
        val existingContent = if (catalogFile.exists()) {
            parseVersionCatalog(catalogFile.readText())
        } else {
            VersionCatalogContent(versions = mutableMapOf(), libraries = mutableMapOf())
        }

        val existingLibrary = existingContent.libraries.values.firstOrNull { entry ->
            entry.groupId == info.groupId && entry.artifactId == info.artifactId
        }
        val libraryKey = existingLibrary?.alias ?: generateLibraryKey(info.groupId, info.artifactId)
        val accessorKey = toCatalogAccessor(libraryKey)

        // 确定最终使用的 versionKey：
        // 优先级：已有库的 versionRef > 变量引用 > 用户选择的已有 key > 自动生成新 key
        val versionKey = existingLibrary?.versionRef
            ?: versionRefFromVar
            ?: selectedVersionKey
            ?: generateVersionKey(info.groupId, info.artifactId)

        // 是否需要在 [versions] 中创建新条目
        // 只有当不是变量引用、且不是复用已有 key 时才需要创建
        val needCreateVersion = versionRefFromVar == null && selectedVersionKey == null && existingLibrary?.versionRef == null

        // 生成新的依赖声明
        val dependencyRef = when {
            info.classifier != null && info.extension != null -> {
                "libs.$accessorKey(\"${info.extension}\", \"${info.classifier}\")"
            }
            info.classifier != null -> {
                "libs.$accessorKey(classifier = \"${info.classifier}\")"
            }
            info.extension != null -> {
                "libs.$accessorKey(\"${info.extension}\")"
            }
            else -> {
                "libs.$accessorKey"
            }
        }

        // 生成完整的依赖声明
        val newDeclaration = "${info.configuration}($dependencyRef)"

        // 替换原始声明
        val startOffset = info.element.textOffset
        val endOffset = startOffset + info.element.text.length

        document.replaceString(startOffset, endOffset, newDeclaration)

        // 合并到 libs.versions.toml
        mergeToVersionCatalog(catalogFile, existingContent, info, libraryKey, versionKey, needCreateVersion)
    }

    // 合并到版本目录文件
    private fun mergeToVersionCatalog(
        catalogFile: File,
        existingContent: VersionCatalogContent,
        info: DependencyInfo,
        libraryKey: String,
        versionKey: String,
        needCreateVersion: Boolean
    ) {
        val catalogDir = catalogFile.parentFile
        if (!catalogDir.exists()) {
            catalogDir.mkdirs()
        }

        // 添加新的版本和库
        if (needCreateVersion) {
            existingContent.versions[versionKey] = info.version
        }
        if (!existingContent.libraries.containsKey(libraryKey)) {
            existingContent.libraries[libraryKey] = LibraryEntry(
                alias = libraryKey,
                groupId = info.groupId,
                artifactId = info.artifactId,
                module = "${info.groupId}:${info.artifactId}",
                versionRef = versionKey,
                version = null,
                classifier = info.classifier
            )
        }

        // 写入文件（保留现有内容与其他表）
        if (catalogFile.exists()) {
            updateVersionCatalogInPlace(catalogFile, info, libraryKey, versionKey, needCreateVersion)
        } else {
            writeVersionCatalog(catalogFile, existingContent)
        }

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
                    val moduleMatch = Regex("""module\s*=\s*"([^"]+)"""").find(trimmed)
                    val groupMatch = Regex("""group\s*=\s*"([^"]+)"""").find(trimmed)
                    val nameMatch = Regex("""name\s*=\s*"([^"]+)"""").find(trimmed)
                    val versionRefMatch = Regex("""version\.ref\s*=\s*"([^"]+)"""").find(trimmed)
                    val versionMatch = Regex("""version\s*=\s*"([^"]+)"""").find(trimmed)
                    val classifierMatch = Regex("""classifier\s*=\s*"([^"]+)"""").find(trimmed)

                    if (aliasMatch != null && (moduleMatch != null || (groupMatch != null && nameMatch != null))) {
                        val alias = aliasMatch.groupValues[1]
                        val module = moduleMatch?.groupValues?.get(1)
                        val groupId = groupMatch?.groupValues?.get(1)
                            ?: module?.substringBefore(":")
                        val artifactId = nameMatch?.groupValues?.get(1)
                            ?: module?.substringAfter(":")
                        if (groupId == null || artifactId == null) return@forEach
                        libraries[alias] = LibraryEntry(
                            alias = alias,
                            groupId = groupId,
                            artifactId = artifactId,
                            module = module,
                            versionRef = versionRefMatch?.groupValues?.get(1),
                            version = versionMatch?.groupValues?.get(1),
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
            tomlBuilder.append("$alias = { group = \"${entry.groupId}\", name = \"${entry.artifactId}\"")
            if (!entry.versionRef.isNullOrBlank()) {
                tomlBuilder.append(", version.ref = \"${entry.versionRef}\"")
            } else if (!entry.version.isNullOrBlank()) {
                tomlBuilder.append(", version = \"${entry.version}\"")
            }
            if (entry.classifier != null) {
                tomlBuilder.append(", classifier = \"${entry.classifier}\"")
            }
            tomlBuilder.appendLine(" }")
        }

        file.writeText(tomlBuilder.toString())
    }

    private fun updateVersionCatalogInPlace(
        file: File,
        info: DependencyInfo,
        libraryKey: String,
        versionKey: String,
        needCreateVersion: Boolean
    ) {
        val original = file.readText()
        val lines = original.lines().toMutableList()
        var updatedLines = lines

        if (needCreateVersion) {
            updatedLines = upsertVersionEntry(updatedLines, versionKey, info.version)
        }
        updatedLines = upsertLibraryEntry(
            updatedLines,
            libraryKey,
            info,
            versionKey
        )

        val updated = updatedLines.joinToString("\n")
        if (updated != original) {
            file.writeText(updated)
        }
    }

    private fun upsertVersionEntry(
        lines: MutableList<String>,
        versionKey: String,
        version: String
    ): MutableList<String> {
        val section = findSection(lines, "[versions]")
        val entryRegex = Regex("""^\s*${Regex.escape(versionKey)}\s*=""")
        if (section != null) {
            if (lines.subList(section.start + 1, section.end).any { entryRegex.containsMatchIn(it) }) {
                return lines
            }
            val insertAt = section.end
            lines.add(insertAt, "$versionKey = \"$version\"")
            return lines
        }

        if (lines.isNotEmpty() && lines.last().isNotBlank()) {
            lines.add("")
        }
        lines.add("[versions]")
        lines.add("$versionKey = \"$version\"")
        return lines
    }

    private fun upsertLibraryEntry(
        lines: MutableList<String>,
        libraryKey: String,
        info: DependencyInfo,
        versionKey: String
    ): MutableList<String> {
        val section = findSection(lines, "[libraries]")
        val entryRegex = Regex("""^\s*${Regex.escape(libraryKey)}\s*=""")
        if (section != null) {
            if (lines.subList(section.start + 1, section.end).any { entryRegex.containsMatchIn(it) }) {
                return lines
            }
            val insertAt = section.end
            lines.add(insertAt, buildLibraryLine(libraryKey, info, versionKey))
            return lines
        }

        if (lines.isNotEmpty() && lines.last().isNotBlank()) {
            lines.add("")
        }
        lines.add("[libraries]")
        lines.add(buildLibraryLine(libraryKey, info, versionKey))
        return lines
    }

    /** 始终使用 version.ref 引用 */
    private fun buildLibraryLine(
        libraryKey: String,
        info: DependencyInfo,
        versionKey: String
    ): String {
        val builder = StringBuilder()
        builder.append("$libraryKey = { group = \"${info.groupId}\", name = \"${info.artifactId}\"")
        builder.append(", version.ref = \"$versionKey\"")
        if (info.classifier != null) {
            builder.append(", classifier = \"${info.classifier}\"")
        }
        builder.append(" }")
        return builder.toString()
    }

    private data class SectionRange(val start: Int, val end: Int)

    private fun findSection(lines: List<String>, header: String): SectionRange? {
        var start = -1
        for (i in lines.indices) {
            if (lines[i].trim() == header) {
                start = i
                break
            }
        }
        if (start == -1) return null
        var end = lines.size
        for (i in start + 1 until lines.size) {
            val trimmed = lines[i].trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                end = i
                break
            }
        }
        return SectionRange(start, end)
    }

    // 生成库键名
    private fun generateLibraryKey(groupId: String, artifactId: String): String {
        return artifactId
            .replace(".", "-")
            .replace("_", "-")
            .lowercase()
    }

    private fun toCatalogAccessor(alias: String): String {
        return alias.replace('-', '.').replace('_', '.')
    }

    // 生成版本键名
    private fun generateVersionKey(groupId: String, artifactId: String): String {
        val libraryKey = generateLibraryKey(groupId, artifactId)
        return "${libraryKey}-version"
    }

    private fun extractVersionRef(version: String): String? {
        val trimmed = version.trim()
        val regex = Regex("""\$\{?\s*libs\.versions\.([A-Za-z0-9_.-]+)\.get\(\)\s*}?\s*""")
        val match = regex.matchEntire(trimmed)
        if (match != null) return match.groupValues[1]
        val alt = Regex("""libs\.versions\.([A-Za-z0-9_.-]+)""")
        return alt.matchEntire(trimmed)?.groupValues?.get(1)
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
        val module: String? = null,
        val versionRef: String? = null,
        val version: String? = null,
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

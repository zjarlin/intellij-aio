package site.addzero.gradle.buddy.intentions.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import site.addzero.gradle.buddy.search.cache.SearchResultCacheService
import site.addzero.gradle.buddy.search.history.SearchHistoryService
import site.addzero.gradle.buddy.search.settings.MavenSearchSettings
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import site.addzero.network.call.maven.util.MavenArtifact
import site.addzero.network.call.maven.util.MavenCentralPaginatedSearchUtil
import site.addzero.network.call.maven.util.MavenCentralSearchUtil
import java.io.File

/**
 * Gradle Kotlin Script 依赖补全
 *
 * 支持场景：
 * 1. implementation("xxx   -> 在引号内输入时补全
 * 2. implementation(xxx    -> 括号内无引号时输入补全
 * 3. 裸输入: 在 dependencies {} 块内直接输入关键字 -> 自动包裹 implementation("...")
 * 4. KMP sourceSets: commonMainImplementation, iosMainApi 等
 * 5. 静默 upsert toml 模式：自动写入 toml 并回显 libs.xxx.xxx
 *
 * 优先级：置顶（order="FIRST" + Double.MAX_VALUE priority）
 */
class GradleKtsCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement()
                .inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".gradle.kts"))),
            GradleDependencyCompletionProvider()
        )
    }
}

private class GradleDependencyCompletionProvider : CompletionProvider<CompletionParameters>() {

    companion object {
        private val STANDARD_CONFIGS = setOf(
            "implementation", "api", "compileOnly", "runtimeOnly",
            "testImplementation", "testCompileOnly", "testRuntimeOnly",
            "kapt", "ksp", "annotationProcessor", "classpath"
        )

        private val KMP_CONFIG_SUFFIXES = setOf(
            "Implementation", "Api", "CompileOnly", "RuntimeOnly"
        )

        fun isDependencyMethod(name: String): Boolean {
            if (name in STANDARD_CONFIGS) return true
            return KMP_CONFIG_SUFFIXES.any { name.endsWith(it) }
        }

        /** alias: artifactId kebab-case */
        fun generateLibraryAlias(groupId: String, artifactId: String): String =
            artifactId.replace(".", "-").replace("_", "-").lowercase()

        /** alias -> accessor: jimmer-sql-kotlin -> jimmer.sql.kotlin */
        fun toCatalogAccessor(alias: String): String =
            alias.replace('-', '.').replace('_', '.')

        fun generateVersionKey(groupId: String, artifactId: String): String =
            generateLibraryAlias(groupId, artifactId)
    }

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val document = parameters.editor.document
        val offset = parameters.offset
        val text = document.text
        val project = parameters.editor.project

        val ctx = detectContext(text, offset) ?: return

        val query = ctx.query
        if (query.isBlank()) return

        val prefixMatcher = result.withPrefixMatcher(query)

        val silentUpsert = project?.let {
            GradleBuddySettingsService.getInstance(it).isSilentUpsertToml()
        } ?: false

        // 历史记录（最高优先级）
        val historyService = SearchHistoryService.getInstance()
        if (historyService.enableHistory) {
            val historyArtifacts = when {
                query.length < 2 -> historyService.recentArtifacts(10)
                else -> historyService.matchArtifacts(query, 5)
            }
            historyArtifacts.forEachIndexed { index, entry ->
                prefixMatcher.addElement(
                    createHistoryElement(entry, ctx, project, silentUpsert, priority = 10000.0 - index)
                )
            }
        }

        if (query.length < 2) {
            result.restartCompletionOnAnyPrefixChange()
            return
        }

        // 缓存
        val cacheService = SearchResultCacheService.getInstance()
        val cached = cacheService.match(query, limit = 20)
        if (cached.isNotEmpty()) {
            cached.forEachIndexed { index, artifact ->
                ProgressManager.checkCanceled()
                val resolvedVersion = resolveLatestVersionForDisplay(artifact.groupId, artifact.artifactId, artifact.latestVersion.ifBlank { artifact.version })
                prefixMatcher.addElement(
                    createArtifactElement(artifact, resolvedVersion, ctx, project, silentUpsert, priority = 5000.0 - index, fromCache = true)
                )
            }
            result.restartCompletionOnAnyPrefixChange()
            return
        }

        // Maven Central 搜索
        ProgressManager.checkCanceled()
        runCatching {
            val session = MavenCentralPaginatedSearchUtil.searchByKeywordPaginated(
                keyword = query,
                pageSize = MavenSearchSettings.getInstance().pageSize.coerceIn(5, 30)
            )
            val artifacts = session.loadNextPage().artifacts
            if (artifacts.isNotEmpty()) cacheService.addAll(artifacts)

            artifacts.forEachIndexed { index, artifact ->
                ProgressManager.checkCanceled()
                val resolvedVersion = resolveLatestVersionForDisplay(artifact.groupId, artifact.artifactId, artifact.latestVersion.ifBlank { artifact.version })
                prefixMatcher.addElement(
                    createArtifactElement(artifact, resolvedVersion, ctx, project, silentUpsert, priority = 1000.0 - index, fromCache = false)
                )
            }
        }
        result.restartCompletionOnAnyPrefixChange()
    }

    /**
     * 检测依赖上下文，支持三种模式：
     * 1. method("xxx  -> 引号内
     * 2. method(xxx   -> 括号内无引号
     * 3. 裸输入: dependencies { } 块内直接输入关键字（不在任何 method() 内）
     */
    private fun detectContext(text: String, offset: Int): KtsDependencyContext? {
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val lineText = text.substring(lineStart, offset)

        // 模式1: method("xxx 或 method("xxx"
        val quotedPattern = Regex("""(\w+)\s*\(\s*"([^"]*)(")?$""")
        quotedPattern.find(lineText)?.let { match ->
            val method = match.groupValues[1]
            if (!isDependencyMethod(method)) return@let
            val query = match.groupValues[2]
            val hasCloseQuote = match.groupValues[3].isNotEmpty()
            return KtsDependencyContext(
                methodName = method,
                mode = KtsInputMode.QUOTED,
                hasOpenQuote = true,
                hasCloseQuote = hasCloseQuote,
                query = query,
                queryStartOffset = offset - query.length - (if (hasCloseQuote) 1 else 0)
            )
        }

        // 模式2: method(xxx （无引号）
        val unquotedPattern = Regex("""(\w+)\s*\(\s*([^"()\s]*)$""")
        unquotedPattern.find(lineText)?.let { match ->
            val method = match.groupValues[1]
            if (!isDependencyMethod(method)) return@let
            val query = match.groupValues[2]
            return KtsDependencyContext(
                methodName = method,
                mode = KtsInputMode.UNQUOTED,
                hasOpenQuote = false,
                hasCloseQuote = false,
                query = query,
                queryStartOffset = offset - query.length
            )
        }

        // 模式3: 裸输入 — 在 dependencies { } 块内直接输入关键字
        // 判断是否在 dependencies 块内：向上扫描找 dependencies {
        if (isInsideDependenciesBlock(text, offset)) {
            // 当前行只有空白+关键字（不在任何函数调用内）
            val barePattern = Regex("""^\s*([\w.:-]+)$""")
            barePattern.find(lineText)?.let { match ->
                val query = match.groupValues[1]
                if (query.isBlank()) return@let
                // 排除 Kotlin 关键字和 Gradle DSL 关键字
                if (query in GRADLE_DSL_KEYWORDS) return@let
                return KtsDependencyContext(
                    methodName = null,
                    mode = KtsInputMode.BARE,
                    hasOpenQuote = false,
                    hasCloseQuote = false,
                    query = query,
                    queryStartOffset = lineStart + (lineText.length - lineText.trimStart().length)
                )
            }
        }

        return null
    }

    /** 简单判断 offset 是否在 dependencies { } 块内 */
    private fun isInsideDependenciesBlock(text: String, offset: Int): Boolean {
        val before = text.substring(0, offset)
        // 找最近的 dependencies { 或 dependencies{
        val depBlockStart = before.lastIndexOf("dependencies")
        if (depBlockStart < 0) return false
        // 确认后面跟着 {
        val afterDep = before.substring(depBlockStart + "dependencies".length).trimStart()
        if (!afterDep.startsWith("{")) return false
        // 计算大括号平衡
        val braceStart = before.indexOf('{', depBlockStart)
        if (braceStart < 0) return false
        var depth = 0
        for (i in braceStart until offset) {
            when (text[i]) {
                '{' -> depth++
                '}' -> depth--
            }
        }
        return depth > 0
    }

    private fun createHistoryElement(
        entry: site.addzero.gradle.buddy.search.history.ArtifactHistoryEntry,
        ctx: KtsDependencyContext,
        project: Project?,
        silentUpsert: Boolean,
        priority: Double
    ): LookupElement {
        val entryArtifactId = entry.artifactId
        val entryGroupId = entry.groupId
        val entryVersion = entry.version
        return PrioritizedLookupElement.withPriority(
            LookupElementBuilder.create(entryArtifactId)
                .withPresentableText(entryArtifactId)
                .withTailText("  $entryGroupId:$entryVersion", true)
                .withTypeText(if (silentUpsert) "→ toml [recent]" else "[recent]", true)
                .withIcon(AllIcons.Nodes.Favorite)
                .withBoldness(true)
                .withInsertHandler(createInsertHandler(ctx, entryGroupId, entryArtifactId, entryVersion, project, silentUpsert)),
            priority
        )
    }

    private fun createArtifactElement(
        artifact: MavenArtifact,
        resolvedVersion: String,
        ctx: KtsDependencyContext,
        project: Project?,
        silentUpsert: Boolean,
        priority: Double,
        fromCache: Boolean
    ): LookupElement {
        val lookupStr = artifact.artifactId
        val cacheIndicator = if (fromCache) "💾 " else ""

        return PrioritizedLookupElement.withPriority(
            LookupElementBuilder.create(lookupStr)
                .withPresentableText(artifact.artifactId)
                .withTailText("  ${artifact.groupId}:$resolvedVersion", true)
                .withTypeText("$cacheIndicator${if (silentUpsert) "→ toml" else "Maven"}", true)
                .withIcon(AllIcons.Nodes.PpLib)
                .withInsertHandler(createInsertHandler(ctx, artifact.groupId, artifact.artifactId, resolvedVersion, project, silentUpsert)),
            priority
        )
    }

    /**
     * 在补全列表构建阶段同步获取最新版本，用于显示。
     * addCompletions 本身在后台线程执行，所以同步网络调用可以接受。
     */
    private fun resolveLatestVersionForDisplay(groupId: String, artifactId: String, searchVersion: String): String {
        return resolveLatestVersion(groupId, artifactId, searchVersion)
    }

    /**
     * 解析真正的最新版本号。
     * 调用 MavenCentralSearchUtil.getLatestVersion()，保证返回版本 >= searchVersion（不降级）。
     */
    private fun resolveLatestVersion(groupId: String, artifactId: String, searchVersion: String): String {
        val resolved = runCatching {
            MavenCentralSearchUtil.getLatestVersion(groupId, artifactId)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return searchVersion
        return if (compareVersions(resolved, searchVersion) >= 0) resolved else searchVersion
    }

    /** 简单的版本比较：按 . 和 - 分段逐段比较数字，保证不降级 */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(Regex("[.\\-]"))
        val parts2 = v2.split(Regex("[.\\-]"))
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrNull(i) ?: "0"
            val p2 = parts2.getOrNull(i) ?: "0"
            val n1 = p1.toLongOrNull()
            val n2 = p2.toLongOrNull()
            val cmp = if (n1 != null && n2 != null) n1.compareTo(n2) else p1.compareTo(p2)
            if (cmp != 0) return cmp
        }
        return 0
    }

    private fun createInsertHandler(
        ctx: KtsDependencyContext,
        groupId: String,
        artifactId: String,
        version: String,
        project: Project?,
        silentUpsert: Boolean
    ): InsertHandler<LookupElement> = InsertHandler { insertCtx, _ ->
        val document = insertCtx.document
        val editor = insertCtx.editor
        val startOffset = ctx.queryStartOffset
        val endOffset = insertCtx.tailOffset

        val afterText = document.text.substring(endOffset, minOf(endOffset + 10, document.textLength))

        if (silentUpsert && project != null) {
            // === 静默 upsert toml 模式 ===
            val alias = generateLibraryAlias(groupId, artifactId)
            val accessor = toCatalogAccessor(alias)

            val insertText = when (ctx.mode) {
                KtsInputMode.BARE -> "implementation(libs.$accessor)"
                KtsInputMode.QUOTED -> "libs.$accessor"
                KtsInputMode.UNQUOTED -> "libs.$accessor"
            }

            when (ctx.mode) {
                KtsInputMode.BARE -> {
                    document.replaceString(startOffset, endOffset, insertText)
                    editor.caretModel.moveToOffset(startOffset + insertText.length)
                }
                KtsInputMode.QUOTED -> {
                    val replaceStart = startOffset - 1
                    var replaceEnd = endOffset
                    if (afterText.startsWith("\")")) replaceEnd += 2
                    else if (afterText.startsWith("\"")) replaceEnd += 1
                    val fullReplace = insertText + (if (!afterText.startsWith("\")") && !afterText.startsWith("\"")) ")" else "")
                    document.replaceString(replaceStart, replaceEnd, fullReplace)
                    editor.caretModel.moveToOffset(replaceStart + fullReplace.length)
                }
                KtsInputMode.UNQUOTED -> {
                    var replaceEnd = endOffset
                    if (afterText.startsWith(")")) replaceEnd += 1
                    val fullReplace = insertText + (if (!afterText.startsWith(")")) ")" else "")
                    document.replaceString(startOffset, replaceEnd, fullReplace)
                    editor.caretModel.moveToOffset(startOffset + fullReplace.length)
                }
            }
            insertCtx.commitDocument()

            // 后台写入 toml（版本已经是最新的了）
            ApplicationManager.getApplication().executeOnPooledThread {
                ApplicationManager.getApplication().invokeLater {
                    WriteCommandAction.runWriteCommandAction(project) {
                        upsertToVersionCatalog(project, groupId, artifactId, version, alias)
                    }
                }
                SearchHistoryService.getInstance().record(groupId, artifactId, version)
            }
        } else {
            // === 普通模式：版本已在补全阶段解析为最新，直接插入 ===
            val coordinate = "$groupId:$artifactId:$version"

            val insertText = when (ctx.mode) {
                KtsInputMode.BARE -> "implementation(\"$coordinate\")"
                KtsInputMode.QUOTED -> {
                    val hasTrailingQuote = afterText.startsWith("\"")
                    buildString {
                        append(coordinate)
                        if (!ctx.hasCloseQuote && !hasTrailingQuote) append("\"")
                    }
                }
                KtsInputMode.UNQUOTED -> {
                    val hasTrailingParen = afterText.startsWith(")")
                    buildString {
                        append("\"$coordinate\"")
                        if (!hasTrailingParen) append(")")
                    }
                }
            }

            document.replaceString(startOffset, endOffset, insertText)
            editor.caretModel.moveToOffset(startOffset + insertText.length)
            insertCtx.commitDocument()

            // 记录历史
            ApplicationManager.getApplication().executeOnPooledThread {
                SearchHistoryService.getInstance().record(groupId, artifactId, version)
            }
        }
    }

    /** 静默写入 libs.versions.toml */
    private fun upsertToVersionCatalog(
        project: Project,
        groupId: String,
        artifactId: String,
        version: String,
        alias: String
    ) {
        val catalogFile = GradleBuddySettingsService.getInstance(project).resolveVersionCatalogFile(project)

        val versionKey = generateVersionKey(groupId, artifactId)
        val libraryLine = "$alias = { module = \"$groupId:$artifactId\", version.ref = \"$versionKey\" }"

        if (!catalogFile.exists()) {
            catalogFile.parentFile?.mkdirs()
            catalogFile.writeText("[versions]\n$versionKey = \"$version\"\n\n[libraries]\n$libraryLine\n")
        } else {
            val lines = catalogFile.readText().lines().toMutableList()

            val aliasRegex = Regex("""^\s*${Regex.escape(alias)}\s*=""")
            val aliasIndex = lines.indexOfFirst { aliasRegex.containsMatchIn(it) }
            if (aliasIndex >= 0) {
                lines[aliasIndex] = libraryLine
            } else {
                lines.addAll(upsertSection(lines, "[libraries]", libraryLine))
            }

            val versionRegex = Regex("""^\s*${Regex.escape(versionKey)}\s*=""")
            val versionIndex = lines.indexOfFirst { versionRegex.containsMatchIn(it) }
            if (versionIndex >= 0) {
                lines[versionIndex] = "$versionKey = \"$version\""
            } else {
                lines.addAll(upsertSection(lines, "[versions]", "$versionKey = \"$version\""))
            }

            catalogFile.writeText(lines.joinToString("\n"))
        }

        LocalFileSystem.getInstance().refreshAndFindFileByPath(catalogFile.absolutePath)
    }

    /**
     * 在指定 section 末尾插入一行，返回空列表（直接修改 lines）
     */
    private fun upsertSection(lines: MutableList<String>, header: String, entry: String): List<String> {
        var sectionStart = -1
        for (i in lines.indices) {
            if (lines[i].trim() == header) {
                sectionStart = i
                break
            }
        }

        if (sectionStart >= 0) {
            // 找 section 结束位置
            var insertAt = lines.size
            for (i in sectionStart + 1 until lines.size) {
                val trimmed = lines[i].trim()
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    insertAt = i
                    break
                }
            }
            lines.add(insertAt, entry)
        } else {
            // section 不存在，追加
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
            lines.add(header)
            lines.add(entry)
        }
        return emptyList()
    }
}

/** 输入模式 */
private enum class KtsInputMode {
    QUOTED,     // implementation("xxx
    UNQUOTED,   // implementation(xxx
    BARE        // 裸输入：在 dependencies {} 内直接输入关键字
}

private data class KtsDependencyContext(
    val methodName: String?,       // null = 裸输入
    val mode: KtsInputMode,
    val hasOpenQuote: Boolean,
    val hasCloseQuote: Boolean,
    val query: String,
    val queryStartOffset: Int
)

/** Gradle DSL 关键字，裸输入模式下排除 */
private val GRADLE_DSL_KEYWORDS = setOf(
    "dependencies", "plugins", "repositories", "allprojects", "subprojects",
    "buildscript", "configurations", "sourceSets", "tasks", "apply",
    "val", "var", "fun", "if", "else", "for", "when", "return",
    "true", "false", "null", "this", "super", "is", "as", "in",
    "project", "gradle", "ext", "extra", "the", "by",
    "implementation", "api", "compileOnly", "runtimeOnly",
    "testImplementation", "testCompileOnly", "testRuntimeOnly",
    "kapt", "ksp", "annotationProcessor", "classpath"
)

package site.addzero.gradle.buddy.intentions.catalog

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import site.addzero.gradle.buddy.intentions.convert.HardcodedDependencyCatalogSupport
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import java.io.File

/**
 * 在 `libs.findLibrary("alias").get()` 上提供一个意图：
 * 如果 [libraries] 中缺少该 alias，则补一条使用专属 version.ref 的 skeleton；
 * 如果已存在，则补齐专属 [versions] 条目并跳转过去。
 */
class GradleKtsFindLibraryCreateCatalogEntryIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.find.library.create.catalog.entry")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            GradleBuddyBundle.message("intention.find.library.create.catalog.entry.preview")
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null || !file.name.endsWith(".gradle.kts")) {
            return false
        }
        return findTarget(file, editor.caretModel.offset) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (editor == null) {
            return
        }

        val target = findTarget(file, editor.caretModel.offset) ?: return
        val catalogFile = GradleBuddySettingsService.getInstance(project).resolveVersionCatalogFile(project)

        var navigation: NavigationTarget? = null
        WriteCommandAction.writeCommandAction(project)
            .withName(GradleBuddyBundle.message("intention.find.library.create.catalog.entry.command"))
            .run<Throwable> {
                navigation = ensureCatalogEntry(project, catalogFile, target.alias)
            }

        val resolvedNavigation = navigation ?: return
        OpenFileDescriptor(project, resolvedNavigation.virtualFile, resolvedNavigation.offset).navigate(true)
    }

    private fun ensureCatalogEntry(project: Project, catalogFile: File, alias: String): NavigationTarget {
        catalogFile.parentFile?.mkdirs()
        if (!catalogFile.exists()) {
            catalogFile.writeText("")
        }

        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(catalogFile)
            ?: error("Cannot refresh version catalog file: ${catalogFile.absolutePath}")
        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
        val currentText = document?.text ?: catalogFile.readText()
        val existingEntries = parseLibraryEntries(currentText)
        val existingVersionEntries = parseVersionEntries(currentText)
        val dedicatedVersionKey = HardcodedDependencyCatalogSupport.generateVersionKey(alias)

        val existing = existingEntries.firstOrNull { it.alias == alias }
        val coordinates = existing?.let { Coordinates(it.groupId, it.artifactId) } ?: guessCoordinates(alias, existingEntries)
        val versionValue = existingVersionEntries[dedicatedVersionKey]?.value
            ?: existing?.directVersion
            ?: existing?.versionRef?.let(existingVersionEntries::get)?.value
            ?: "+"
        val updatedText = upsertVersionEntry(
            originalText = upsertLibraryEntry(
                originalText = currentText,
                alias = alias,
                groupId = coordinates.groupId,
                artifactId = coordinates.artifactId,
                versionKey = dedicatedVersionKey
            ),
            versionKey = dedicatedVersionKey,
            versionValue = versionValue
        )

        if (document != null) {
            document.replaceString(0, document.textLength, updatedText)
            PsiDocumentManager.getInstance(project).commitDocument(document)
            FileDocumentManager.getInstance().saveDocument(document)
        } else {
            catalogFile.writeText(updatedText)
        }

        val refreshedVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(catalogFile) ?: virtualFile
        return NavigationTarget(
            virtualFile = refreshedVirtualFile,
            offset = findVersionValueOffset(updatedText, dedicatedVersionKey)
        )
    }

    private fun findTarget(file: PsiFile, offset: Int): TargetCall? {
        val offsets = buildList {
            if (offset in 0 until file.textLength) add(offset)
            if (offset > 0 && offset - 1 in 0 until file.textLength) add(offset - 1)
        }.distinct()

        for (candidateOffset in offsets) {
            val element = file.findElementAt(candidateOffset) ?: continue
            findTarget(element)?.let { return it }
        }
        return null
    }

    private fun findTarget(element: PsiElement): TargetCall? {
        val stringExpression = PsiTreeUtil.getParentOfType(element, KtStringTemplateExpression::class.java, false)
            ?: return null
        return resolveTarget(stringExpression)
    }

    private fun resolveTarget(stringExpression: KtStringTemplateExpression): TargetCall? {
        val alias = extractLiteralString(stringExpression) ?: return null
        if (alias.isBlank()) {
            return null
        }

        val callExpression = PsiTreeUtil.getParentOfType(stringExpression, KtCallExpression::class.java) ?: return null
        if (callExpression.valueArguments.singleOrNull()?.getArgumentExpression() != stringExpression) {
            return null
        }
        if (callExpression.calleeExpression?.text != "findLibrary") {
            return null
        }

        val receiver = (callExpression.parent as? KtDotQualifiedExpression)?.receiverExpression?.text?.trim()
        if (receiver.isNullOrBlank()) {
            return null
        }

        return TargetCall(alias)
    }

    private fun extractLiteralString(expression: KtStringTemplateExpression): String? {
        if (expression.entries.any { it !is KtLiteralStringTemplateEntry }) {
            return null
        }
        return expression.entries.joinToString(separator = "") { it.text }
    }

    private fun parseLibraryEntries(content: String): List<CatalogLibraryEntry> {
        val result = mutableListOf<CatalogLibraryEntry>()
        val lines = content.split('\n')
        var offset = 0
        var inLibraries = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inLibraries = trimmed == "[libraries]"
                offset += line.length + 1
                continue
            }

            if (inLibraries && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                parseLibraryEntryLine(line, offset)?.let(result::add)
            }
            offset += line.length + 1
        }

        return result
    }

    private fun parseLibraryEntryLine(line: String, lineStartOffset: Int): CatalogLibraryEntry? {
        val aliasMatch = Regex("""^\s*["']?([A-Za-z0-9_.-]+)["']?\s*=""").find(line) ?: return null
        val alias = aliasMatch.groupValues[1]
        val moduleMatch = Regex("module\\s*=\\s*\"([^\"]+):([^\"]+)\"").find(line)
        val groupMatch = Regex("group\\s*=\\s*\"([^\"]+)\"").find(line)
        val nameMatch = Regex("name\\s*=\\s*\"([^\"]+)\"").find(line)
        val versionRefMatch = Regex("version\\.ref\\s*=\\s*\"([^\"]+)\"").find(line)
        val versionMatch = Regex("(?<!\\.)version\\s*=\\s*\"([^\"]+)\"").find(line)
        val groupId = groupMatch?.groupValues?.get(1) ?: moduleMatch?.groupValues?.get(1) ?: return null
        val artifactId = nameMatch?.groupValues?.get(1) ?: moduleMatch?.groupValues?.get(2) ?: return null

        return CatalogLibraryEntry(
            alias = alias,
            groupId = groupId,
            artifactId = artifactId,
            versionRef = versionRefMatch?.groupValues?.get(1),
            directVersion = versionMatch?.groupValues?.get(1),
            lineText = line,
            lineStartOffset = lineStartOffset
        )
    }

    private fun parseVersionEntries(content: String): Map<String, CatalogVersionEntry> {
        val result = linkedMapOf<String, CatalogVersionEntry>()
        val lines = content.split('\n')
        var offset = 0
        var inVersions = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inVersions = trimmed == "[versions]"
                offset += line.length + 1
                continue
            }

            if (inVersions && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val match = Regex("^\\s*[\"']?([A-Za-z0-9_.-]+)[\"']?\\s*=\\s*\"([^\"]*)\"").find(line)
                if (match != null) {
                    val key = match.groupValues[1]
                    val value = match.groupValues[2]
                    result[key] = CatalogVersionEntry(
                        key = key,
                        value = value,
                        lineText = line,
                        lineStartOffset = offset
                    )
                }
            }
            offset += line.length + 1
        }

        return result
    }

    private fun guessCoordinates(alias: String, existingEntries: List<CatalogLibraryEntry>): Coordinates {
        inferFromExistingGroups(alias, existingEntries)?.let { return it }

        if (alias.startsWith("site-addzero-")) {
            return Coordinates(
                groupId = "site.addzero",
                artifactId = alias.removePrefix("site-addzero-")
            )
        }

        val tokens = alias.split('-').filter { it.isNotBlank() }
        if (tokens.size >= 3 && tokens.first() in DOMAIN_PREFIXES) {
            return Coordinates(
                groupId = tokens.take(2).joinToString("."),
                artifactId = tokens.drop(2).joinToString("-")
            )
        }
        if (tokens.size >= 2) {
            return Coordinates(
                groupId = tokens.first(),
                artifactId = tokens.drop(1).joinToString("-")
            )
        }
        return Coordinates(
            groupId = alias.replace('-', '.'),
            artifactId = alias
        )
    }

    private fun inferFromExistingGroups(alias: String, existingEntries: List<CatalogLibraryEntry>): Coordinates? {
        return existingEntries
            .mapNotNull { entry ->
                val groupPrefix = toKebabCase(entry.groupId)
                if (groupPrefix.isBlank() || !alias.startsWith("$groupPrefix-")) {
                    return@mapNotNull null
                }
                val artifactId = alias.removePrefix("$groupPrefix-")
                if (artifactId.isBlank()) {
                    return@mapNotNull null
                }
                InferredCoordinates(
                    groupId = entry.groupId,
                    artifactId = artifactId,
                    score = groupPrefix.length
                )
            }
            .maxByOrNull { it.score }
            ?.let { Coordinates(it.groupId, it.artifactId) }
    }

    private fun toKebabCase(value: String): String {
        return value
            .replace('.', '-')
            .replace('_', '-')
            .lowercase()
    }

    private fun upsertLibraryEntry(
        originalText: String,
        alias: String,
        groupId: String,
        artifactId: String,
        versionKey: String
    ): String {
        val lines = if (originalText.isEmpty()) mutableListOf() else originalText.split('\n').toMutableList()
        val entryLine = """$alias = { group = "$groupId", name = "$artifactId", version.ref = "$versionKey" }"""
        val section = findSection(lines, "[libraries]")

        if (section != null) {
            val entryRegex = Regex("""^\s*${Regex.escape(alias)}\s*=""")
            val existingIndex = (section.start + 1 until section.end).firstOrNull { index ->
                entryRegex.containsMatchIn(lines[index])
            }
            if (existingIndex != null) {
                lines[existingIndex] = entryLine
                return lines.joinToString("\n")
            }
            val insertAt = trimTrailingBlanks(lines, section.start, section.end)
            lines.add(insertAt, entryLine)
            return lines.joinToString("\n")
        }

        if (lines.isNotEmpty() && lines.last().isNotBlank()) {
            lines.add("")
        }
        lines.add("[libraries]")
        lines.add(entryLine)
        return lines.joinToString("\n")
    }

    private fun upsertVersionEntry(
        originalText: String,
        versionKey: String,
        versionValue: String
    ): String {
        val lines = if (originalText.isEmpty()) mutableListOf() else originalText.split('\n').toMutableList()
        val entryLine = "$versionKey = \"$versionValue\""
        val section = findSection(lines, "[versions]")

        if (section != null) {
            val entryRegex = Regex("""^\s*${Regex.escape(versionKey)}\s*=""")
            val existingIndex = (section.start + 1 until section.end).firstOrNull { index ->
                entryRegex.containsMatchIn(lines[index])
            }
            if (existingIndex != null) {
                return lines.joinToString("\n")
            }
            val insertAt = trimTrailingBlanks(lines, section.start, section.end)
            lines.add(insertAt, entryLine)
            return lines.joinToString("\n")
        }

        if (lines.isNotEmpty() && lines.last().isNotBlank()) {
            lines.add("")
        }
        lines.add("[versions]")
        lines.add(entryLine)
        return lines.joinToString("\n")
    }

    private fun findSection(lines: List<String>, header: String): SectionRange? {
        val start = lines.indexOfFirst { it.trim() == header }
        if (start < 0) {
            return null
        }
        var end = lines.size
        for (index in start + 1 until lines.size) {
            val trimmed = lines[index].trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                end = index
                break
            }
        }
        return SectionRange(start, end)
    }

    private fun trimTrailingBlanks(lines: List<String>, sectionStart: Int, sectionEnd: Int): Int {
        var insertAt = sectionEnd
        while (insertAt > sectionStart + 1 && lines[insertAt - 1].isBlank()) {
            insertAt--
        }
        return insertAt
    }

    private fun findVersionValueOffset(text: String, versionKey: String): Int {
        val match = Regex("""(?m)^\s*${Regex.escape(versionKey)}\s*=.*$""").find(text) ?: return 0
        val lineText = match.value
        val versionMatch = Regex("=\\s*\"").find(lineText)
        return if (versionMatch != null) {
            match.range.first + versionMatch.range.last + 1
        } else {
            match.range.first
        }
    }

    private data class TargetCall(
        val alias: String
    )

    private data class Coordinates(
        val groupId: String,
        val artifactId: String
    )

    private data class InferredCoordinates(
        val groupId: String,
        val artifactId: String,
        val score: Int
    )

    private data class CatalogLibraryEntry(
        val alias: String,
        val groupId: String,
        val artifactId: String,
        val versionRef: String?,
        val directVersion: String?,
        val lineText: String,
        val lineStartOffset: Int
    ) {
        fun preferredOffset(): Int {
            val versionMatch = Regex("version\\s*=\\s*\"").find(lineText)
            return if (versionMatch != null) {
                lineStartOffset + versionMatch.range.last + 1
            } else {
                lineStartOffset + lineText.indexOf(alias).coerceAtLeast(0)
            }
        }
    }

    private data class CatalogVersionEntry(
        val key: String,
        val value: String,
        val lineText: String,
        val lineStartOffset: Int
    )

    private data class SectionRange(
        val start: Int,
        val end: Int
    )

    private data class NavigationTarget(
        val virtualFile: com.intellij.openapi.vfs.VirtualFile,
        val offset: Int
    )

    private companion object {
        val DOMAIN_PREFIXES = setOf("com", "org", "io", "net", "dev", "app", "site", "cn", "top", "me")
    }
}

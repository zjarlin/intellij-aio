package site.addzero.gradle.buddy.intentions.convert

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import java.io.File

/**
 * 将选中的多行 catalog 依赖合并为 [bundles] 条目
 *
 * 使用场景：选中多行 implementation(libs.xxx) 后 Alt+Enter
 * 效果：
 *   1. 在 libs.versions.toml 的 [bundles] 中创建 bundle
 *   2. 将选中的多行替换为单行 implementation(libs.bundles.xxx)
 */
class CreateBundleFromDependenciesIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH
    override fun getFamilyName(): String = "Gradle Buddy"
    override fun getText(): String = "(Gradle Buddy) Create bundle from selected dependencies"
    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("将选中的版本目录依赖合并为 [bundles] 条目。")
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null || !file.name.endsWith(".gradle.kts")) return false
        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) return false
        val refs = extractCatalogRefs(editor)
        return refs.size >= 2
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (editor == null) return
        val refs = extractCatalogRefs(editor)
        if (refs.size < 2) return

        // 推断 bundle 名称：取所有 alias 的最长公共前缀（按 - 分段）
        val suggestedName = suggestBundleName(refs.map { it.alias })

        val bundleName = Messages.showInputDialog(
            project,
            "Bundle 包含 ${refs.size} 个库:\n${refs.joinToString("\n") { "  • ${it.alias}" }}",
            "创建 Bundle",
            null,
            suggestedName,
            null
        ) ?: return // 用户取消

        if (bundleName.isBlank()) return

        // 确定 configuration（取第一个的，通常都一样）
        val configuration = refs.first().configuration

        WriteCommandAction.runWriteCommandAction(project, "Create Bundle: $bundleName", null, {
            val document = editor.document

            // 1. 写入 TOML [bundles]
            val catalogPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
            val basePath = project.basePath ?: return@runWriteCommandAction
            val catalogFile = File(basePath, catalogPath)
            if (catalogFile.exists()) {
                upsertBundleEntry(catalogFile, bundleName, refs.map { it.alias })
                LocalFileSystem.getInstance().refreshAndFindFileByPath(catalogFile.absolutePath)
            }

            // 2. 替换选中区域为单行 bundle 引用
            val bundleAccessor = bundleName.replace('-', '.').replace('_', '.')
            val replacement = "$configuration(libs.bundles.$bundleAccessor)"

            val selModel = editor.selectionModel
            val start = selModel.selectionStart
            val end = selModel.selectionEnd

            // 扩展到完整行
            val lineStart = document.getLineStartOffset(document.getLineNumber(start))
            val lineEnd = document.getLineEndOffset(document.getLineNumber(end - 1))

            document.replaceString(lineStart, lineEnd, replacement)
            editor.caretModel.moveToOffset(lineStart + replacement.length)
            selModel.removeSelection()
        })
    }

    // ── 解析选中文本中的 catalog 引用 ──────────────────────────────

    private data class CatalogRef(
        /** 原始 configuration，如 implementation / api */
        val configuration: String,
        /** TOML alias，如 filekit-core（从 libs.filekit.core 反推） */
        val alias: String
    )

    /**
     * 从编辑器选区中提取所有 catalog 依赖引用。
     *
     * 支持格式：
     *   implementation(libs.xxx.yyy)
     *   api(libs.xxx.yyy)
     *   testImplementation(libs.xxx)
     */
    private fun extractCatalogRefs(editor: Editor): List<CatalogRef> {
        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) return emptyList()
        val selectedText = selectionModel.selectedText ?: return emptyList()

        val pattern = Regex("""(\w+)\s*\(\s*libs\.([A-Za-z0-9_.]+)\s*\)""")
        return pattern.findAll(selectedText).map { match ->
            val config = match.groupValues[1]
            val accessor = match.groupValues[2]
            // accessor: filekit.core -> alias: filekit-core
            val alias = accessor.replace('.', '-')
            CatalogRef(config, alias)
        }.toList()
    }

    // ── Bundle 名称推断 ─────────────────────────────────────────

    /**
     * 取所有 alias 按 `-` 分段的最长公共前缀作为 bundle 名称。
     * 例如 [filekit-core, filekit-dialogs, filekit-dialogs-compose, filekit-coil] -> "filekit"
     */
    private fun suggestBundleName(aliases: List<String>): String {
        if (aliases.isEmpty()) return "my-bundle"
        val segmented = aliases.map { it.split("-") }
        val minLen = segmented.minOf { it.size }
        val common = mutableListOf<String>()
        for (i in 0 until minLen) {
            val seg = segmented[0][i]
            if (segmented.all { it[i] == seg }) common.add(seg) else break
        }
        return if (common.isNotEmpty()) common.joinToString("-") else "my-bundle"
    }

    // ── TOML [bundles] 写入 ─────────────────────────────────────

    /**
     * 在 TOML 文件的 [bundles] section 中追加一条 bundle 定义。
     * 如果 [bundles] section 不存在则创建。
     * 如果同名 bundle 已存在则跳过。
     */
    private fun upsertBundleEntry(catalogFile: File, bundleName: String, aliases: List<String>) {
        val lines = catalogFile.readText().lines().toMutableList()
        val entryRegex = Regex("""^\s*${Regex.escape(bundleName)}\s*=""")

        // 查找 [bundles] section
        var sectionStart = -1
        var sectionEnd = lines.size
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed == "[bundles]") {
                sectionStart = i
                for (j in i + 1 until lines.size) {
                    val t = lines[j].trim()
                    if (t.startsWith("[") && t.endsWith("]")) {
                        sectionEnd = j
                        break
                    }
                }
                break
            }
        }

        if (sectionStart >= 0) {
            // 检查是否已存在
            if (lines.subList(sectionStart + 1, sectionEnd).any { entryRegex.containsMatchIn(it) }) return
            // 在 section 末尾插入
            val bundleLine = buildBundleLine(bundleName, aliases)
            lines.add(sectionEnd, bundleLine)
        } else {
            // [bundles] 不存在，追加到文件末尾
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
            lines.add("[bundles]")
            lines.add(buildBundleLine(bundleName, aliases))
        }

        catalogFile.writeText(lines.joinToString("\n"))
    }

    private fun buildBundleLine(bundleName: String, aliases: List<String>): String {
        val quoted = aliases.joinToString(", ") { "\"$it\"" }
        return "$bundleName = [$quoted]"
    }
}

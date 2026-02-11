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
 *   1. 在 libs.versions.toml 的 [bundles] 中创建（或合并到已有的）bundle
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
        if (!editor.selectionModel.hasSelection()) return false
        return extractCatalogRefs(editor).size >= 2
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (editor == null) return
        val refs = extractCatalogRefs(editor)
        if (refs.size < 2) return

        val suggestedName = suggestBundleName(refs.map { it.alias })

        val bundleName = Messages.showInputDialog(
            project,
            "Bundle 包含 ${refs.size} 个库:\n${refs.joinToString("\n") { "  • ${it.alias}" }}",
            "创建 Bundle",
            null,
            suggestedName,
            null
        ) ?: return

        if (bundleName.isBlank()) return

        val configuration = refs.first().configuration

        WriteCommandAction.runWriteCommandAction(project, "Create Bundle: $bundleName", null, {
            val document = editor.document
            val catalogFile = GradleBuddySettingsService.getInstance(project).resolveVersionCatalogFile(project)
            if (catalogFile.exists()) {
                upsertBundleEntry(catalogFile, bundleName, refs.map { it.alias })
                LocalFileSystem.getInstance().refreshAndFindFileByPath(catalogFile.absolutePath)
            }

            val bundleAccessor = bundleName.replace('-', '.').replace('_', '.')
            val replacement = "$configuration(libs.bundles.$bundleAccessor)"

            val selModel = editor.selectionModel
            val lineStart = document.getLineStartOffset(document.getLineNumber(selModel.selectionStart))
            val lineEnd = document.getLineEndOffset(document.getLineNumber(selModel.selectionEnd - 1))

            document.replaceString(lineStart, lineEnd, replacement)
            editor.caretModel.moveToOffset(lineStart + replacement.length)
            selModel.removeSelection()
        })
    }

    // ── 解析选中文本中的 catalog 引用 ──────────────────────────────

    internal data class CatalogRef(
        val configuration: String,
        /** TOML alias，如 filekit-core（从 libs.filekit.core 反推） */
        val alias: String
    )

    internal companion object {
        /** 从编辑器选区中提取所有 catalog 依赖引用 */
        fun extractCatalogRefs(editor: Editor): List<CatalogRef> {
            val selectedText = editor.selectionModel.selectedText ?: return emptyList()
            val pattern = Regex("""(\w+)\s*\(\s*libs\.([A-Za-z0-9_.]+)\s*\)""")
            return pattern.findAll(selectedText).map { match ->
                CatalogRef(match.groupValues[1], match.groupValues[2].replace('.', '-'))
            }.toList()
        }

        /**
         * 取所有 alias 按 `-` 分段的最长公共前缀作为 bundle 名称。
         * [filekit-core, filekit-dialogs, filekit-coil] -> "filekit"
         */
        fun suggestBundleName(aliases: List<String>): String {
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

        /**
         * 在 TOML 的 [bundles] 中 upsert 一条 bundle。
         * 同名 bundle 已存在时，合并新的 alias（去重）。
         */
        fun upsertBundleEntry(catalogFile: File, bundleName: String, aliases: List<String>) {
            val lines = catalogFile.readText().lines().toMutableList()
            val entryRegex = Regex("""^\s*${Regex.escape(bundleName)}\s*=\s*\[""")

            var sectionStart = -1
            var sectionEnd = lines.size
            for (i in lines.indices) {
                val trimmed = lines[i].trim()
                if (trimmed == "[bundles]") {
                    sectionStart = i
                    for (j in i + 1 until lines.size) {
                        val t = lines[j].trim()
                        if (t.startsWith("[") && t.endsWith("]")) { sectionEnd = j; break }
                    }
                    break
                }
            }

            if (sectionStart >= 0) {
                // 查找已有同名 bundle
                val existingIdx = (sectionStart + 1 until sectionEnd).firstOrNull { entryRegex.containsMatchIn(lines[it]) }
                if (existingIdx != null) {
                    // 合并：解析已有 alias 列表，追加新的（去重）
                    val existingLine = lines[existingIdx]
                    val existingAliases = Regex(""""([^"]+)"""").findAll(existingLine).map { it.groupValues[1] }.toMutableList()
                    val merged = (existingAliases + aliases).distinct()
                    lines[existingIdx] = buildBundleLine(bundleName, merged)
                } else {
                    lines.add(sectionEnd, buildBundleLine(bundleName, aliases))
                }
            } else {
                if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
                lines.add("[bundles]")
                lines.add(buildBundleLine(bundleName, aliases))
            }

            catalogFile.writeText(lines.joinToString("\n"))
        }

        /** 解析 TOML 中指定 bundle 的 alias 列表 */
        fun parseBundleAliases(catalogFile: File, bundleName: String): List<String> {
            if (!catalogFile.exists()) return emptyList()
            val lines = catalogFile.readText().lines()
            var inBundles = false
            val entryRegex = Regex("""^\s*${Regex.escape(bundleName)}\s*=\s*\[""")
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed == "[bundles]") { inBundles = true; continue }
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    if (inBundles) break
                    continue
                }
                if (inBundles && entryRegex.containsMatchIn(line)) {
                    return Regex(""""([^"]+)"""").findAll(line).map { it.groupValues[1] }.toList()
                }
            }
            return emptyList()
        }

        private fun buildBundleLine(bundleName: String, aliases: List<String>): String {
            val quoted = aliases.joinToString(", ") { "\"$it\"" }
            return "$bundleName = [$quoted]"
        }
    }
}

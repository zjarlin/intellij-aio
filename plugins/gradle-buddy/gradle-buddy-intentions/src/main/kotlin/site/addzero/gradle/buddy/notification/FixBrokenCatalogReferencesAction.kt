package site.addzero.gradle.buddy.notification

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.io.File
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor

/**
 * Standalone action to scan all .gradle.kts files and fix broken version catalog references.
 *
 * Behavior:
 * - Single candidate → auto-fix silently
 * - Multiple candidates → show table dialog with combo-box per row for user to pick
 * - Zero candidates → listed in summary
 */
class FixBrokenCatalogReferencesAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!file.name.endsWith(".versions.toml")) return

        val content = FileDocumentManager.getInstance().getDocument(file)?.text
            ?: String(file.contentsToByteArray())

        fixBrokenReferences(project, content, file)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible =
            e.project != null && file != null && file.name.endsWith(".versions.toml")
    }

    // ── Data model ──────────────────────────────────────────────────────

    private data class BrokenRef(
        val ktsFile: VirtualFile,
        val fullMatch: String,
        val refAccessor: String,
        val matchRange: IntRange,
        val candidates: List<String>
    )

    // ── Core logic ──────────────────────────────────────────────────────

    private fun fixBrokenReferences(project: Project, tomlContent: String, tomlFile: VirtualFile) {
        val lines = tomlContent.lines()
        val libraryAliases = parseLibraryAliases(lines)
        val pluginAliases = parseSectionAliases(lines, PLUGINS_HEADER_REGEX)

        val versionAliases = parseSectionAliases(lines, VERSIONS_HEADER_REGEX)
        val bundleAliases = parseSectionAliases(lines, BUNDLES_HEADER_REGEX)

        val accessorToAlias = mutableMapOf<String, String>()
        for (alias in libraryAliases) accessorToAlias[aliasToAccessor(alias)] = alias
        for (alias in pluginAliases) accessorToAlias["plugins." + aliasToAccessor(alias)] = alias
        for (alias in versionAliases) accessorToAlias["versions." + aliasToAccessor(alias)] = alias
        for (alias in bundleAliases) accessorToAlias["bundles." + aliasToAccessor(alias)] = alias

        val normalizedToAccessors = mutableMapOf<String, MutableList<String>>()
        for (accessor in accessorToAlias.keys) {
            normalizedToAccessors.getOrPut(toGradleAccessorName(accessor)) { mutableListOf() }.add(accessor)
        }
        val accessorTokensMap = accessorToAlias.keys.associateWith { it.split('.').map(String::lowercase) }

        val catalogName = tomlFile.nameWithoutExtension.removeSuffix(".versions")

        val basePath = project.basePath ?: return
        val baseDir = VfsUtil.findFile(File(basePath).toPath(), true) ?: return
        val ktsFiles = mutableListOf<VirtualFile>()
        VfsUtil.processFileRecursivelyWithoutIgnored(baseDir) { vf ->
            if (!vf.isDirectory && vf.name.endsWith(".gradle.kts")) ktsFiles.add(vf)
            true
        }

        val catalogRefPattern = Regex("""(?<!\w)${Regex.escape(catalogName)}\.([a-zA-Z0-9_.]+)""")

        // Phase 1: Collect broken references
        val allBroken = mutableListOf<BrokenRef>()
        for (ktsFile in ktsFiles) {
            val doc = FileDocumentManager.getInstance().getDocument(ktsFile) ?: continue
            val text = doc.text
            for (match in catalogRefPattern.findAll(text)) {
                val rawAccessor = match.groupValues[1]
                // Skip false positives (reflection chains, dynamic API calls, catalog declarations)
                if (shouldSkipReference(rawAccessor, text, match, ktsFile)) continue
                // Strip trailing Gradle Provider API method calls captured by the regex
                val refAccessor = stripTrailingMethods(rawAccessor)
                if (refAccessor in accessorToAlias) continue

                // Strategy 0: Duplicate catalog prefix — e.g. libs.libs.xxx → accessor is "libs.xxx",
                // the real accessor is "xxx" (strip the leading catalogName + ".")
                val dupPrefix = catalogName + "."
                val candidates = if (refAccessor.startsWith(dupPrefix)) {
                    val stripped = stripTrailingMethods(refAccessor.removePrefix(dupPrefix))
                    if (stripped in accessorToAlias) listOf(stripped)
                    else findAllCandidates(stripped, normalizedToAccessors, accessorTokensMap)
                } else {
                    findAllCandidates(refAccessor, normalizedToAccessors, accessorTokensMap)
                }

                allBroken.add(BrokenRef(ktsFile, match.value, refAccessor, match.range, candidates))
            }
        }

        if (allBroken.isEmpty()) {
            Messages.showInfoMessage(project, "所有版本目录引用均有效，无需修复。", "Fix Broken References")
            return
        }

        val autoFixable = allBroken.filter { it.candidates.size == 1 }
        val ambiguous   = allBroken.filter { it.candidates.size > 1 }
        val unfixable   = allBroken.filter { it.candidates.isEmpty() }

        // Phase 2: Auto-fix unique matches
        val autoFixed = applyFixes(project, catalogName,
            autoFixable.map { it to it.candidates[0] })

        // Phase 3: Show table dialog only when there are ambiguous items to resolve
        if (ambiguous.isNotEmpty()) {
            val dialog = BrokenRefTableDialog(project, catalogName, ambiguous, unfixable, autoFixed)
            if (dialog.showAndGet()) {
                val manualFixes = dialog.getSelectedFixes()
                val manualFixed = applyFixes(project, catalogName, manualFixes)
                showSummary(project, autoFixed, manualFixed, unfixable)
            } else {
                showSummary(project, autoFixed, 0, unfixable)
            }
        } else {
            // All unfixable or only auto-fixed — just show summary silently
            showSummary(project, autoFixed, 0, unfixable)
        }
    }

    // ── Apply fixes ─────────────────────────────────────────────────────

    private fun applyFixes(
        project: Project,
        catalogName: String,
        fixes: List<Pair<BrokenRef, String>>
    ): Int {
        if (fixes.isEmpty()) return 0
        var count = 0
        WriteCommandAction.runWriteCommandAction(project, "Fix Broken Catalog References", null, {
            val docManager = FileDocumentManager.getInstance()
            val psiDocManager = PsiDocumentManager.getInstance(project)
            val byFile = fixes.groupBy { it.first.ktsFile }
            for ((ktsFile, fileFixes) in byFile) {
                val doc = docManager.getDocument(ktsFile) ?: continue
                var text = doc.text
                for ((broken, chosenAccessor) in fileFixes.sortedByDescending { it.first.matchRange.first }) {
                    val fullNew = "$catalogName.$chosenAccessor"
                    if (broken.matchRange.last < text.length) {
                        text = text.substring(0, broken.matchRange.first) + fullNew +
                                text.substring(broken.matchRange.last + 1)
                        count++
                    }
                }
                doc.replaceString(0, doc.textLength, text)
                psiDocManager.commitDocument(doc)
                docManager.saveDocument(doc)
            }
        })
        return count
    }

    // ── Summary ─────────────────────────────────────────────────────────

    private fun showSummary(project: Project, autoFixed: Int, manualFixed: Int, unfixable: List<BrokenRef>) {
        val sb = StringBuilder()
        if (autoFixed > 0) sb.appendLine("自动修复: $autoFixed 处")
        if (manualFixed > 0) sb.appendLine("手动选择修复: $manualFixed 处")
        if (unfixable.isNotEmpty()) {
            sb.appendLine("无法修复 (未找到候选): ${unfixable.size} 处")
            for (b in unfixable.take(10)) sb.appendLine("  • ${b.ktsFile.name}: ${b.fullMatch}")
            if (unfixable.size > 10) sb.appendLine("  ... 及其他 ${unfixable.size - 10} 处")
        }
        if (autoFixed == 0 && manualFixed == 0 && unfixable.isEmpty()) sb.append("所有引用均有效。")
        Messages.showInfoMessage(project, sb.toString().trim(), "Fix Broken References")
    }

    // ── Table Dialog ────────────────────────────────────────────────────

    /**
     * Dialog with a JBTable showing all broken references.
     *
     * Columns:
     *  0 - File        (read-only)
     *  1 - Broken Ref  (read-only)
     *  2 - Replace With (ComboBox for ambiguous, "—" for unfixable)
     *
     * The combo-box for each ambiguous row contains all candidates;
     * the first candidate is pre-selected.
     */
    private class BrokenRefTableDialog(
        private val project: Project,
        private val catalogName: String,
        private val ambiguous: List<BrokenRef>,
        private val unfixable: List<BrokenRef>,
        private val autoFixedCount: Int
    ) : DialogWrapper(project, true) {

        // Merge ambiguous + unfixable into one list for display
        private val rows: List<BrokenRef> = ambiguous + unfixable

        // Selected replacement per row index (null = skip / unfixable)
        private val selections = Array<String?>(rows.size) { i ->
            val ref = rows[i]
            if (ref.candidates.isNotEmpty()) ref.candidates[0] else null
        }

        init {
            title = "修复断裂的版本目录引用"
            setOKButtonText("Apply")
            setCancelButtonText("Cancel")
            init()
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel(BorderLayout(0, 8))

            // Info label
            val info = buildString {
                if (autoFixedCount > 0) append("已自动修复 $autoFixedCount 处唯一匹配。")
                if (ambiguous.isNotEmpty()) {
                    if (isNotEmpty()) append(" ")
                    append("以下 ${ambiguous.size} 处有多个候选，请选择正确的替换。")
                }
                if (unfixable.isNotEmpty()) {
                    if (isNotEmpty()) append(" ")
                    append("另有 ${unfixable.size} 处无候选项（标记为 —）。")
                }
            }
            panel.add(JLabel(info), BorderLayout.NORTH)

            // Table
            val model = RefTableModel()
            val table = JBTable(model)
            table.setShowGrid(true)
            table.rowHeight = 28

            // Column 0: File
            table.columnModel.getColumn(0).preferredWidth = 180

            // Column 1: Broken Ref
            table.columnModel.getColumn(1).preferredWidth = 280

            // Column 2: Replace With — use combo-box editor & renderer
            val replaceCol = table.columnModel.getColumn(2)
            replaceCol.preferredWidth = 380
            replaceCol.cellRenderer = ComboCellRenderer()
            replaceCol.cellEditor = ComboCellEditor()

            val scrollPane = JBScrollPane(table)
            scrollPane.preferredSize = Dimension(860, 400.coerceAtMost(rows.size * 28 + 40))
            panel.add(scrollPane, BorderLayout.CENTER)

            return panel
        }

        fun getSelectedFixes(): List<Pair<BrokenRef, String>> {
            return rows.indices.mapNotNull { i ->
                val sel = selections[i]
                if (sel != null) rows[i] to sel else null
            }
        }

        // ── Table model ─────────────────────────────────────────────

        private inner class RefTableModel : AbstractTableModel() {
            private val colNames = arrayOf("文件", "断裂引用", "替换为")
            override fun getRowCount() = rows.size
            override fun getColumnCount() = 3
            override fun getColumnName(col: Int) = colNames[col]

            override fun getValueAt(row: Int, col: Int): Any {
                val ref = rows[row]
                return when (col) {
                    0 -> ref.ktsFile.name
                    1 -> "$catalogName.${ref.refAccessor}"
                    2 -> selections[row] ?: "—"
                    else -> ""
                }
            }

            override fun isCellEditable(row: Int, col: Int): Boolean {
                return col == 2 && rows[row].candidates.size > 1
            }

            override fun setValueAt(value: Any?, row: Int, col: Int) {
                if (col == 2 && value is String && value != "—") {
                    selections[row] = value
                    fireTableCellUpdated(row, col)
                }
            }
        }

        // ── Combo renderer ──────────────────────────────────────────

        private inner class ComboCellRenderer : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable, value: Any?, isSelected: Boolean,
                hasFocus: Boolean, row: Int, col: Int
            ): Component {
                val ref = rows[row]
                val display = if (ref.candidates.isEmpty()) {
                    "— (无候选)"
                } else {
                    val sel = selections[row] ?: ref.candidates[0]
                    "$catalogName.$sel"
                }
                return super.getTableCellRendererComponent(table, display, isSelected, hasFocus, row, col)
            }
        }

        // ── Combo editor ────────────────────────────────────────────

        private inner class ComboCellEditor : AbstractCellEditor(), TableCellEditor {
            private var combo: JComboBox<String>? = null
            private var currentRow = -1

            override fun getCellEditorValue(): Any {
                return combo?.selectedItem ?: "—"
            }

            override fun getTableCellEditorComponent(
                table: JTable, value: Any?, isSelected: Boolean, row: Int, col: Int
            ): Component {
                currentRow = row
                val ref = rows[row]
                val items = ref.candidates.map { "$catalogName.$it" }.toTypedArray()
                combo = JComboBox(items).apply {
                    val current = selections[row]
                    if (current != null) selectedItem = "$catalogName.$current"
                }
                return combo!!
            }

            override fun stopCellEditing(): Boolean {
                val selected = (combo?.selectedItem as? String) ?: return super.stopCellEditing()
                selections[currentRow] = selected.removePrefix("$catalogName.")
                return super.stopCellEditing()
            }
        }
    }


    // ── False positive filtering ────────────────────────────────────────

    /**
     * Determine whether a catalog reference match should be skipped (false positive).
     *
     * Filters out:
     * 1. Java/Kotlin reflection chains: `libs.javaClass.superclass.protectionDomain...`
     * 2. Dynamic catalog API calls: `libs.findLibrary(...)`, `libs.findBundle(...)`, etc.
     * 3. Catalog declaration blocks in settings: `create("libs") { from(...) }` inside `versionCatalogs`
     */
    private fun shouldSkipReference(
        refAccessor: String,
        fileText: String,
        match: MatchResult,
        file: VirtualFile
    ): Boolean {
        // 1. Reflection chain — accessor starts with a JVM reflection token
        val firstSegment = refAccessor.substringBefore('.')
        if (firstSegment in REFLECTION_TOKENS) return true

        // 2. Dynamic catalog API — text immediately before the match is a dynamic lookup call
        //    e.g. libs.findLibrary("...").get()
        if (firstSegment in DYNAMIC_API_METHODS) return true

        // 3. Catalog declaration in settings.gradle.kts
        //    e.g. versionCatalogs { create("libs") { from(files("...")) } }
        //    The match here would be on the string literal "libs" inside create(),
        //    but our regex requires `libs.` so this mainly catches `libs.versions.toml` path refs
        //    or any `libs.xxx` inside a versionCatalogs block.
        if (file.name == "settings.gradle.kts") {
            val matchStart = match.range.first
            // Check if this match is inside a versionCatalogs { ... } block
            val textBefore = fileText.substring(0, matchStart)
            val catalogBlockStart = textBefore.lastIndexOf("versionCatalogs")
            if (catalogBlockStart >= 0) {
                // Count braces between versionCatalogs and our match to see if we're still inside
                val between = textBefore.substring(catalogBlockStart)
                val openBraces = between.count { it == '{' }
                val closeBraces = between.count { it == '}' }
                if (openBraces > closeBraces) return true
            }
        }

        // 4. String literal context — match is inside a string (e.g. from(files("../gradle/libs.versions.toml")))
        val matchStart = match.range.first
        val lineStart = fileText.lastIndexOf('\n', matchStart - 1) + 1
        val lineEnd = fileText.indexOf('\n', matchStart).let { if (it < 0) fileText.length else it }
        val line = fileText.substring(lineStart, lineEnd)
        val posInLine = matchStart - lineStart
        // Count unescaped quotes before our position in the line
        val quotesBeforeMatch = line.substring(0, posInLine).count { it == '"' }
        if (quotesBeforeMatch % 2 == 1) return true  // inside a string literal

        return false
    }

    // ── Matching strategies ─────────────────────────────────────────────

    private fun findAllCandidates(
        brokenRef: String,
        normalizedToAccessors: Map<String, MutableList<String>>,
        accessorTokensMap: Map<String, List<String>>
    ): List<String> {
        val candidates = mutableSetOf<String>()
        // Strategy 1: Exact normalized accessor match
        normalizedToAccessors[toGradleAccessorName(brokenRef)]?.let { candidates.addAll(it) }

        val refTokens = brokenRef.split('.').map(String::lowercase)
        if (refTokens.isEmpty()) return candidates.toList()

        // Strategy 2: Token suffix match
        accessorTokensMap.entries.filter { (_, tokens) ->
            tokens.size > refTokens.size && tokens.takeLast(refTokens.size) == refTokens
        }.forEach { candidates.add(it.key) }

        // Strategy 3: Token ordered-subset match
        accessorTokensMap.entries.filter { (_, tokens) ->
            tokens.size > refTokens.size && isOrderedSubset(refTokens, tokens)
        }.forEach { candidates.add(it.key) }

        return candidates.toList()
    }

    private fun isOrderedSubset(sub: List<String>, full: List<String>): Boolean {
        var fi = 0
        for (token in sub) {
            val idx = full.subList(fi, full.size).indexOf(token)
            if (idx < 0) return false
            fi += idx + 1
        }
        return true
    }

    // ── Utility ─────────────────────────────────────────────────────────

    private fun aliasToAccessor(alias: String): String = alias.replace('-', '.').replace('_', '.')

    /**
     * Strip trailing Gradle Provider API method names that get captured by the regex.
     * e.g. "versions.jdk.get" → "versions.jdk", "versions.android.compileSdk.get" → "versions.android.compileSdk"
     */
    private fun stripTrailingMethods(accessor: String): String {
        var result = accessor
        while (true) {
            val lastDot = result.lastIndexOf('.')
            if (lastDot < 0) break
            val lastSegment = result.substring(lastDot + 1)
            if (lastSegment in PROVIDER_API_METHODS) {
                result = result.substring(0, lastDot)
            } else {
                break
            }
        }
        return result
    }

    private fun toGradleAccessorName(key: String): String = key
        .replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
        .split(Regex("[\\-_.]"))
        .joinToString("") { it.lowercase() }

    private fun parseLibraryAliases(lines: List<String>) = parseSectionAliases(lines, LIBRARIES_HEADER_REGEX)

    private fun parseSectionAliases(lines: List<String>, headerRegex: Regex): List<String> {
        val result = mutableListOf<String>()
        var inSection = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.matches(SECTION_HEADER_REGEX)) { inSection = trimmed.matches(headerRegex); continue }
            if (!inSection || trimmed.isBlank() || trimmed.startsWith("#")) continue
            val alias = trimmed.substringBefore("=").trim()
            if (alias.isNotEmpty()) result.add(alias)
        }
        return result
    }

    companion object {
        private val SECTION_HEADER_REGEX = Regex("^\\[.+]\\s*(#.*)?$")
        private val LIBRARIES_HEADER_REGEX = Regex("^\\[libraries]\\s*(#.*)?$")
        private val VERSIONS_HEADER_REGEX = Regex("^\\[versions]\\s*(#.*)?$")
        private val BUNDLES_HEADER_REGEX = Regex("^\\[bundles]\\s*(#.*)?$")
        private val PLUGINS_HEADER_REGEX = Regex("^\\[plugins]\\s*(#.*)?$")

        /** JVM reflection / meta tokens that are never catalog accessors */
        private val REFLECTION_TOKENS = setOf(
            "javaClass", "class", "superclass", "protectionDomain",
            "codeSource", "classLoader", "kotlin"
        )

        /** Dynamic catalog API methods — `libs.findLibrary(...)`, etc. */
        private val DYNAMIC_API_METHODS = setOf(
            "findLibrary", "findBundle", "findPlugin", "findVersion"
        )

        /** Gradle Provider API methods that get captured as trailing accessor segments */
        private val PROVIDER_API_METHODS = setOf(
            "get", "getOrNull", "orNull", "asProvider", "map", "flatMap",
            "orElse", "forUseAtConfigurationTime", "toString"
        )
    }
}

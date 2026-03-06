package site.addzero.gradle.buddy.notification

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import site.addzero.gradle.buddy.intentions.projectdep.ModulePathDistance
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.AbstractCellEditor
import javax.swing.JComboBox
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor

/**
 * Standalone action: scan all .gradle.kts files and fix broken project references.
 *
 * Supported:
 * 1) project(":a:b:c") call (fix broken path by leaf module name)
 *
 * Behavior:
 * - Collects all broken refs in the project
 * - Shows a table dialog with before/after, allowing per-row "Skip" or picking candidates
 * - Applies replacements in one write command
 */
class FixBrokenProjectReferencesAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        runBatchFix(project)
    }

    private fun runBatchFixFlow(project: Project, basePath: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Scanning broken project references...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val allModulePaths = scanAllModules(basePath).distinct()
                val modulePathSet = allModulePaths.toSet()
                val modulesByLeaf = allModulePaths
                    .asSequence()
                    .filter { it != ":" }
                    .groupBy { it.substringAfterLast(':') }

                val ktsFiles = collectGradleKtsFiles(basePath)

                val broken = ApplicationManager.getApplication().runReadAction<List<BrokenProjectRef>> {
                    collectBrokenRefs(project, basePath, ktsFiles, modulePathSet)
                }

                val merged = mergeBrokenRefs(broken, modulesByLeaf)

                ApplicationManager.getApplication().invokeLater {
                    if (merged.isEmpty()) {
                        Messages.showInfoMessage(project, "所有 project 引用均有效。", "Fix Broken Project References")
                        return@invokeLater
                    }

                    val dialog = BrokenProjectRefTableDialog(project, merged)
                    if (!dialog.showAndGet()) return@invokeLater

                    val chosenFixes = dialog.getSelectedFixes()
                    if (chosenFixes.isEmpty()) {
                        Messages.showInfoMessage(project, "未选择任何修复项。", "Fix Broken Project References")
                        return@invokeLater
                    }

                    val fixedCount = applyFixes(project, chosenFixes)
                    val skippedGroups = dialog.getSkippedGroupsCount()
                    val unfixableGroups = dialog.getUnfixableGroupsCount()

                    val summary = buildString {
                        appendLine("已修复: $fixedCount 处")
                        if (skippedGroups > 0) appendLine("已跳过: $skippedGroups 组")
                        if (unfixableGroups > 0) appendLine("无候选: $unfixableGroups 组")
                    }.trim()

                    Messages.showInfoMessage(project, summary, "Fix Broken Project References")
                }
            }
        })
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.basePath != null
    }

    // ── Data model ──────────────────────────────────────────────────────

    private enum class RefKind { PROJECT_CALL }

    /** A single broken reference occurrence in a kts file. */
    private data class BrokenProjectRef(
        val ktsFile: VirtualFile,
        val kind: RefKind,
        val key: String,
        val beforeDisplay: String,
        val leaf: String,
        val matchRange: IntRange,
        val contextModulePath: String?,
        val derivedPath: String?
    )

    /** Merged group by key+kind, shown as one row in the table. */
    private data class MergedBrokenProjectRef(
        val kind: RefKind,
        val key: String,
        val beforeDisplay: String,
        val leaf: String,
        val candidates: List<String>,
        val occurrences: List<BrokenProjectRef>
    ) {
        val occurrenceCount: Int get() = occurrences.size
        val fileNames: String
            get() {
                val names = occurrences.map { it.ktsFile.name }.distinct()
                val limit = 6
                return if (names.size <= limit) {
                    names.joinToString(", ")
                } else {
                    names.take(limit).joinToString(", ") + " … +${names.size - limit}"
                }
            }
    }

    // ── Scan & collect ──────────────────────────────────────────────────

    private fun collectBrokenRefs(
        project: Project,
        basePath: String,
        ktsFiles: List<VirtualFile>,
        modulePathSet: Set<String>
    ): List<BrokenProjectRef> {
        val psiManager = PsiManager.getInstance(project)
        val result = mutableListOf<BrokenProjectRef>()

        for (vf in ktsFiles) {
            val psiFile = psiManager.findFile(vf) ?: continue
            val moduleContext = detectModulePathFromVirtualFile(vf, basePath)

            // 1) project(":...") calls (anywhere)
            val projectCalls = PsiTreeUtil.collectElementsOfType(psiFile, KtCallExpression::class.java)
                .filter { it.calleeExpression?.text == "project" }

            for (call in projectCalls) {
                val stringExpr = call.valueArguments.firstOrNull()
                    ?.getArgumentExpression() as? KtStringTemplateExpression ?: continue
                val path = extractLiteralString(stringExpr)?.trim() ?: continue
                if (!path.startsWith(":")) continue
                if (path in modulePathSet) continue

                val leaf = path.substringAfterLast(':').trim()
                if (leaf.isEmpty()) continue

                val range = stringExpr.textRange
                result.add(
                    BrokenProjectRef(
                        ktsFile = vf,
                        kind = RefKind.PROJECT_CALL,
                        key = path,
                        beforeDisplay = "project(\"$path\")",
                        leaf = leaf,
                        matchRange = range.startOffset until range.endOffset,
                        contextModulePath = moduleContext,
                        derivedPath = path
                    )
                )
            }
        }

        return result
    }

    private fun mergeBrokenRefs(
        broken: List<BrokenProjectRef>,
        modulesByLeaf: Map<String, List<String>>
    ): List<MergedBrokenProjectRef> {
        if (broken.isEmpty()) return emptyList()

        return broken
            .groupBy { it.kind to it.key }
            .map { (k, occurrences) ->
                val (kind, key) = k
                val leaf = occurrences.first().leaf
                val beforeDisplay = occurrences.first().beforeDisplay
                val candidates = modulesByLeaf[leaf].orEmpty()
                val sortedCandidates = sortCandidates(candidates, occurrences)
                MergedBrokenProjectRef(
                    kind = kind,
                    key = key,
                    beforeDisplay = beforeDisplay,
                    leaf = leaf,
                    candidates = sortedCandidates,
                    occurrences = occurrences
                )
            }
            .sortedWith(compareBy<MergedBrokenProjectRef>({ it.candidates.isEmpty() }, { it.leaf }, { it.beforeDisplay }))
    }

    private fun sortCandidates(candidates: List<String>, occurrences: List<BrokenProjectRef>): List<String> {
        if (candidates.size <= 1) return candidates
        return candidates.sortedWith(compareBy<String> { candidate ->
            occurrences.sumOf { occ ->
                val base = occ.contextModulePath ?: occ.derivedPath
                if (base == null) 0L else ModulePathDistance.distance(base, candidate).toLong()
            }
        }.thenBy { it })
    }

    // ── Apply fixes ─────────────────────────────────────────────────────

    private fun applyFixes(project: Project, fixes: List<Pair<BrokenProjectRef, String>>): Int {
        if (fixes.isEmpty()) return 0

        var fixed = 0
        val docManager = FileDocumentManager.getInstance()

        WriteCommandAction.runWriteCommandAction(project, "Fix Broken Project References", null, {
            val psiDocManager = com.intellij.psi.PsiDocumentManager.getInstance(project)
            val byFile = fixes.groupBy { it.first.ktsFile }
            for ((ktsFile, fileFixes) in byFile) {
                val doc = docManager.getDocument(ktsFile) ?: continue
                var text = doc.text

                for ((broken, chosenModulePath) in fileFixes.sortedByDescending { it.first.matchRange.first }) {
                    val start = broken.matchRange.first
                    val endExclusive = broken.matchRange.last + 1
                    if (start < 0 || endExclusive > text.length || start >= endExclusive) continue

                    val replacement = "\"$chosenModulePath\""

                    text = text.substring(0, start) + replacement + text.substring(endExclusive)
                    fixed++
                }

                doc.replaceString(0, doc.textLength, text)
                psiDocManager.commitDocument(doc)
                docManager.saveDocument(doc)
            }
        })

        return fixed
    }

    // ── Table dialog ────────────────────────────────────────────────────

    private class BrokenProjectRefTableDialog(
        project: Project,
        private val rows: List<MergedBrokenProjectRef>
    ) : DialogWrapper(project, true) {

        // Selected module path per row; null means "Skip" or "Unfixable"
        private val selections = Array<String?>(rows.size) { i ->
            rows[i].candidates.firstOrNull()
        }

        init {
            title = "修复断裂的 project 引用"
            setOKButtonText("Apply")
            setCancelButtonText("Cancel")
            init()
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel(BorderLayout(0, 8))

            val unfixable = rows.count { it.candidates.isEmpty() }
            val totalOccurrences = rows.sumOf { it.occurrenceCount }
            val info = buildString {
                append("发现 ${rows.size} 组断裂引用，共 $totalOccurrences 处。")
                if (unfixable > 0) append(" 其中 $unfixable 组无候选（显示为 —）。")
                append(" 可在“替换为”列选择候选或跳过。")
            }
            panel.add(JLabel(info), BorderLayout.NORTH)

            val model = RefTableModel()
            val table = JBTable(model)
            table.setShowGrid(true)
            table.rowHeight = 28

            table.columnModel.getColumn(0).preferredWidth = 260
            table.columnModel.getColumn(1).preferredWidth = 360

            val replaceCol = table.columnModel.getColumn(2)
            replaceCol.preferredWidth = 520
            replaceCol.cellRenderer = ComboCellRenderer()
            replaceCol.cellEditor = ComboCellEditor()

            val scrollPane = JBScrollPane(table)
            scrollPane.preferredSize = Dimension(1150, 480.coerceAtMost(rows.size * 28 + 40))
            panel.add(scrollPane, BorderLayout.CENTER)

            return panel
        }

        fun getSelectedFixes(): List<Pair<BrokenProjectRef, String>> {
            return rows.indices.flatMap { i ->
                val chosen = selections[i] ?: return@flatMap emptyList()
                rows[i].occurrences.map { it to chosen }
            }
        }

        fun getSkippedGroupsCount(): Int {
            return rows.indices.count { i -> rows[i].candidates.isNotEmpty() && selections[i] == null }
        }

        fun getUnfixableGroupsCount(): Int {
            return rows.count { it.candidates.isEmpty() }
        }

        private inner class RefTableModel : AbstractTableModel() {
            private val colNames = arrayOf("涉及文件", "替换前", "替换后")
            override fun getRowCount() = rows.size
            override fun getColumnCount() = 3
            override fun getColumnName(col: Int) = colNames[col]

            override fun getValueAt(row: Int, col: Int): Any {
                val ref = rows[row]
                return when (col) {
                    0 -> "${ref.fileNames} (×${ref.occurrenceCount})"
                    1 -> ref.beforeDisplay
                    2 -> selections[row] ?: "—"
                    else -> ""
                }
            }

            override fun isCellEditable(row: Int, col: Int): Boolean {
                return col == 2 && rows[row].candidates.isNotEmpty()
            }

            override fun setValueAt(value: Any?, row: Int, col: Int) {
                if (col != 2 || value !is String) return
                selections[row] = if (value == SKIP_ITEM) null else value
                fireTableCellUpdated(row, col)
            }
        }

        private inner class ComboCellRenderer : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                col: Int
            ): Component {
                val ref = rows[row]
                val display = if (ref.candidates.isEmpty()) {
                    "— (无候选)"
                } else {
                    val sel = selections[row]
                    if (sel == null) {
                        "— (跳过)"
                    } else {
                        "project(\"$sel\")"
                    }
                }
                return super.getTableCellRendererComponent(table, display, isSelected, hasFocus, row, col)
            }
        }

        private inner class ComboCellEditor : AbstractCellEditor(), TableCellEditor {
            private var combo: JComboBox<String>? = null
            private var currentRow: Int = -1

            override fun getCellEditorValue(): Any {
                return combo?.selectedItem ?: SKIP_ITEM
            }

            override fun getTableCellEditorComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                row: Int,
                col: Int
            ): Component {
                currentRow = row
                val ref = rows[row]
                val items = (listOf(SKIP_ITEM) + ref.candidates).toTypedArray()
                combo = JComboBox(items).apply {
                    val current = selections[row]
                    selectedItem = current ?: SKIP_ITEM
                    renderer = object : DefaultListCellRenderer() {
                        override fun getListCellRendererComponent(
                            list: javax.swing.JList<*>?,
                            value: Any?,
                            index: Int,
                            isSelected: Boolean,
                            cellHasFocus: Boolean
                        ): Component {
                            val raw = value as? String ?: SKIP_ITEM
                            val display = if (raw == SKIP_ITEM) "— (跳过)" else "project(\"$raw\")"
                            return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus)
                        }
                    }
                    addActionListener { this@ComboCellEditor.stopCellEditing() }
                }
                return combo as JComboBox<String>
            }
        }

        companion object {
            private const val SKIP_ITEM = "— Skip"
        }
    }

    // ── Utilities ───────────────────────────────────────────────────────

    private fun extractLiteralString(expr: KtStringTemplateExpression): String? {
        if (expr.entries.any { it !is KtLiteralStringTemplateEntry }) return null
        return expr.entries.joinToString(separator = "") { it.text }
    }

    private fun hasSettingsFile(dir: VirtualFile): Boolean {
        return dir.findChild("settings.gradle.kts") != null || dir.findChild("settings.gradle") != null
    }

    /**
     * Scan the project for Gradle modules by finding build.gradle.kts / build.gradle,
     * skipping nested Gradle builds (directories containing settings.gradle(.kts)).
     */
    private fun scanAllModules(basePath: String): List<String> {
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return emptyList()

        val modules = mutableListOf<String>()
        VfsUtilCore.visitChildrenRecursively(baseDir, object : VirtualFileVisitor<Void>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (!file.isDirectory) return true

                val name = file.name
                if (name.startsWith(".") || name == "build" || name == "buildSrc" || name == "out" || name == "node_modules" || name == ".gradle") {
                    return false
                }
                if (file != baseDir && hasSettingsFile(file)) {
                    return false
                }

                val hasBuildFile = file.findChild("build.gradle.kts") != null || file.findChild("build.gradle") != null
                if (hasBuildFile) {
                    val rel = file.path.removePrefix(basePath).trimStart('/')
                    val modulePath = if (rel.isEmpty()) ":" else ":${rel.replace('/', ':')}"
                    modules.add(modulePath)
                }

                return true
            }
        })
        return modules
    }

    private fun collectGradleKtsFiles(basePath: String): List<VirtualFile> {
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return emptyList()

        val files = mutableListOf<VirtualFile>()
        VfsUtilCore.visitChildrenRecursively(baseDir, object : VirtualFileVisitor<Void>() {
            override fun visitFile(file: VirtualFile): Boolean {
                val name = file.name
                if (file.isDirectory) {
                    if (name.startsWith(".") || name == "build" || name == "buildSrc" || name == "out" || name == "node_modules" || name == ".gradle") {
                        return false
                    }
                    if (file != baseDir && hasSettingsFile(file)) {
                        return false
                    }
                    return true
                }

                if (name.endsWith(".gradle.kts")) files.add(file)
                return true
            }
        })
        return files
    }

    private fun detectModulePathFromVirtualFile(vFile: VirtualFile, basePath: String): String? {
        if (!vFile.path.startsWith(basePath)) return null

        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return null
        var dir = if (vFile.isDirectory) vFile else vFile.parent
        while (dir != null && dir.path.startsWith(basePath)) {
            if (dir != baseDir && hasSettingsFile(dir)) return null
            if (dir.findChild("build.gradle.kts") != null || dir.findChild("build.gradle") != null) {
                val rel = dir.path.removePrefix(basePath).trimStart('/')
                return if (rel.isEmpty()) ":" else ":${rel.replace('/', ':')}"
            }
            dir = dir.parent
        }
        return null
    }

    companion object {
        fun runBatchFix(project: Project) {
            val basePath = project.basePath ?: return
            FixBrokenProjectReferencesAction().runBatchFixFlow(project, basePath)
        }

        fun hasOtherBrokenReferences(
            project: Project,
            currentFile: VirtualFile,
            currentRange: IntRange
        ): Boolean {
            val basePath = project.basePath ?: return false
            val action = FixBrokenProjectReferencesAction()

            return ApplicationManager.getApplication().runReadAction<Boolean> {
                val allModulePaths = action.scanAllModules(basePath).distinct()
                val modulePathSet = allModulePaths.toSet()
                val ktsFiles = action.collectGradleKtsFiles(basePath)
                val broken = action.collectBrokenRefs(project, basePath, ktsFiles, modulePathSet)

                broken.any { ref ->
                    ref.ktsFile.path != currentFile.path ||
                        ref.matchRange.first != currentRange.first ||
                        ref.matchRange.last != currentRange.last
                }
            }
        }

    }
}

package site.addzero.i18n.buddy.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import site.addzero.i18n.buddy.scanner.ScanResult
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.table.AbstractTableModel

/**
 * Preview dialog showing all scanned hardcoded strings.
 * Users can:
 * - Toggle selection (checkbox)
 * - Edit generated key names
 * - See file path and line number
 */
class I18nPreviewDialog(
    project: Project,
    private val results: List<ScanResult>,
) : DialogWrapper(project, true) {

    init {
        title = "I18n Buddy — Preview Hardcoded Strings (${results.size} found)"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val model = ScanResultTableModel(results)
        val table = JBTable(model).apply {
            columnModel.getColumn(COL_SELECTED).preferredWidth = 40
            columnModel.getColumn(COL_SELECTED).maxWidth = 50
            columnModel.getColumn(COL_TEXT).preferredWidth = 200
            columnModel.getColumn(COL_KEY).preferredWidth = 160
            columnModel.getColumn(COL_MODE).preferredWidth = 80
            columnModel.getColumn(COL_MODE).maxWidth = 100
            columnModel.getColumn(COL_FILE).preferredWidth = 220
            columnModel.getColumn(COL_LINE).preferredWidth = 50
            columnModel.getColumn(COL_LINE).maxWidth = 60
            setRowHeight(24)
        }

        val scrollPane = JBScrollPane(table)
        scrollPane.preferredSize = Dimension(850, 450)
        return scrollPane
    }

    fun getResults(): List<ScanResult> = results

    companion object {
        const val COL_SELECTED = 0
        const val COL_TEXT = 1
        const val COL_KEY = 2
        const val COL_MODE = 3
        const val COL_FILE = 4
        const val COL_LINE = 5
    }
}

private class ScanResultTableModel(private val results: List<ScanResult>) : AbstractTableModel() {

    private val columns = arrayOf("✓", "String", "Key", "Mode", "File", "Line")

    override fun getRowCount(): Int = results.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        I18nPreviewDialog.COL_SELECTED -> java.lang.Boolean::class.java
        I18nPreviewDialog.COL_LINE -> Integer::class.java
        else -> String::class.java
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean =
        columnIndex == I18nPreviewDialog.COL_SELECTED || columnIndex == I18nPreviewDialog.COL_KEY

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val r = results[rowIndex]
        return when (columnIndex) {
            I18nPreviewDialog.COL_SELECTED -> r.selected
            I18nPreviewDialog.COL_TEXT -> r.text
            I18nPreviewDialog.COL_KEY -> r.generatedKey
            I18nPreviewDialog.COL_MODE -> if (r.inComposable) "i18n" else "const"
            I18nPreviewDialog.COL_FILE -> r.relativePath
            I18nPreviewDialog.COL_LINE -> r.line + 1
            else -> ""
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        val r = results[rowIndex]
        when (columnIndex) {
            I18nPreviewDialog.COL_SELECTED -> r.selected = aValue as? Boolean ?: true
            I18nPreviewDialog.COL_KEY -> r.generatedKey = aValue as? String ?: r.generatedKey
        }
        fireTableCellUpdated(rowIndex, columnIndex)
    }
}

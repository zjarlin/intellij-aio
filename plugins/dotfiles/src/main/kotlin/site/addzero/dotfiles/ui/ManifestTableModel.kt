package site.addzero.dotfiles.ui

import javax.swing.table.AbstractTableModel
import site.addzero.dotfiles.sync.DotfilesSyncStateService

class ManifestTableModel : AbstractTableModel() {
    private val columns = listOf(
        "Id",
        "Path",
        "Scope",
        "Mode",
        "IncludeIgnored",
        "ExcludeFromGit",
    )

    private val rows = mutableListOf<DotfilesSyncStateService.EntryState>()

    fun setRows(entries: List<DotfilesSyncStateService.EntryState>) {
        rows.clear()
        rows.addAll(entries.map { it.copy() })
        fireTableDataChanged()
    }

    fun getRows(): List<DotfilesSyncStateService.EntryState> = rows.map { it.copy() }

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        4, 5 -> Boolean::class.java
        else -> String::class.java
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex != 0

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> row.id
            1 -> row.path
            2 -> row.scope
            3 -> row.mode
            4 -> row.includeIgnored
            5 -> row.excludeFromGit
            else -> ""
        }
    }

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        val row = rows[rowIndex]
        when (columnIndex) {
            1 -> row.path = value?.toString().orEmpty()
            2 -> row.scope = value?.toString().orEmpty()
            3 -> row.mode = value?.toString().orEmpty()
            4 -> row.includeIgnored = value as? Boolean ?: false
            5 -> row.excludeFromGit = value as? Boolean ?: false
        }
        fireTableCellUpdated(rowIndex, columnIndex)
    }
}

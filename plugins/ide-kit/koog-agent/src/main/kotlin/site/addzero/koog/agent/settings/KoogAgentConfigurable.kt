package site.addzero.koog.agent.settings

import com.intellij.openapi.options.SearchableConfigurable
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.DefaultCellEditor
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

class KoogAgentConfigurable : SearchableConfigurable {
    private val settingsService = KoogAgentSettingsService.getInstance()
    private val enabledCheckBox = JCheckBox("Enable Alt+Enter comment code generation")
    private val tableModel = ModelTableModel()
    private val table = JTable(tableModel)
    private var panel: JPanel? = null

    override fun getId(): String {
        return "site.addzero.koog.agent.settings"
    }

    override fun getDisplayName(): String {
        return "ide-kit AI"
    }

    override fun createComponent(): JComponent {
        if (panel == null) {
            configureTable()
            panel = JPanel(BorderLayout()).apply {
                add(enabledCheckBox, BorderLayout.NORTH)
                add(JScrollPane(table), BorderLayout.CENTER)
                add(createButtonPanel(), BorderLayout.SOUTH)
            }
        }
        reset()
        return panel!!
    }

    private fun configureTable() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        table.rowHeight = 24
        table.columnModel.getColumn(ModelTableModel.VENDOR_COLUMN).cellEditor = DefaultCellEditor(
            JComboBox(KoogAgentProvider.entries.map { it.name }.toTypedArray()),
        )
        table.columnModel.getColumn(ModelTableModel.ENABLED_COLUMN).preferredWidth = 70
        table.columnModel.getColumn(ModelTableModel.VENDOR_COLUMN).preferredWidth = 150
        table.columnModel.getColumn(ModelTableModel.BASE_URL_COLUMN).preferredWidth = 260
        table.columnModel.getColumn(ModelTableModel.MODEL_COLUMN).preferredWidth = 190
        table.columnModel.getColumn(ModelTableModel.API_KEY_COLUMN).preferredWidth = 260
        table.columnModel.getColumn(ModelTableModel.ORDER_COLUMN).preferredWidth = 70
    }

    private fun createButtonPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
            add(
                JLabel("Fallback order is ascending. Add inserts first. Duplicate vendor/base URL/model/API key rows are collapsed on apply."),
                BorderLayout.CENTER,
            )
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
                    add(JButton("Add").apply { addActionListener { addRow() } })
                    add(JButton("Remove").apply { addActionListener { removeSelectedRow() } })
                    add(JButton("Up").apply { addActionListener { moveSelectedRow(-1) } })
                    add(JButton("Down").apply { addActionListener { moveSelectedRow(1) } })
                    add(JButton("Re-detect").apply { addActionListener { redetect() } })
                    add(JButton("Dedupe").apply { addActionListener { dedupeRows() } })
                },
                BorderLayout.EAST,
            )
        }
    }

    private fun addRow() {
        tableModel.prepend(
            KoogAgentModelState(
                enabled = true,
                vendor = KoogAgentProvider.OPENAI_COMPATIBLE.name,
                baseUrl = "",
                model = "",
                apiKey = "",
                order = 10,
                detected = false,
                source = "",
            ),
        )
        tableModel.renumberOrders()
        selectRow(0)
    }

    private fun removeSelectedRow() {
        val row = table.selectedModelRow() ?: return
        tableModel.remove(row)
        tableModel.renumberOrders()
        selectRow(row.coerceAtMost(tableModel.rowCount - 1))
    }

    private fun moveSelectedRow(delta: Int) {
        val row = table.selectedModelRow() ?: return
        val target = row + delta
        if (target !in 0 until tableModel.rowCount) {
            return
        }
        tableModel.move(row, target)
        tableModel.renumberOrders()
        selectRow(target)
    }

    private fun redetect() {
        val rows = KoogAgentDetectedModelMerger.merge(
            existingModels = tableModel.rows,
            detectedModels = KoogAgentCredentialDetector.detect(),
            addMissing = true,
        )
        tableModel.setRows(rows)
    }

    private fun dedupeRows() {
        tableModel.setRows(KoogAgentModelDeduplicator.deduplicate(tableModel.rows))
    }

    private fun selectRow(row: Int) {
        if (row in 0 until tableModel.rowCount) {
            table.selectionModel.setSelectionInterval(row, row)
        }
    }

    override fun isModified(): Boolean {
        val uiState = currentUiState()
        val stored = settingsService.snapshot()
        return uiState.enabled != stored.enabled ||
            KoogAgentModelDeduplicator.deduplicate(uiState.models).modelsSignature() !=
            KoogAgentModelDeduplicator.deduplicate(stored.models).modelsSignature()
    }

    override fun apply() {
        val state = currentUiState()
        state.models = KoogAgentModelDeduplicator.deduplicate(state.models)
        settingsService.update(state)
        reset()
    }

    override fun reset() {
        val state = settingsService.snapshot()
        enabledCheckBox.isSelected = state.enabled
        tableModel.setRows(state.models)
    }

    private fun currentUiState(): KoogAgentSettingsState {
        return KoogAgentSettingsState().apply {
            enabled = enabledCheckBox.isSelected
            debounceMillis = settingsService.snapshot().debounceMillis
            detectedDefaultsInitialized = settingsService.snapshot().detectedDefaultsInitialized
            models = tableModel.rows.map { it.copy() }.toMutableList()
        }
    }

    private fun JTable.selectedModelRow(): Int? {
        val viewRow = selectedRow
        if (viewRow < 0) {
            return null
        }
        return convertRowIndexToModel(viewRow)
    }

    private fun Collection<KoogAgentModelState>.modelsSignature(): String {
        return joinToString("|") { model ->
            listOf(
                model.enabled,
                model.vendor,
                model.baseUrl,
                model.model,
                model.apiKey,
                model.order,
                model.detected,
                model.source,
            ).joinToString("\u0000")
        }
    }

    private class ModelTableModel : AbstractTableModel() {
        var rows: MutableList<KoogAgentModelState> = mutableListOf()
            private set

        override fun getRowCount(): Int = rows.size

        override fun getColumnCount(): Int = COLUMNS.size

        override fun getColumnName(column: Int): String = COLUMNS[column]

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                ENABLED_COLUMN -> Boolean::class.javaObjectType
                ORDER_COLUMN -> Int::class.javaObjectType
                else -> String::class.java
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            return when (columnIndex) {
                ENABLED_COLUMN -> row.enabled
                VENDOR_COLUMN -> row.vendor
                BASE_URL_COLUMN -> row.baseUrl
                MODEL_COLUMN -> row.model
                API_KEY_COLUMN -> row.apiKey
                ORDER_COLUMN -> row.order
                SOURCE_COLUMN -> row.source
                else -> ""
            }
        }

        override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
            val row = rows[rowIndex]
            when (columnIndex) {
                ENABLED_COLUMN -> row.enabled = value as? Boolean ?: value.toString().toBoolean()
                VENDOR_COLUMN -> row.vendor = value?.toString().orEmpty()
                BASE_URL_COLUMN -> row.baseUrl = value?.toString().orEmpty()
                MODEL_COLUMN -> row.model = value?.toString().orEmpty()
                API_KEY_COLUMN -> row.apiKey = value?.toString().orEmpty()
                ORDER_COLUMN -> row.order = value?.toString()?.toIntOrNull() ?: row.order
                SOURCE_COLUMN -> row.source = value?.toString().orEmpty()
            }
            fireTableRowsUpdated(rowIndex, rowIndex)
        }

        fun setRows(newRows: Collection<KoogAgentModelState>) {
            rows = newRows.map { it.copy() }
                .sortedWith(ROW_ORDER)
                .toMutableList()
            fireTableDataChanged()
        }

        fun prepend(row: KoogAgentModelState) {
            rows.add(0, row)
            fireTableRowsInserted(0, 0)
        }

        fun remove(row: Int) {
            if (row !in rows.indices) {
                return
            }
            rows.removeAt(row)
            fireTableDataChanged()
        }

        fun move(from: Int, to: Int) {
            if (from !in rows.indices || to !in rows.indices) {
                return
            }
            val row = rows.removeAt(from)
            rows.add(to, row)
            fireTableDataChanged()
        }

        fun renumberOrders() {
            rows.forEachIndexed { index, row -> row.order = (index + 1) * 10 }
            fireTableDataChanged()
        }

        companion object {
            const val ENABLED_COLUMN = 0
            const val VENDOR_COLUMN = 1
            const val BASE_URL_COLUMN = 2
            const val MODEL_COLUMN = 3
            const val API_KEY_COLUMN = 4
            const val ORDER_COLUMN = 5
            const val SOURCE_COLUMN = 6

            private val COLUMNS = listOf("Enabled", "Vendor", "Base URL", "Model", "API Key", "Order", "Source")
            private val ROW_ORDER = compareBy<KoogAgentModelState> { it.order }
                .thenBy { it.detected }
                .thenBy { it.vendor }
                .thenBy { it.model }
        }
    }
}

package site.addzero.smart.intentions.kotlin.entityfieldmerge

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

internal class EntityFieldMergeDialog(
    project: Project,
    private val plan: EntityFieldMergePlan,
) : DialogWrapper(project) {
    private val rowStates = plan.rows.map { row -> EntityFieldMergeRowState(row, row.defaultSelected) }
    private val hideSameFieldsCheckBox = JBCheckBox("折叠相同字段", true)
    private val deleteSourceFilesCheckBox = JBCheckBox("合并后提示删除源文件", true)
    private val summaryLabel = JBLabel()
    private val tableModel = EntityFieldMergeTableModel(rowStates) { hideSameFieldsCheckBox.isSelected }
    private val table = JBTable(tableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        autoCreateRowSorter = false
        rowHeight = JBUI.scale(24)
        preferredScrollableViewportSize = Dimension(JBUI.scale(900), JBUI.scale(360))
        columnModel.getColumn(0).preferredWidth = JBUI.scale(58)
        columnModel.getColumn(1).preferredWidth = JBUI.scale(78)
        columnModel.getColumn(2).preferredWidth = JBUI.scale(180)
        columnModel.getColumn(3).preferredWidth = JBUI.scale(220)
        columnModel.getColumn(4).preferredWidth = JBUI.scale(120)
        columnModel.getColumn(5).preferredWidth = JBUI.scale(240)
    }

    init {
        title = "合并实体字段到 ${plan.target.displayName}"
        setOKButtonText("合并所选字段")
        hideSameFieldsCheckBox.addActionListener {
            tableModel.fireTableDataChanged()
            refreshSummary()
        }
        deleteSourceFilesCheckBox.toolTipText = "写入完成后弹出确认框，由你决定是否删除源文件。"
        init()
        refreshSummary()
    }

    override fun createCenterPanel(): JComponent {
        val headerPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyBottom(8)
            add(
                JBLabel("源实体：${plan.sources.joinToString { source -> source.displayName }}"),
                BorderLayout.NORTH,
            )
            add(summaryLabel, BorderLayout.SOUTH)
        }

        val toolbarPanel = JPanel().apply {
            add(hideSameFieldsCheckBox)
            add(deleteSourceFilesCheckBox)
            add(JButton("选择全部新增字段").apply {
                addActionListener {
                    rowStates.forEach { state ->
                        if (state.row.status == EntityFieldMergeStatus.NEW) {
                            state.selected = true
                        }
                    }
                    tableModel.fireTableDataChanged()
                    refreshSummary()
                }
            })
            add(JButton("清空选择").apply {
                addActionListener {
                    rowStates.forEach { state -> state.selected = false }
                    tableModel.fireTableDataChanged()
                    refreshSummary()
                }
            })
        }

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(headerPanel, BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(toolbarPanel, BorderLayout.SOUTH)
        }
    }

    override fun doValidate(): ValidationInfo? {
        val hasSelectedRows = selectedRows().isNotEmpty()
        if (!hasSelectedRows && !deleteSourceFilesCheckBox.isSelected) {
            return ValidationInfo("请至少选择一个新增字段，或保留“合并后提示删除源文件”。", table)
        }
        return null
    }

    fun selection(): EntityFieldMergeSelection {
        return EntityFieldMergeSelection(
            selectedRows = selectedRows(),
            deleteSourceFiles = deleteSourceFilesCheckBox.isSelected,
        )
    }

    private fun selectedRows(): List<EntityFieldMergeRow> {
        return rowStates
            .filter { state -> state.selected && state.row.status == EntityFieldMergeStatus.NEW }
            .map { state -> state.row }
    }

    private fun refreshSummary() {
        val sameCount = plan.rows.count { row -> row.status == EntityFieldMergeStatus.SAME }
        val conflictCount = plan.rows.count { row -> row.status == EntityFieldMergeStatus.CONFLICT }
        val newCount = plan.rows.count { row -> row.status == EntityFieldMergeStatus.NEW }
        val selectedCount = selectedRows().size
        summaryLabel.text = "新增 $newCount / 相同 $sameCount / 冲突 $conflictCount，当前将合并 $selectedCount 个字段。"
    }
}

private data class EntityFieldMergeRowState(
    val row: EntityFieldMergeRow,
    var selected: Boolean,
)

private class EntityFieldMergeTableModel(
    private val rowStates: List<EntityFieldMergeRowState>,
    private val hideSameFields: () -> Boolean,
) : AbstractTableModel() {
    private val columns = listOf("合并", "状态", "字段", "类型", "来源", "目标字段")

    override fun getColumnName(column: Int): String {
        return columns[column]
    }

    override fun getColumnCount(): Int {
        return columns.size
    }

    override fun getRowCount(): Int {
        return visibleRows().size
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        if (columnIndex == 0) {
            return Boolean::class.javaObjectType
        }
        return String::class.java
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        if (columnIndex != 0) {
            return false
        }
        return visibleRows()[rowIndex].row.status == EntityFieldMergeStatus.NEW
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val state = visibleRows()[rowIndex]
        val row = state.row
        return when (columnIndex) {
            0 -> state.selected && row.status == EntityFieldMergeStatus.NEW
            1 -> row.status.label
            2 -> row.sourceField.name
            3 -> row.sourceField.typeText
            4 -> row.sourceField.sourceClassName
            5 -> row.sameTargetField?.let { targetField -> "相同：${targetField.name}: ${targetField.typeText}" }
                ?: row.nameMatchedTargetField?.let { targetField -> "同名不同类型：${targetField.name}: ${targetField.typeText}" }
                ?: ""

            else -> ""
        }
    }

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        if (columnIndex != 0) {
            return
        }
        val state = visibleRows()[rowIndex]
        if (state.row.status != EntityFieldMergeStatus.NEW) {
            return
        }
        state.selected = value == true
        fireTableRowsUpdated(rowIndex, rowIndex)
    }

    private fun visibleRows(): List<EntityFieldMergeRowState> {
        if (!hideSameFields()) {
            return rowStates
        }
        return rowStates.filterNot { state -> state.row.status == EntityFieldMergeStatus.SAME }
    }

    private val EntityFieldMergeStatus.label: String
        get() = when (this) {
            EntityFieldMergeStatus.NEW -> "新增"
            EntityFieldMergeStatus.SAME -> "相同"
            EntityFieldMergeStatus.CONFLICT -> "冲突"
        }
}

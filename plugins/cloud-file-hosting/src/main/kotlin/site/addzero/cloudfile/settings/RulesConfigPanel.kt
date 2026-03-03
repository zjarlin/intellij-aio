package site.addzero.cloudfile.settings

import java.awt.GridLayout
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Panel for configuring hosting rules (global or project-specific)
 */
class RulesConfigPanel(private val ruleType: String) : JPanel(BorderLayout()) {

    private val tableModel = DefaultTableModel(arrayOf("Pattern", "Type", "Enabled"), 0)
    private val table = JBTable(tableModel)

    init {
        table.selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION

        // Type column as combo box
        val typeColumn = table.columnModel.getColumn(1)
        typeColumn.cellEditor = DefaultCellEditor(JComboBox(arrayOf("FILE", "DIRECTORY", "GLOB")))

        // Enabled column as checkbox
        val enabledColumn = table.columnModel.getColumn(2)
        enabledColumn.cellEditor = DefaultCellEditor(JCheckBox())

        val scrollPane = JScrollPane(table)
        add(scrollPane, BorderLayout.CENTER)

        // Button panel
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val addButton = JButton("Add")
        val removeButton = JButton("Remove")

        addButton.addActionListener { addRule() }
        removeButton.addActionListener { removeSelectedRules() }

        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)
        add(buttonPanel, BorderLayout.SOUTH)
    }

    private fun addRule() {
        tableModel.addRow(arrayOf<Any>("", "FILE", true))
    }

    private fun removeSelectedRules() {
        val selectedRows = table.selectedRows.sortedDescending()
        selectedRows.forEach { tableModel.removeRow(it) }
    }

    fun isModified(currentRules: MutableList<CloudFileSettings.HostingRule>): Boolean {
        if (tableModel.rowCount != currentRules.size) return true

        for (i in 0 until tableModel.rowCount) {
            val pattern = tableModel.getValueAt(i, 0) as String
            val type = tableModel.getValueAt(i, 1) as String
            val enabled = tableModel.getValueAt(i, 2) as Boolean

            val currentRule = currentRules.getOrNull(i)
            if (currentRule == null ||
                currentRule.pattern != pattern ||
                currentRule.type.name != type ||
                currentRule.enabled != enabled
            ) {
                return true
            }
        }
        return false
    }

    fun applyTo(rules: MutableList<CloudFileSettings.HostingRule>) {
        rules.clear()
        for (i in 0 until tableModel.rowCount) {
            val pattern = tableModel.getValueAt(i, 0) as String
            val type = CloudFileSettings.HostingRule.RuleType.valueOf(tableModel.getValueAt(i, 1) as String)
            val enabled = tableModel.getValueAt(i, 2) as Boolean

            rules.add(CloudFileSettings.HostingRule(pattern, type, enabled))
        }
    }

    fun resetFrom(rules: List<CloudFileSettings.HostingRule>) {
        tableModel.rowCount = 0
        rules.forEach { rule ->
            tableModel.addRow(arrayOf<Any>(rule.pattern, rule.type.name, rule.enabled))
        }
    }
}

/**
 * Panel for configuring custom rules (based on Git author and project name)
 */
class CustomRulesConfigPanel : JPanel(BorderLayout()) {

    private val listModel = DefaultListModel<String>()
    private val rulesList = JList(listModel)
    private val customRules = mutableListOf<CloudFileSettings.CustomHostingRule>()

    init {
        val scrollPane = JScrollPane(rulesList)
        add(scrollPane, BorderLayout.CENTER)

        // Button panel
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val addButton = JButton("Add Rule")
        val editButton = JButton("Edit")
        val removeButton = JButton("Remove")

        addButton.addActionListener { addCustomRule() }
        editButton.addActionListener { editSelectedRule() }
        removeButton.addActionListener { removeSelectedRule() }

        buttonPanel.add(addButton)
        buttonPanel.add(editButton)
        buttonPanel.add(removeButton)
        add(buttonPanel, BorderLayout.SOUTH)

        // Info label
        val infoLabel = JLabel("Custom rules apply based on Git author and project name patterns")
        add(infoLabel, BorderLayout.NORTH)
    }

    private fun addCustomRule() {
        val dialog = CustomRuleDialog(null)
        if (dialog.showAndGet()) {
            val rule = dialog.getRule()
            customRules.add(rule)
            refreshList()
        }
    }

    private fun editSelectedRule() {
        val index = rulesList.selectedIndex
        if (index >= 0) {
            val rule = customRules[index]
            val dialog = CustomRuleDialog(rule)
            if (dialog.showAndGet()) {
                customRules[index] = dialog.getRule()
                refreshList()
            }
        }
    }

    private fun removeSelectedRule() {
        val index = rulesList.selectedIndex
        if (index >= 0) {
            customRules.removeAt(index)
            refreshList()
        }
    }

    private fun refreshList() {
        listModel.clear()
        customRules.forEach { rule ->
            val display = buildString {
                append("Author: '${rule.gitAuthorPattern}'")
                append(" | Project: '${rule.projectNamePattern}'")
                append(" | Rules: ${rule.rules.size}")
            }
            listModel.addElement(display)
        }
    }

    fun isModified(currentRules: MutableList<CloudFileSettings.CustomHostingRule>): Boolean {
        if (customRules.size != currentRules.size) return true

        return customRules.zip(currentRules).any { (new, current) ->
            new.gitAuthorPattern != current.gitAuthorPattern ||
            new.projectNamePattern != current.projectNamePattern ||
            new.priority != current.priority ||
            new.enabled != current.enabled ||
            new.rules.size != current.rules.size
        }
    }

    fun applyTo(rules: MutableList<CloudFileSettings.CustomHostingRule>) {
        rules.clear()
        rules.addAll(customRules)
    }

    fun resetFrom(rules: List<CloudFileSettings.CustomHostingRule>) {
        customRules.clear()
        customRules.addAll(rules.map { it.copy(rules = it.rules.toMutableList()) })
        refreshList()
    }
}

/**
 * Dialog for editing a custom rule
 */
class CustomRuleDialog(private val existingRule: CloudFileSettings.CustomHostingRule?) : com.intellij.openapi.ui.DialogWrapper(true) {

    private val authorField = com.intellij.ui.components.JBTextField()
    private val projectField = com.intellij.ui.components.JBTextField()
    private val prioritySpinner = JSpinner(SpinnerNumberModel(0, 0, 100, 1))
    private val enabledCheck = JCheckBox("Enabled", true)

    init {
        title = "Custom Hosting Rule"
        init()

        existingRule?.let { rule ->
            authorField.text = rule.gitAuthorPattern
            projectField.text = rule.projectNamePattern
            prioritySpinner.value = rule.priority
            enabledCheck.isSelected = rule.enabled
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridLayout(0, 2, 5, 5))
        panel.preferredSize = java.awt.Dimension(400, 150)

        panel.add(JLabel("Git Author Pattern:"))
        panel.add(authorField)
        panel.add(JLabel("Project Name Pattern:"))
        panel.add(projectField)
        panel.add(JLabel("Priority (higher first):"))
        panel.add(prioritySpinner)
        panel.add(enabledCheck)
        panel.add(JPanel())

        // Info labels
        panel.add(JLabel("(Leave blank to match any)"))
        panel.add(JLabel())

        return panel
    }

    fun getRule(): CloudFileSettings.CustomHostingRule {
        return CloudFileSettings.CustomHostingRule(
            gitAuthorPattern = authorField.text,
            projectNamePattern = projectField.text,
            priority = prioritySpinner.value as Int,
            enabled = enabledCheck.isSelected
        )
    }
}

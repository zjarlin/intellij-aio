package site.addzero.vibetask.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * 任务视图规则设置界面
 */
class TaskViewRulesConfigurable : Configurable {

    private var mainPanel: JPanel? = null
    private val tableModel = DefaultTableModel()
    private var rulesTable: JBTable? = null
    private val settings = TaskViewSettings.getInstance()

    override fun getDisplayName(): String = "Vibe Task 视图规则"

    override fun getPreferredFocusedComponent(): JComponent? = rulesTable

    override fun createComponent(): JComponent {
        mainPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)

            // 说明文本
            add(JTextArea("""
                配置任务视图的识别规则。系统会根据这些规则自动将模块分组到不同的视图中。

                内置视图：
                • 🌍 全局备忘 - 不关联任何项目的任务
                • 📁 项目级任务 - 不关联特定模块的项目任务
                • ✨ 全部任务 - 所有任务

                自定义规则：按路径匹配自动分组模块
            """.trimIndent()).apply {
                isEditable = false
                isOpaque = false
                lineWrap = true
                wrapStyleWord = true
                font = UIUtil.getLabelFont()
                foreground = JBColor.namedColor("Label.foreground", UIUtil.getLabelForeground())
                border = JBUI.Borders.emptyBottom(12)
            }, BorderLayout.NORTH)

            // 表格
            tableModel.apply {
                addColumn("图标")
                addColumn("名称")
                addColumn("规则类型")
                addColumn("匹配模式")
                addColumn("启用")
            }

            rulesTable = JBTable(tableModel).apply {
                setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
                setShowGrid(false)
                rowHeight = 28

                // 设置列宽
                columnModel.getColumn(0).preferredWidth = 50
                columnModel.getColumn(1).preferredWidth = 150
                columnModel.getColumn(2).preferredWidth = 80
                columnModel.getColumn(3).preferredWidth = 200
                columnModel.getColumn(4).preferredWidth = 50

                // 居中渲染
                setDefaultRenderer(Any::class.java, object : DefaultTableCellRenderer() {
                    init {
                        horizontalAlignment = SwingConstants.CENTER
                    }
                })
            }

            add(JBScrollPane(rulesTable), BorderLayout.CENTER)

            // 按钮面板
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                border = JBUI.Borders.emptyTop(8)

                add(JButton("添加规则").apply {
                    addActionListener { addRule() }
                })

                add(JButton("编辑").apply {
                    addActionListener { editRule() }
                })

                add(JButton("删除").apply {
                    addActionListener { deleteRule() }
                })

                add(Box.createHorizontalStrut(20))

                add(JButton("重置为默认").apply {
                    addActionListener { resetToDefault() }
                })
            }, BorderLayout.SOUTH)
        }

        refreshTable()
        return mainPanel!!
    }

    override fun isModified(): Boolean = true

    override fun apply() {
        // 保存规则
        // 这里简化处理，实际应该在编辑时即时保存
    }

    override fun reset() {
        refreshTable()
    }

    private fun refreshTable() {
        tableModel.rowCount = 0
        settings.getRules().forEach { rule ->
            tableModel.addRow(arrayOf<Any>(
                rule.icon,
                rule.name,
                rule.getTypeDisplay(),
                rule.pattern,
                rule.enabled
            ))
        }
    }

    private fun addRule() {
        val dialog = RuleEditDialog(null)
        if (dialog.showAndGet()) {
            settings.addRule(dialog.getRule())
            refreshTable()
        }
    }

    private fun editRule() {
        val selectedRow = rulesTable?.selectedRow ?: return
        // 编辑逻辑
    }

    private fun deleteRule() {
        val selectedRow = rulesTable?.selectedRow ?: return
        // 删除逻辑
    }

    private fun resetToDefault() {
        val result = JOptionPane.showConfirmDialog(
            mainPanel,
            "确定要重置为默认规则吗？这将删除所有自定义规则。",
            "确认重置",
            JOptionPane.YES_NO_OPTION
        )
        if (result == JOptionPane.YES_OPTION) {
            // 重置逻辑
        }
    }

    /**
     * 规则编辑对话框
     */
    class RuleEditDialog(private val rule: TaskViewRule?) : DialogWrapper(null) {

        private val iconField = JTextField(5)
        private val nameField = JTextField(20)
        private val typeCombo = JComboBox(TaskViewRule.RuleType.values())
        private val patternField = JTextField(20)
        private val enabledCheck = JCheckBox("启用", true)

        init {
            title = if (rule == null) "添加规则" else "编辑规则"
            init()

            rule?.let {
                iconField.text = it.icon
                nameField.text = it.name
                typeCombo.selectedItem = it.type
                patternField.text = it.pattern
                enabledCheck.isSelected = it.enabled
            }
        }

        override fun createCenterPanel(): JComponent {
            return JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = JBUI.Borders.empty(12)

                add(createFieldPanel("图标:", iconField))
                add(createFieldPanel("名称:", nameField))
                add(createFieldPanel("规则类型:", typeCombo))
                add(createFieldPanel("匹配模式:", patternField))
                add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                    isOpaque = false
                    add(enabledCheck)
                })
            }
        }

        private fun createFieldPanel(label: String, component: JComponent): JPanel {
            return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                isOpaque = false
                add(JLabel(label))
                add(component)
            }
        }

        fun getRule(): TaskViewRule {
            return TaskViewRule(
                id = rule?.id ?: java.util.UUID.randomUUID().toString(),
                name = nameField.text,
                type = typeCombo.selectedItem as TaskViewRule.RuleType,
                pattern = patternField.text,
                icon = iconField.text,
                enabled = enabledCheck.isSelected
            )
        }
    }
}
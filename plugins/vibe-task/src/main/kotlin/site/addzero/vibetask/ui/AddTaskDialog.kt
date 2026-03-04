package site.addzero.vibetask.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import site.addzero.vibetask.model.ProjectModule
import site.addzero.vibetask.model.VibeTask
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.*
import javax.swing.border.EmptyBorder

class AddTaskDialog(
    project: Project,
    private val task: VibeTask? = null,
    private val isEdit: Boolean = false,
    private val defaultGlobal: Boolean = false,
    private val defaultModule: ProjectModule? = null,
    private val availableModules: List<ProjectModule> = emptyList()
) : DialogWrapper(project) {

    private val contentField = JTextArea(4, 40)
    private val globalCheckBox = JCheckBox("全局备忘 (不关联特定项目)")
    private val priorityCombo = JComboBox(arrayOf("🔴 高优先级", "🟡 中优先级", "🟢 低优先级"))
    private val moduleCombo = JComboBox<ModuleItem>()
    private val assigneesField = JTextField(30)

    data class ModuleItem(
        val display: String,
        val module: ProjectModule? = null
    ) {
        override fun toString(): String = display
    }

    init {
        title = if (isEdit) "编辑 Vibe Task" else "添加 Vibe Task"
        init()

        // 初始化模块选择
        initModuleCombo()

        // 初始化值
        task?.let {
            contentField.text = it.content
            globalCheckBox.isSelected = it.isGlobal()
            priorityCombo.selectedIndex = when (it.priority) {
                VibeTask.Priority.HIGH -> 0
                VibeTask.Priority.MEDIUM -> 1
                VibeTask.Priority.LOW -> 2
            }
            // 设置模块选择
            if (it.modulePath.isNotBlank()) {
                val module = availableModules.find { m -> m.path == it.modulePath }
                module?.let { selectModule(it) }
            } else {
                selectNoModule()
            }
            // 设置负责人
            assigneesField.text = it.assignees.joinToString(", ")
        } ?: run {
            globalCheckBox.isSelected = defaultGlobal
            priorityCombo.selectedIndex = 1 // 默认中优先级
            defaultModule?.let { selectModule(it) }
        }
    }

    private fun initModuleCombo() {
        moduleCombo.apply {
            background = JBColor.namedColor("ComboBox.background", UIUtil.getPanelBackground())
            foreground = JBColor.namedColor("ComboBox.foreground", UIUtil.getLabelForeground())

            // 添加选项
            addItem(ModuleItem("📁 项目级（无特定模块）"))

            if (availableModules.isNotEmpty()) {
                // 按类型分组
                val plugins = availableModules.filter { it.type == ProjectModule.ModuleType.PLUGIN }
                val libs = availableModules.filter { it.type == ProjectModule.ModuleType.LIB }
                val apps = availableModules.filter { it.type == ProjectModule.ModuleType.APP }
                val others = availableModules.filter { it.type == ProjectModule.ModuleType.UNKNOWN }

                if (plugins.isNotEmpty()) {
                    addItem(ModuleItem("—— 插件 ——"))
                    plugins.forEach { addItem(ModuleItem("  ${it.getDisplayName()}", it)) }
                }
                if (libs.isNotEmpty()) {
                    addItem(ModuleItem("—— 库 ——"))
                    libs.forEach { addItem(ModuleItem("  ${it.getDisplayName()}", it)) }
                }
                if (apps.isNotEmpty()) {
                    addItem(ModuleItem("—— 应用 ——"))
                    apps.forEach { addItem(ModuleItem("  ${it.getDisplayName()}", it)) }
                }
                if (others.isNotEmpty()) {
                    addItem(ModuleItem("—— 其他 ——"))
                    others.forEach { addItem(ModuleItem("  ${it.getDisplayName()}", it)) }
                }
            }

            addActionListener {
                val selected = selectedItem as? ModuleItem
                if (selected?.module != null) {
                    // 选择模块时取消全局
                    globalCheckBox.isSelected = false
                }
            }
        }
    }

    private fun selectModule(module: ProjectModule) {
        for (i in 0 until moduleCombo.itemCount) {
            val item = moduleCombo.getItemAt(i)
            if (item.module?.path == module.path) {
                moduleCombo.selectedIndex = i
                return
            }
        }
    }

    private fun selectNoModule() {
        moduleCombo.selectedIndex = 0
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
            border = EmptyBorder(12, 12, 12, 12)
            background = JBColor.namedColor("Panel.background", UIUtil.getPanelBackground())

            // 内容输入区
            val contentPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyBottom(8)

                add(JLabel("需求描述:").apply {
                    foreground = JBColor.namedColor("Label.foreground", UIUtil.getLabelForeground())
                }, BorderLayout.NORTH)

                contentField.apply {
                    lineWrap = true
                    wrapStyleWord = true
                    font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 13f)
                    background = JBColor.namedColor("TextArea.background", UIUtil.getTextFieldBackground())
                    foreground = JBColor.namedColor("TextArea.foreground", UIUtil.getTextFieldForeground())
                    caretColor = JBColor.namedColor("TextArea.caretForeground", UIUtil.getTextFieldForeground())
                    border = JBUI.Borders.empty(8)
                }

                add(JScrollPane(contentField).apply {
                    border = BorderFactory.createLineBorder(JBColor.border())
                }, BorderLayout.CENTER)
            }

            // 模块选择区
            val modulePanel = if (availableModules.isNotEmpty()) {
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    isOpaque = false
                    border = JBUI.Borders.emptyBottom(8)

                    add(JLabel("所属模块:").apply {
                        foreground = JBColor.namedColor("Label.foreground", UIUtil.getLabelForeground())
                    })
                    add(moduleCombo)
                }
            } else null

            // 负责人输入区
            val assigneesPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                isOpaque = false
                border = JBUI.Borders.emptyBottom(8)

                add(JLabel("负责人:").apply {
                    foreground = JBColor.namedColor("Label.foreground", UIUtil.getLabelForeground())
                })

                assigneesField.apply {
                    toolTipText = "多个负责人用逗号分隔，如: zhangsan, lisi"
                    background = JBColor.namedColor("TextField.background", UIUtil.getTextFieldBackground())
                    foreground = JBColor.namedColor("TextField.foreground", UIUtil.getTextFieldForeground())
                }
                add(assigneesField)

                add(JLabel("用逗号分隔多人").apply {
                    foreground = JBColor.GRAY
                    font = font.deriveFont(Font.PLAIN, 11f)
                })
            }

            // 选项区
            val optionsPanel = JPanel(BorderLayout()).apply {
                isOpaque = false

                val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    isOpaque = false

                    globalCheckBox.apply {
                        foreground = JBColor.namedColor("CheckBox.foreground", UIUtil.getLabelForeground())
                        background = JBColor.namedColor("CheckBox.background", UIUtil.getPanelBackground())
                        addActionListener {
                            if (isSelected) {
                                // 选择全局时清除模块选择
                                selectNoModule()
                            }
                        }
                    }

                    add(globalCheckBox)
                }

                val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                    isOpaque = false

                    add(JLabel("优先级:").apply {
                        foreground = JBColor.namedColor("Label.foreground", UIUtil.getLabelForeground())
                    })

                    priorityCombo.apply {
                        background = JBColor.namedColor("ComboBox.background", UIUtil.getPanelBackground())
                        foreground = JBColor.namedColor("ComboBox.foreground", UIUtil.getLabelForeground())
                    }
                    add(priorityCombo)
                }

                add(leftPanel, BorderLayout.WEST)
                add(rightPanel, BorderLayout.EAST)
            }

            val southPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                modulePanel?.let { add(it, BorderLayout.NORTH) }
                add(assigneesPanel, BorderLayout.CENTER)
                add(optionsPanel, BorderLayout.SOUTH)
            }

            add(contentPanel, BorderLayout.CENTER)
            add(southPanel, BorderLayout.SOUTH)
        }
    }

    override fun getPreferredSize(): Dimension {
        return Dimension(520, 320)
    }

    override fun doValidate(): ValidationInfo? {
        return when {
            contentField.text.isBlank() -> ValidationInfo("请输入需求描述", contentField)
            contentField.text.length > 500 -> ValidationInfo("需求描述太长了 (最多500字符)", contentField)
            else -> null
        }
    }

    fun getTaskContent(): String = contentField.text.trim()

    fun isGlobal(): Boolean = globalCheckBox.isSelected

    fun getPriority(): VibeTask.Priority = when (priorityCombo.selectedIndex) {
        0 -> VibeTask.Priority.HIGH
        1 -> VibeTask.Priority.MEDIUM
        else -> VibeTask.Priority.LOW
    }

    fun getSelectedModule(): ProjectModule? {
        val selected = moduleCombo.selectedItem as? ModuleItem
        return selected?.module
    }

    /**
     * 获取负责人列表
     */
    fun getAssignees(): List<String> {
        return assigneesField.text
            .split(",", "，")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}

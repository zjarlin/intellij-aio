package site.addzero.ide.ui.form

import site.addzero.ide.config.model.ConfigItem
import site.addzero.ide.config.model.InputType
import site.addzero.ide.config.model.TableColumn
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.concurrent.ConcurrentHashMap
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellEditor
import com.intellij.ui.components.JBScrollPane

/**
 * 动态表单构建器
 * 用于根据元数据构建配置界面的表单面板
 *
 * @param configItems 配置项元数据列表
 */
class DynamicFormBuilder(private val configItems: List<ConfigItem>) {
    
    // 存储表单组件的映射，用于验证
    private val componentMap = ConcurrentHashMap<String, JComponent>()
    
    // 存储验证状态
    private val validationState = ConcurrentHashMap<String, Boolean>()
    
    // 存储修改状态
    private var isModified = false
    
    /**
     * 根据元数据构建动态表单
     *
     * @return 构建好的表单面板
     */
    fun build(): JPanel {
        val formPanel = JPanel()
        formPanel.layout = BoxLayout(formPanel, BoxLayout.Y_AXIS)
        
        // 为每个配置项创建对应的UI组件
        configItems.forEach { item ->
            val itemPanel = createItemPanel(item)
            formPanel.add(itemPanel)
        }
        
        // 将表单包装在带滚动条的面板中
        val scrollPane = JBScrollPane(formPanel)
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER // 禁用水平滚动
        // 确保面板不会超出容器宽度
        formPanel.alignmentX = JPanel.LEFT_ALIGNMENT
        
        // 创建主面板并添加滚动面板
        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        
        return mainPanel
    }
    
    /**
     * 为单个配置项创建UI面板
     *
     * @param item 配置项元数据
     * @return 包含标签和输入组件的面板
     */
    private fun createItemPanel(item: ConfigItem): JPanel {
        val itemPanel = JPanel()
        itemPanel.layout = BorderLayout()
        itemPanel.alignmentX = JPanel.LEFT_ALIGNMENT
        // 添加适当的边距
        itemPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        
        // 对于checkbox，标签直接设置在组件上，不需要单独的标签
        if (item.inputType == InputType.CHECKBOX) {
            val checkbox = createComponentForType(item) as? JCheckBox
            // JCheckBox不支持HTML，所以直接设置文本，必填标记用星号
            checkbox?.text = if (item.required) "${item.label} *" else item.label
            // 默认值已在createComponentForType中设置
            componentMap[item.key] = checkbox ?: createComponentForType(item)
            
            // 添加描述信息（如果有的话）
            if (item.description.isNotEmpty()) {
                val containerPanel = JPanel(BorderLayout())
                containerPanel.add(checkbox, BorderLayout.WEST)
                val descriptionLabel = JLabel("<html><small>${item.description}</small></html>")
                descriptionLabel.border = BorderFactory.createEmptyBorder(2, 0, 0, 0)
                containerPanel.add(descriptionLabel, BorderLayout.SOUTH)
                itemPanel.add(containerPanel, BorderLayout.CENTER)
            } else {
                itemPanel.add(checkbox, BorderLayout.WEST)
            }
            
            // 添加验证监听器
            checkbox?.let { addValidationListener(item, it) }
            
            return itemPanel
        }
        
        // 创建标题面板，包含标签和必填标记
        val titlePanel = JPanel(BorderLayout())
        titlePanel.border = BorderFactory.createEmptyBorder(0, 0, 5, 0)
        titlePanel.alignmentX = JPanel.LEFT_ALIGNMENT
        
        // 创建标签
        val label = JLabel(item.label)
        label.horizontalAlignment = JLabel.LEFT
        
        // 如果是必填项，添加红色星号标记
        if (item.required) {
            label.text = "<html>${item.label}<span color='red'> *</span></html>"
        }
        
        titlePanel.add(label, BorderLayout.WEST)
        itemPanel.add(titlePanel, BorderLayout.NORTH)
        
        // 创建组件面板
        val componentPanel = JPanel()
        componentPanel.layout = BorderLayout()
        componentPanel.alignmentX = JPanel.LEFT_ALIGNMENT
        
        // 根据输入类型创建不同的组件
        val component = createComponentForType(item)
        componentMap[item.key] = component
        componentPanel.add(component, BorderLayout.CENTER)
        
        // 添加实时验证监听器
        addValidationListener(item, component)
        
        // 添加描述信息（如果有的话）
        if (item.description.isNotEmpty()) {
            val descriptionLabel = JLabel("<html><small>${item.description}</small></html>")
            descriptionLabel.border = BorderFactory.createEmptyBorder(2, 0, 0, 0)
            componentPanel.add(descriptionLabel, BorderLayout.SOUTH)
        }
        
        itemPanel.add(componentPanel, BorderLayout.CENTER)
        return itemPanel
    }
    
    /**
     * 为组件添加验证监听器
     */
    private fun addValidationListener(item: ConfigItem, component: JComponent) {
        when (component) {
            is JTextField -> {
                component.addFocusListener(object : FocusAdapter() {
                    override fun focusLost(e: FocusEvent?) {
                        validateField(item, component.text)
                        // 标记为已修改
                        isModified = true
                    }
                })
                
                // 添加文档监听器来实时跟踪修改
                component.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                    override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {
                        isModified = true
                    }
                    
                    override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {
                        isModified = true
                    }
                    
                    override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {
                        isModified = true
                    }
                })
            }
            
            is JTextArea -> {
                component.addFocusListener(object : FocusAdapter() {
                    override fun focusLost(e: FocusEvent?) {
                        validateField(item, component.text)
                        // 标记为已修改
                        isModified = true
                    }
                })
                
                // 添加文档监听器来实时跟踪修改
                component.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                    override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {
                        isModified = true
                    }
                    
                    override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {
                        isModified = true
                    }
                    
                    override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {
                        isModified = true
                    }
                })
            }
            
            is JPasswordField -> {
                component.addFocusListener(object : FocusAdapter() {
                    override fun focusLost(e: FocusEvent?) {
                        validateField(item, String(component.password))
                        // 标记为已修改
                        isModified = true
                    }
                })
                
                // 添加文档监听器来实时跟踪修改
                component.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                    override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {
                        isModified = true
                    }
                    
                    override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {
                        isModified = true
                    }
                    
                    override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {
                        isModified = true
                    }
                })
            }
            
            is JCheckBox -> {
                component.addActionListener {
                    validateField(item, component.isSelected.toString())
                    // 标记为已修改
                    isModified = true
                }
            }
            
            is JComboBox<*> -> {
                component.addActionListener {
                    validateField(item, component.selectedItem?.toString() ?: "")
                    // 标记为已修改
                    isModified = true
                }
            }
            
            is JBScrollPane -> {
                // 处理包装在滚动面板中的组件（如文本域）
                val viewport = component.viewport
                val viewComponent = viewport.view
                when (viewComponent) {
                    is JTextArea -> {
                        viewComponent.addFocusListener(object : FocusAdapter() {
                            override fun focusLost(e: FocusEvent?) {
                                validateField(item, viewComponent.text)
                                // 标记为已修改
                                isModified = true
                            }
                        })
                        
                        // 添加文档监听器来实时跟踪修改
                        viewComponent.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {
                                isModified = true
                            }
                            
                            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {
                                isModified = true
                            }
                            
                            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {
                                isModified = true
                            }
                        })
                    }
                    is JTable -> {
                        // 表格验证在添加/删除行时自动处理
                    }
                }
            }
            
            is JPanel -> {
                // 检查是否是表格面板
                val table = (component.getComponent(1) as? JBScrollPane)?.viewport?.view as? JTable
                table?.let {
                    // 表格验证逻辑
                }
            }
        }
    }
    
    /**
     * 验证单个字段
     */
    private fun validateField(item: ConfigItem, value: String) {
        val isValid = if (item.required) {
            value.isNotBlank()
        } else {
            true
        }
        
        validationState[item.key] = isValid
        // 可以在这里添加UI反馈，比如高亮显示错误字段
    }
    
    /**
     * 根据输入类型创建对应的组件
     *
     * @param item 配置项元数据
     * @return 对应的Swing组件
     */
    private fun createComponentForType(item: ConfigItem): JComponent {
        return when (item.inputType) {
            InputType.TEXT -> {
                val textField = JTextField()
                // 设置默认值
                if (item.defaultValue != null) {
                    textField.text = item.defaultValue.toString()
                }
                // 设置合适的列数，但限制最大宽度
                textField.columns = 40
                val preferredWidth = minOf(600, textField.preferredSize.width)
                textField.preferredSize = Dimension(preferredWidth, textField.preferredSize.height)
                textField.maximumSize = Dimension(preferredWidth, textField.preferredSize.height)
                // 确保是单行，不换行
                textField.horizontalAlignment = JTextField.LEFT
                if (item.required) {
                    // 添加必填验证逻辑
                }
                textField
            }
            
            InputType.NUMBER -> {
                val numberField = JFormattedTextField()
                // 设置默认值
                when (item.defaultValue) {
                    is Int -> numberField.value = item.defaultValue
                    is Long -> numberField.value = item.defaultValue.toInt()
                    is Number -> numberField.value = item.defaultValue.toInt()
                    else -> numberField.value = 0
                }
                numberField.columns = 20
                val preferredWidth = minOf(300, numberField.preferredSize.width)
                numberField.preferredSize = Dimension(preferredWidth, numberField.preferredSize.height)
                numberField.maximumSize = Dimension(preferredWidth, numberField.preferredSize.height)
                numberField
            }
            
            InputType.PASSWORD -> {
                val passwordField = JPasswordField()
                // 设置默认值
                if (item.defaultValue != null) {
                    passwordField.text = item.defaultValue.toString()
                }
                passwordField.columns = 40
                val preferredWidth = minOf(600, passwordField.preferredSize.width)
                passwordField.preferredSize = Dimension(preferredWidth, passwordField.preferredSize.height)
                passwordField.maximumSize = Dimension(preferredWidth, passwordField.preferredSize.height)
                passwordField
            }
            
            InputType.TEXTAREA -> {
                val textArea = JTextArea(5, 40)
                // 设置默认值
                if (item.defaultValue != null) {
                    textArea.text = item.defaultValue.toString()
                }
                textArea.lineWrap = true
                textArea.wrapStyleWord = true
                // 限制文本域的最大宽度
                val preferredWidth = minOf(600, textArea.preferredSize.width)
                textArea.preferredSize = Dimension(preferredWidth, 100)
                
                val scrollPane = JBScrollPane(textArea)
                scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER // 禁用水平滚动
                scrollPane.preferredSize = Dimension(preferredWidth, 120)
                scrollPane.maximumSize = Dimension(preferredWidth, 150)
                scrollPane
            }
            
            InputType.CHECKBOX -> {
                val checkBox = JCheckBox()
                // 设置默认值
                if (item.defaultValue is Boolean) {
                    checkBox.isSelected = item.defaultValue
                } else {
                    checkBox.isSelected = false
                }
                checkBox
            }
            
            InputType.SELECT -> {
                val comboBox = JComboBox<String>()
                item.options.forEach { option ->
                    comboBox.addItem(option.label)
                }
                // 设置默认值 - 根据默认值找到对应的选项
                if (item.defaultValue != null) {
                    val defaultValueStr = item.defaultValue.toString()
                    val matchingOption = item.options.find { it.value == defaultValueStr }
                    if (matchingOption != null) {
                        comboBox.selectedItem = matchingOption.label
                    }
                }
                val preferredWidth = minOf(600, comboBox.preferredSize.width)
                comboBox.preferredSize = Dimension(preferredWidth, comboBox.preferredSize.height)
                comboBox.maximumSize = Dimension(preferredWidth, comboBox.preferredSize.height)
                comboBox
            }
            
            InputType.TABLE -> {
                createTableComponent(item)
            }
            
            // 使用穷尽性when表达式处理所有情况
            else -> JLabel("Unsupported input type: ${item.inputType}")
        }
    }
    
    /**
     * 创建表格组件
     */
    private fun createTableComponent(item: ConfigItem): JComponent {
        // 创建表格列名
        val columnNames = item.tableColumns.map { it.label }.toTypedArray()
        
        // 创建表格模型
        val tableModel = object : DefaultTableModel(columnNames, 0) {
            override fun isCellEditable(row: Int, column: Int): Boolean = true
        }
        
        // 创建表格
        val table = JTable(tableModel)
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        
        // 设置列宽
        item.tableColumns.forEachIndexed { index, column ->
            val tableColumn = table.columnModel.getColumn(index)
            tableColumn.preferredWidth = column.width
            tableColumn.minWidth = 100
        }
        
        // 创建表格面板，包含表格、添加/删除按钮
        val panel = JPanel(BorderLayout())
        
        // 工具栏面板
        val toolbarPanel = JPanel()
        toolbarPanel.layout = BoxLayout(toolbarPanel, BoxLayout.X_AXIS)
        
        // 添加按钮
        val addButton = JButton("添加行")
        addButton.addActionListener {
            val newRow = arrayOfNulls<Any>(columnNames.size)
            tableModel.addRow(newRow)
            
            // 检查最大行数限制
            if (item.maxRows > 0 && tableModel.rowCount > item.maxRows) {
                tableModel.removeRow(tableModel.rowCount - 1)
                JOptionPane.showMessageDialog(
                    panel,
                    "已达到最大行数限制: ${item.maxRows}",
                    "提示",
                    JOptionPane.WARNING_MESSAGE
                )
            } else {
                // 标记为已修改
                isModified = true
            }
        }
        
        // 删除按钮
        val removeButton = JButton("删除行")
        removeButton.addActionListener {
            val selectedRow = table.selectedRow
            if (selectedRow >= 0) {
                // 检查最小行数限制
                if (item.minRows > 0 && tableModel.rowCount <= item.minRows) {
                    JOptionPane.showMessageDialog(
                        panel,
                        "已达到最小行数限制: ${item.minRows}",
                        "提示",
                        JOptionPane.WARNING_MESSAGE
                    )
                } else {
                    tableModel.removeRow(selectedRow)
                    // 标记为已修改
                    isModified = true
                }
            } else {
                JOptionPane.showMessageDialog(
                    panel,
                    "请先选择要删除的行",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        }
        
        toolbarPanel.add(addButton)
        toolbarPanel.add(Box.createHorizontalStrut(5))
        toolbarPanel.add(removeButton)
        toolbarPanel.add(Box.createHorizontalGlue())
        
        // 表格滚动面板
        val scrollPane = JBScrollPane(table)
        scrollPane.preferredSize = Dimension(600, 200)
        scrollPane.maximumSize = Dimension(600, 300)
        
        // 确保最小行数
        if (item.minRows > 0) {
            for (i in 0 until item.minRows) {
                val newRow = arrayOfNulls<Any>(columnNames.size)
                tableModel.addRow(newRow)
            }
        }
        
        // 添加表格模型监听器来跟踪修改
        tableModel.addTableModelListener {
            isModified = true
        }
        
        panel.add(toolbarPanel, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    /**
     * 验证整个表单
     *
     * @return 验证是否通过
     */
    fun validateForm(): Boolean {
        var isFormValid = true
        
        configItems.forEach { item ->
            val component = componentMap[item.key]
            if (component != null) {
                val isValid = validateComponent(item, component)
                validationState[item.key] = isValid
                if (!isValid) {
                    isFormValid = false
                }
            }
        }
        
        return isFormValid
    }
    
    /**
     * 验证单个组件
     */
    private fun validateComponent(item: ConfigItem, component: JComponent): Boolean {
        return when (component) {
            is JTextField -> {
                if (item.required) {
                    component.text.isNotBlank()
                } else {
                    true
                }
            }
            
            is JTextArea -> {
                if (item.required) {
                    component.text.isNotBlank()
                } else {
                    true
                }
            }
            
            is JPasswordField -> {
                if (item.required) {
                    component.password.isNotEmpty()
                } else {
                    true
                }
            }
            
            is JCheckBox -> {
                // 复选框通常不需要必填验证，除非有特殊需求
                true
            }
            
            is JComboBox<*> -> {
                if (item.required) {
                    component.selectedIndex >= 0
                } else {
                    true
                }
            }
            
            is JBScrollPane -> {
                // 处理包装在滚动面板中的组件（如文本域）
                val viewport = component.viewport
                val viewComponent = viewport.view
                if (viewComponent is JTextArea && item.required) {
                    viewComponent.text.isNotBlank()
                } else {
                    true
                }
            }
            
            is JPanel -> {
                // 检查是否是表格面板
                val scrollPane = component.getComponent(1) as? JBScrollPane
                val table = scrollPane?.viewport?.view as? JTable
                if (table != null && item.required) {
                    table.model.rowCount > 0
                } else {
                    true
                }
            }
            
            else -> true
        }
    }
    
    /**
     * 获取表单数据
     *
     * @return 配置项键值对映射
     */
    fun getFormData(): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>()
        
        configItems.forEach { item ->
            val component = componentMap[item.key]
            if (component != null) {
                val value = getComponentValue(component)
                data[item.key] = value
            }
        }
        
        return data
    }
    
    /**
     * 检查表单是否被修改
     */
    fun isModified(): Boolean {
        return isModified
    }
    
    /**
     * 设置表单修改状态
     */
    fun setModified(modified: Boolean) {
        isModified = modified
    }
    
    /**
     * 设置表单数据
     */
    fun setFormData(data: Map<String, String>) {
        configItems.forEach { item ->
            val component = componentMap[item.key]
            val value = data[item.key]
            if (component != null && value != null) {
                setComponentValue(component, value, item)
            }
        }
        // 重置修改状态
        isModified = false
    }
    
    /**
     * 设置组件的值
     */
    private fun setComponentValue(component: JComponent, value: String, item: ConfigItem) {
        when (component) {
            is JTextField -> {
                if (component.text != value) {
                    component.text = value
                }
            }
            is JTextArea -> {
                if (component.text != value) {
                    component.text = value
                }
            }
            is JCheckBox -> {
                val boolValue = value.toBoolean()
                if (component.isSelected != boolValue) {
                    component.isSelected = boolValue
                }
            }
            is JComboBox<*> -> {
                // 查找匹配的选项
                for (i in 0 until component.itemCount) {
                    val option = component.getItemAt(i)
                    val matchingOption = item.options.find { it.label == option }
                    if (matchingOption?.value == value) {
                        component.selectedIndex = i
                        break
                    }
                }
            }
            is JBScrollPane -> {
                // 处理包装在滚动面板中的组件（如文本域）
                val viewport = component.viewport
                val viewComponent = viewport.view
                if (viewComponent is JTextArea) {
                    if (viewComponent.text != value) {
                        viewComponent.text = value
                    }
                }
            }
            is JPasswordField -> {
                val password = value.toCharArray()
                if (!component.password.contentEquals(password)) {
                    component.text = value
                }
            }
            // 表格组件的处理比较复杂，这里简化处理
            is JPanel -> {
                // 表格组件暂不处理
            }
        }
    }
    
    /**
     * 获取组件的值
     */
    private fun getComponentValue(component: JComponent): Any? {
        return when (component) {
            is JTextField -> component.text
            is JTextArea -> component.text
            is JCheckBox -> component.isSelected
            is JComboBox<*> -> component.selectedItem
            is JBScrollPane -> {
                // 处理包装在滚动面板中的组件（如文本域）
                val viewport = component.viewport
                val viewComponent = viewport.view
                if (viewComponent is JTextArea) {
                    viewComponent.text
                } else {
                    null
                }
            }
            
            is JPanel -> {
                // 处理表格组件
                val scrollPane = component.getComponent(1) as? JBScrollPane
                val table = scrollPane?.viewport?.view as? JTable
                if (table != null) {
                    // 获取表格列配置 - 通过反向查找找到对应的ConfigItem
                    val tableItem = componentMap.entries.find { it.value == component }?.let { entry ->
                        configItems.find { it.key == entry.key && it.inputType == InputType.TABLE }
                    }
                    if (tableItem != null) {
                        // 返回表格数据
                        val rows = mutableListOf<Map<String, Any?>>()
                        val model = table.model
                        val columnCount = model.columnCount
                        
                        for (row in 0 until model.rowCount) {
                            val rowData = mutableMapOf<String, Any?>()
                            for (col in 0 until columnCount) {
                                // 使用列的key而不是label
                                val columnKey = tableItem.tableColumns.getOrNull(col)?.key ?: model.getColumnName(col)
                                val value = model.getValueAt(row, col)
                                rowData[columnKey] = value
                            }
                            rows.add(rowData)
                        }
                        rows
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            
            is JPasswordField -> String(component.password)
            else -> null
        }
    }
}
package site.addzero.lsi.analyzer.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import site.addzero.lsi.analyzer.metadata.FieldMetadata
import site.addzero.lsi.analyzer.metadata.PojoMetadata
import site.addzero.lsi.analyzer.service.PojoScanService
import site.addzero.lsi.analyzer.settings.PojoMetaSettings
import site.addzero.lsi.analyzer.settings.PojoMetaSettingsService
import site.addzero.lsi.analyzer.template.JteTemplateManager
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*
import javax.swing.table.DefaultTableModel

class PojoMetaToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.setIcon(AllIcons.Nodes.Class)
        val panel = PojoMetaPanel(project)
        val content = toolWindow.contentManager.factory.createContent(panel, "POJO\u5143\u6570\u636e", false)
        toolWindow.contentManager.addContent(content)
    }
}

class PojoMetaPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val pojoTableModel = DefaultTableModel(arrayOf("\u7c7b\u540d", "\u5305\u540d", "\u5b57\u6bb5\u6570", "\u8868\u540d", "\u6ce8\u91ca"), 0)
    private val pojoTable = JBTable(pojoTableModel)
    private val fieldTableModel = DefaultTableModel(arrayOf("\u5b57\u6bb5\u540d", "\u7c7b\u578b", "\u5217\u540d", "\u6ce8\u91ca", "\u4e3b\u952e", "\u53ef\u7a7a"), 0)
    private val fieldTable = JBTable(fieldTableModel)
    private val templateEditor = JBTextArea()
    private val templateList = DefaultListModel<String>()
    private val scanService = PojoScanService.getInstance(project)
    private val statusLabel = JLabel("\u5c31\u7eea")

    private var currentMetadataList: List<PojoMetadata> = emptyList()

    init {
        setupUI()
        setupListeners()
        loadFromCache()
    }

    private fun setupUI() {
        val toolbar = createToolbar()
        add(toolbar, BorderLayout.NORTH)

        val tabbedPane = JBTabbedPane()
        
        val pojoPanel = JPanel(BorderLayout()).apply {
            val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
                topComponent = JBScrollPane(pojoTable)
                bottomComponent = JBScrollPane(fieldTable)
                dividerLocation = 300
            }
            add(splitPane, BorderLayout.CENTER)
        }
        tabbedPane.addTab("POJO\u5217\u8868", pojoPanel)
        
        val templatePanel = createTemplatePanel()
        tabbedPane.addTab("\u6a21\u677f\u914d\u7f6e", templatePanel)
        
        val settingsPanel = createSettingsPanel()
        tabbedPane.addTab("\u8bbe\u7f6e", settingsPanel)

        add(tabbedPane, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
    }

    private fun createToolbar(): JToolBar {
        return JToolBar().apply {
            isFloatable = false
            
            add(JButton("\u626b\u63cf", AllIcons.Actions.Refresh).apply {
                addActionListener { scanPojos() }
            })
            
            add(JButton("\u5bfc\u51fa", AllIcons.Actions.Download).apply {
                addActionListener { exportMetadata() }
            })
            
            add(JButton("\u751f\u6210Kotlin", AllIcons.FileTypes.Kotlin).apply {
                addActionListener { generateKotlin() }
            })
            
            addSeparator()
            
            add(JButton("\u5e94\u7528\u6a21\u677f", AllIcons.Actions.Execute).apply {
                addActionListener { applyTemplate() }
            })
        }
    }

    private fun createTemplatePanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            val listPanel = JPanel(BorderLayout()).apply {
                JteTemplateManager.getAvailableTemplates().keys.forEach { templateList.addElement(it) }
                val list = JList(templateList)
                list.addListSelectionListener { e ->
                    if (!e.valueIsAdjusting) {
                        val selected = list.selectedValue
                        if (selected != null) {
                            templateEditor.text = JteTemplateManager.getAvailableTemplates()[selected] ?: ""
                        }
                    }
                }
                add(JBScrollPane(list), BorderLayout.CENTER)
                
                val btnPanel = JPanel().apply {
                    add(JButton("\u65b0\u5efa").apply {
                        addActionListener {
                            val name = Messages.showInputDialog(project, "\u6a21\u677f\u540d\u79f0:", "\u65b0\u5efa\u6a21\u677f", null)
                            if (!name.isNullOrBlank()) {
                                templateList.addElement(name)
                                JteTemplateManager.saveTemplate(name, PojoMetaSettings.DEFAULT_TEMPLATE)
                            }
                        }
                    })
                    add(JButton("\u5220\u9664").apply {
                        addActionListener {
                            val selected = (parent.parent as JPanel).components
                                .filterIsInstance<JBScrollPane>()
                                .firstOrNull()
                                ?.viewport?.view as? JList<*>
                            selected?.selectedValue?.toString()?.let { name ->
                                if (name != "default") {
                                    templateList.removeElement(name)
                                    JteTemplateManager.deleteTemplate(name)
                                }
                            }
                        }
                    })
                }
                add(btnPanel, BorderLayout.SOUTH)
                preferredSize = java.awt.Dimension(150, 0)
            }
            
            val editorPanel = JPanel(BorderLayout()).apply {
                templateEditor.font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
                add(JBScrollPane(templateEditor), BorderLayout.CENTER)
                add(JButton("\u4fdd\u5b58\u6a21\u677f").apply {
                    addActionListener {
                        val list = listPanel.components.filterIsInstance<JBScrollPane>()
                            .firstOrNull()?.viewport?.view as? JList<*>
                        list?.selectedValue?.toString()?.let { name ->
                            JteTemplateManager.saveTemplate(name, templateEditor.text)
                            Messages.showInfoMessage("\u6a21\u677f\u5df2\u4fdd\u5b58", "\u63d0\u793a")
                        }
                    }
                }, BorderLayout.SOUTH)
            }
            
            add(listPanel, BorderLayout.WEST)
            add(editorPanel, BorderLayout.CENTER)
        }
    }

    private fun createSettingsPanel(): JPanel {
        val settings = PojoMetaSettingsService.getInstance().state
        
        return JPanel(GridBagLayout()).apply {
            val gbc = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(5, 5, 5, 5)
                anchor = GridBagConstraints.WEST
            }
            
            gbc.gridx = 0; gbc.gridy = 0
            add(JLabel("\u626b\u63cf\u95f4\u9694(\u5206\u949f):"), gbc)
            gbc.gridx = 1
            val intervalSpinner = JSpinner(SpinnerNumberModel(settings.scanIntervalMinutes, 1, 60, 1))
            add(intervalSpinner, gbc)
            
            gbc.gridx = 0; gbc.gridy = 1
            add(JLabel("\u542f\u52a8\u65f6\u81ea\u52a8\u626b\u63cf:"), gbc)
            gbc.gridx = 1
            val autoScanCheck = JCheckBox().apply { isSelected = settings.autoScanOnStartup }
            add(autoScanCheck, gbc)
            
            gbc.gridx = 0; gbc.gridy = 2
            add(JLabel("\u751f\u6210Kotlin\u6570\u636e\u7c7b:"), gbc)
            gbc.gridx = 1
            val genKotlinCheck = JCheckBox().apply { isSelected = settings.generateKotlinDataClass }
            add(genKotlinCheck, gbc)
            
            gbc.gridx = 0; gbc.gridy = 3
            add(JLabel("\u5bfc\u51fa\u8def\u5f84:"), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            val exportPathField = JTextField(settings.metadataExportPath)
            add(exportPathField, gbc)
            
            gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.weightx = 0.0
            add(JButton("\u4fdd\u5b58\u8bbe\u7f6e").apply {
                addActionListener {
                    settings.scanIntervalMinutes = intervalSpinner.value as Int
                    settings.autoScanOnStartup = autoScanCheck.isSelected
                    settings.generateKotlinDataClass = genKotlinCheck.isSelected
                    settings.metadataExportPath = exportPathField.text
                    
                    if (autoScanCheck.isSelected) {
                        scanService.startScheduledScan(settings.scanIntervalMinutes)
                    } else {
                        scanService.stopScheduledScan()
                    }
                    
                    Messages.showInfoMessage("\u8bbe\u7f6e\u5df2\u4fdd\u5b58", "\u63d0\u793a")
                }
            }, gbc)
            
            gbc.gridy = 5; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
            add(JPanel(), gbc)
        }
    }

    private fun setupListeners() {
        pojoTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting && pojoTable.selectedRow >= 0) {
                showFieldsForSelectedPojo()
            }
        }

        scanService.addScanListener { metadata ->
            SwingUtilities.invokeLater {
                updateTable(metadata)
            }
        }
    }

    private fun loadFromCache() {
        val projectPath = project.basePath ?: return
        val cached = site.addzero.lsi.analyzer.cache.MetadataCacheManager.loadPojoMetadata(projectPath)
        if (!cached.isNullOrEmpty()) {
            currentMetadataList = cached
            updateTable(cached)
        }
    }

    private fun scanPojos() {
        statusLabel.text = "\u626b\u63cf\u4e2d..."
        scanService.scanNowAsync { metadata ->
            SwingUtilities.invokeLater {
                statusLabel.text = "\u626b\u63cf\u5b8c\u6210\uff0c\u5171 ${metadata.size} \u4e2a POJO"
            }
        }
    }

    private fun updateTable(metadata: List<PojoMetadata>) {
        currentMetadataList = metadata
        pojoTableModel.rowCount = 0
        metadata.forEach { pojo ->
            pojoTableModel.addRow(arrayOf(
                pojo.className,
                pojo.packageName,
                pojo.fields.size,
                pojo.tableName ?: "-",
                pojo.comment ?: "-"
            ))
        }
    }

    private fun showFieldsForSelectedPojo() {
        val row = pojoTable.selectedRow
        if (row < 0 || row >= currentMetadataList.size) return
        
        val pojo = currentMetadataList[row]
        fieldTableModel.rowCount = 0
        pojo.fields.forEach { field ->
            fieldTableModel.addRow(arrayOf(
                field.name,
                field.typeName,
                field.columnName ?: "-",
                field.comment ?: "-",
                if (field.isPrimaryKey) "\u2713" else "",
                if (field.nullable) "\u2713" else ""
            ))
        }
    }

    private fun exportMetadata() {
        statusLabel.text = "\u5bfc\u51fa\u4e2d..."
        val exportDir = scanService.exportMetadata()
        if (exportDir != null) {
            statusLabel.text = "\u5df2\u5bfc\u51fa\u5230: ${exportDir.absolutePath}"
            Messages.showInfoMessage("\u5143\u6570\u636e\u5df2\u5bfc\u51fa\u5230:\n${exportDir.absolutePath}", "\u5bfc\u51fa\u6210\u529f")
        } else {
            statusLabel.text = "\u5bfc\u51fa\u5931\u8d25"
        }
    }

    private fun generateKotlin() {
        statusLabel.text = "\u751f\u6210Kotlin\u6570\u636e\u7c7b..."
        val genDir = scanService.generateKotlinDataClasses()
        if (genDir != null) {
            statusLabel.text = "\u5df2\u751f\u6210\u5230: ${genDir.absolutePath}"
            Messages.showInfoMessage(
                "\u5df2\u751f\u6210Kotlin\u6570\u636e\u7c7b\u5230:\n${genDir.absolutePath}\n\nIDE\u5c06\u81ea\u52a8\u7d22\u5f15\u8be5\u76ee\u5f55",
                "\u751f\u6210\u6210\u529f"
            )
        } else {
            statusLabel.text = "\u751f\u6210\u5931\u8d25\uff0c\u8bf7\u5148\u626b\u63cf"
        }
    }

    private fun applyTemplate() {
        val row = pojoTable.selectedRow
        if (row < 0) {
            Messages.showWarningDialog("\u8bf7\u5148\u9009\u62e9\u4e00\u4e2aPOJO", "\u63d0\u793a")
            return
        }
        
        val templateNames = JteTemplateManager.getAvailableTemplates().keys.toTypedArray()
        val selected = Messages.showEditableChooseDialog(
            "\u9009\u62e9\u6a21\u677f:",
            "\u5e94\u7528\u6a21\u677f",
            null,
            templateNames,
            templateNames.firstOrNull() ?: "",
            null
        ) ?: return
        
        val pojo = currentMetadataList[row]
        val templateContent = JteTemplateManager.getAvailableTemplates()[selected] ?: return
        
        try {
            val manager = JteTemplateManager.createFromSettings()
            val result = manager.renderWithString(templateContent, pojo)
            
            val dialog = JDialog().apply {
                title = "\u751f\u6210\u7ed3\u679c - ${pojo.className}"
                contentPane = JBScrollPane(JBTextArea(result).apply {
                    isEditable = false
                    font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
                })
                setSize(600, 400)
                setLocationRelativeTo(null)
                isVisible = true
            }
        } catch (e: Exception) {
            Messages.showErrorDialog("\u6a21\u677f\u6e32\u67d3\u5931\u8d25: ${e.message}", "\u9519\u8bef")
        }
    }
}

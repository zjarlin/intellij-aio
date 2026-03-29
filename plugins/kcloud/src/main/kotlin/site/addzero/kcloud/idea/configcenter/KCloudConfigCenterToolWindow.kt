package site.addzero.kcloud.idea.configcenter

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel

class KCloudConfigCenterToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.setIcon(AllIcons.General.Settings)
        val panel = KCloudConfigCenterPanel(project)
        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class KCloudConfigCenterPanel(
    private val project: Project,
) : JPanel(BorderLayout()) {
    private val service = KCloudConfigCenterService.getInstance()
    private val projectSettings = KCloudConfigCenterProjectSettings.getInstance(project)

    private val keywordField = JBTextField()
    private val namespaceFilterField = JBTextField(projectSettings.resolvedNamespace())
    private val profileFilterField = JBTextField(projectSettings.resolvedProfile())
    private val domainFilterCombo = ComboBox(arrayOf("ALL") + KCloudConfigDomain.entries.map { it.name }.toTypedArray())

    private val entryTableModel = object : DefaultTableModel(
        arrayOf("Key", "项目", "Profile", "类型", "值"),
        0,
    ) {
        override fun isCellEditable(row: Int, column: Int): Boolean {
            return false
        }
    }
    private val entryTable = JBTable(entryTableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    }
    private var entries: List<KCloudConfigEntry> = emptyList()

    private var selectedEntryId: String? = null

    private val keyField = JBTextField()
    private val namespaceField = JBTextField(projectSettings.resolvedNamespace())
    private val profileField = JBTextField(projectSettings.resolvedProfile())
    private val domainCombo = ComboBox(KCloudConfigDomain.entries.toTypedArray())
    private val valueTypeCombo = ComboBox(KCloudConfigValueType.entries.toTypedArray())
    private val storageModeCombo = ComboBox(KCloudConfigStorageMode.entries.toTypedArray())
    private val enabledCheckBox = JBCheckBox("启用", true)
    private val descriptionField = JBTextField()
    private val valueArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 8
    }

    private val statusLabel = JBLabel()
    private val dbPathLabel = JBLabel()

    init {
        add(buildToolbar(), BorderLayout.NORTH)
        add(buildBody(), BorderLayout.CENTER)
        add(buildStatusBar(), BorderLayout.SOUTH)
        installListeners()
        beginCreate()
        refreshEntries()
    }

    private fun buildToolbar(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 8, 8)).apply {
            add(JBLabel("关键词"))
            keywordField.columns = 12
            add(keywordField)

            add(JBLabel("项目/命名空间"))
            namespaceFilterField.columns = 14
            add(namespaceFilterField)

            add(JBLabel("Profile"))
            profileFilterField.columns = 10
            add(profileFilterField)

            add(JBLabel("分类"))
            add(domainFilterCombo)

            add(JButton("刷新", AllIcons.Actions.Refresh).apply {
                addActionListener { refreshEntries() }
            })
            add(JButton("新建", AllIcons.General.Add).apply {
                addActionListener { beginCreate() }
            })
            add(JButton("保存", AllIcons.Actions.MenuSaveall).apply {
                addActionListener { saveCurrentEntry() }
            })
            add(JButton("删除", AllIcons.General.Remove).apply {
                addActionListener { deleteCurrentEntry() }
            })
        }
    }

    private fun buildBody(): JBSplitter {
        val leftPanel = JPanel(BorderLayout()).apply {
            add(JBScrollPane(entryTable), BorderLayout.CENTER)
        }
        val rightPanel = JPanel(BorderLayout()).apply {
            add(buildEditorForm(), BorderLayout.CENTER)
        }
        return JBSplitter(false, 0.45f).apply {
            firstComponent = leftPanel
            secondComponent = rightPanel
        }
    }

    private fun buildEditorForm(): JPanel {
        val form = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
        }

        fun row(label: String, component: java.awt.Component): JPanel {
            return JPanel(BorderLayout(8, 0)).apply {
                add(JBLabel(label), BorderLayout.WEST)
                add(component, BorderLayout.CENTER)
                maximumSize = java.awt.Dimension(Int.MAX_VALUE, preferredSize.height)
            }
        }

        form.add(row("Key", keyField))
        form.add(row("项目/命名空间", namespaceField))
        form.add(row("Profile", profileField))
        form.add(row("分类", domainCombo))
        form.add(row("值类型", valueTypeCombo))
        form.add(row("存储方式", storageModeCombo))
        form.add(row("描述", descriptionField))
        form.add(row("状态", enabledCheckBox))
        form.add(row("Value", JBScrollPane(valueArea)))
        return JPanel(BorderLayout()).apply {
            add(form, BorderLayout.CENTER)
        }
    }

    private fun buildStatusBar(): JPanel {
        dbPathLabel.text = "DB: ${service.describeDatabase(project)}"
        statusLabel.text = "就绪"
        return JPanel(BorderLayout(8, 0)).apply {
            border = javax.swing.BorderFactory.createEmptyBorder(6, 8, 6, 8)
            add(dbPathLabel, BorderLayout.WEST)
            add(statusLabel, BorderLayout.EAST)
        }
    }

    private fun installListeners() {
        entryTable.selectionModel.addListSelectionListener { event ->
            if (event.valueIsAdjusting) {
                return@addListSelectionListener
            }
            val row = entryTable.selectedRow
            if (row < 0) {
                return@addListSelectionListener
            }
            val modelRow = entryTable.convertRowIndexToModel(row)
            entries.getOrNull(modelRow)?.let { entry ->
                loadEntry(entry)
            }
        }
    }

    fun refreshEntries() {
        runCatching {
            val domain = domainFilterCombo.selectedItem
                ?.toString()
                ?.takeIf { it != "ALL" }
                ?.let(KCloudConfigDomain::valueOf)
            service.listEntries(
                project = project,
                query = KCloudConfigQuery(
                    namespace = namespaceFilterField.text.trim().ifBlank { null },
                    profile = profileFilterField.text.trim().ifBlank { "default" },
                    domain = domain,
                    keyword = keywordField.text.trim().ifBlank { null },
                    includeDisabled = true,
                ),
            )
        }.onSuccess { loaded ->
            entries = loaded
            entryTableModel.setRowCount(0)
            loaded.forEach { entry ->
                entryTableModel.addRow(
                    arrayOf(
                        entry.key,
                        entry.namespace,
                        entry.profile,
                        entry.valueType.name,
                        entry.value?.take(80).orEmpty(),
                    ),
                )
            }
            dbPathLabel.text = "DB: ${service.describeDatabase(project)}"
            statusLabel.text = "共 ${loaded.size} 条"
        }.onFailure { error ->
            statusLabel.text = error.message ?: "刷新失败"
            KCloudConfigCenterNotification.error(project, statusLabel.text)
        }
    }

    private fun beginCreate() {
        selectedEntryId = null
        keyField.text = ""
        namespaceField.text = projectSettings.resolvedNamespace()
        profileField.text = projectSettings.resolvedProfile()
        domainCombo.selectedItem = KCloudConfigDomain.SYSTEM
        valueTypeCombo.selectedItem = KCloudConfigValueType.STRING
        storageModeCombo.selectedItem = KCloudConfigStorageMode.REPO_PLAIN
        enabledCheckBox.isSelected = true
        descriptionField.text = ""
        valueArea.text = ""
        entryTable.clearSelection()
        statusLabel.text = "新建配置项"
    }

    private fun loadEntry(entry: KCloudConfigEntry) {
        selectedEntryId = entry.id
        keyField.text = entry.key
        namespaceField.text = entry.namespace
        profileField.text = entry.profile
        domainCombo.selectedItem = entry.domain
        valueTypeCombo.selectedItem = entry.valueType
        storageModeCombo.selectedItem = entry.storageMode
        enabledCheckBox.isSelected = entry.enabled
        descriptionField.text = entry.description.orEmpty()
        valueArea.text = entry.value.orEmpty()
        statusLabel.text = if (entry.decryptionAvailable) {
            "已载入 ${entry.key}"
        } else {
            "已载入 ${entry.key}，但当前缺少解密主密钥"
        }
    }

    private fun saveCurrentEntry() {
        runCatching {
            service.saveEntry(
                project = project,
                mutation = KCloudConfigMutation(
                    id = selectedEntryId,
                    key = keyField.text.trim(),
                    namespace = namespaceField.text.trim(),
                    profile = profileField.text.trim().ifBlank { "default" },
                    domain = domainCombo.selectedItem as? KCloudConfigDomain ?: KCloudConfigDomain.SYSTEM,
                    valueType = valueTypeCombo.selectedItem as? KCloudConfigValueType ?: KCloudConfigValueType.STRING,
                    storageMode = storageModeCombo.selectedItem as? KCloudConfigStorageMode ?: KCloudConfigStorageMode.REPO_PLAIN,
                    value = valueArea.text,
                    description = descriptionField.text.trim().ifBlank { null },
                    enabled = enabledCheckBox.isSelected,
                ),
            )
        }.onSuccess { saved ->
            statusLabel.text = "已保存 ${saved.key}"
            refreshEntries()
            selectEntryById(saved.id)
            KCloudConfigCenterNotification.info(project, "已保存配置项 ${saved.key}")
        }.onFailure { error ->
            statusLabel.text = error.message ?: "保存失败"
            KCloudConfigCenterNotification.error(project, statusLabel.text)
        }
    }

    private fun deleteCurrentEntry() {
        val entryId = selectedEntryId ?: return
        val result = Messages.showYesNoDialog(
            project,
            "确定删除当前配置项吗？",
            "删除配置项",
            Messages.getQuestionIcon(),
        )
        if (result != Messages.YES) {
            return
        }
        runCatching {
            service.deleteEntry(project, entryId)
        }.onSuccess {
            beginCreate()
            refreshEntries()
            KCloudConfigCenterNotification.info(project, "已删除配置项")
        }.onFailure { error ->
            statusLabel.text = error.message ?: "删除失败"
            KCloudConfigCenterNotification.error(project, statusLabel.text)
        }
    }

    private fun selectEntryById(entryId: String) {
        val index = entries.indexOfFirst { it.id == entryId }
        if (index < 0) {
            return
        }
        entryTable.selectionModel.setSelectionInterval(index, index)
        entryTable.scrollRectToVisible(entryTable.getCellRect(index, 0, true))
    }
}

package site.addzero.lsi.analyzer.config

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JPanel

/**
 * DDL 设置配置界面
 */
class DdlSettingsConfigurable : Configurable {

    private val ddlSaveDirectoryField = TextFieldWithBrowseButton()
    private val autoSaveDdlCheckBox = JBCheckBox("自动保存 DDL 到文件")
    private val openFileAfterGenerationCheckBox = JBCheckBox("生成后打开文件")
    private val ddlFileNameTemplateField = JBTextField()

    private var settings: DdlSettings = DdlSettings()

    override fun getDisplayName(): String = "LSI Code Analyzer - DDL Settings"

    override fun createComponent(): JComponent {
        // 设置目录选择器
        ddlSaveDirectoryField.addActionListener {
            val chooser = JFileChooser()
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            chooser.dialogTitle = "选择 DDL 保存目录"

            if (ddlSaveDirectoryField.text.isNotBlank()) {
                chooser.currentDirectory = java.io.File(ddlSaveDirectoryField.text)
            }

            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                ddlSaveDirectoryField.text = chooser.selectedFile.absolutePath
            }
        }

        // 创建表单
        val formPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(5)
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL

        // 第一行：DDL 保存目录
        gbc.gridx = 0
        gbc.gridy = 0
        formPanel.add(JBLabel("DDL 保存目录:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        formPanel.add(ddlSaveDirectoryField, gbc)

        // 第二行：目录说明
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        val directoryHint = JBLabel("<html><small>可用变量: {projectDir} 项目根目录, {entityName} 实体名称<br>默认模板: {projectDir}/.autoddl/{entityName}</small></html>")
        formPanel.add(directoryHint, gbc)

        // 第三行：文件名模板
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.weightx = 0.0
        formPanel.add(JBLabel("文件名模板:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        formPanel.add(ddlFileNameTemplateField, gbc)

        // 第四行：模板说明
        gbc.gridx = 0
        gbc.gridy = 3
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        val templateHint =
            JBLabel("<html><small>可用变量: {table} 表名, {dialect} 数据库方言, {timestamp} 时间戳, {version} 版本号(YYYYMMDDHHMM)<br>Flyway 命名规范示例: V{version}__Create_{table}_{dialect}.sql</small></html>")
        formPanel.add(templateHint, gbc)

        // 第五行：复选框
        gbc.gridx = 0
        gbc.gridy = 4
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.NORTHWEST
        val checkboxPanel = JPanel(GridLayout(2, 1, 0, 5))
        checkboxPanel.add(autoSaveDdlCheckBox)
        checkboxPanel.add(openFileAfterGenerationCheckBox)
        formPanel.add(checkboxPanel, gbc)

        // 主面板
        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(10)
        mainPanel.add(formPanel, BorderLayout.NORTH)

        return mainPanel
    }

    override fun isModified(): Boolean {
        settings = DdlSettings.getInstance()
        return ddlSaveDirectoryField.text != settings.ddlSaveDirectory ||
                autoSaveDdlCheckBox.isSelected != settings.autoSaveDdl ||
                openFileAfterGenerationCheckBox.isSelected != settings.openFileAfterGeneration ||
                ddlFileNameTemplateField.text != settings.ddlFileNameTemplate
    }

    override fun apply() {
        settings = DdlSettings.getInstance()
        settings.ddlSaveDirectory = ddlSaveDirectoryField.text
        settings.autoSaveDdl = autoSaveDdlCheckBox.isSelected
        settings.openFileAfterGeneration = openFileAfterGenerationCheckBox.isSelected
        settings.ddlFileNameTemplate = ddlFileNameTemplateField.text
    }

    override fun reset() {
        settings = DdlSettings.getInstance()
        // 显示默认值模板（如果没有修改过）
        ddlSaveDirectoryField.text = if (settings.ddlSaveDirectory == "{projectDir}/.autoddl/{entityName}") {
            "{projectDir}/.autoddl/{entityName}"
        } else {
            settings.ddlSaveDirectory
        }
        autoSaveDdlCheckBox.isSelected = settings.autoSaveDdl
        openFileAfterGenerationCheckBox.isSelected = settings.openFileAfterGeneration
        ddlFileNameTemplateField.text = settings.ddlFileNameTemplate
    }
}
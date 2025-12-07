package site.addzero.autoddl.jimmer.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import site.addzero.autoddl.jimmer.datasource.SpringDataSourceResolver
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Jimmer DDL 配置界面
 */
class JimmerDdlConfigurable(private val project: Project) : Configurable {

    private val settings = JimmerDdlSettings.getInstance(project)

    // UI 组件 - 基础设置
    private val outputDirField = JBTextField()
    private val autoExecuteCheckBox = JBCheckBox("自动执行生成的DDL")
    private val confirmCheckBox = JBCheckBox("执行前需要确认")
    private val rollbackCheckBox = JBCheckBox("生成回滚SQL")
    private val includeIndexesCheckBox = JBCheckBox("包含索引")
    private val includeForeignKeysCheckBox = JBCheckBox("包含外键")
    private val includeCommentsCheckBox = JBCheckBox("包含注释")
    private val scanPackagesField = JBTextField()

    // UI 组件 - 数据源设置
    private val selectedDataSourceField = JBTextField()
    private val detectDataSourceButton = JButton("自动检测")
    private val manualJdbcUrlField = JBTextField()
    private val manualJdbcUsernameField = JBTextField()
    private val manualJdbcPasswordField = JBPasswordField()

    override fun getDisplayName(): String = "AutoDDL Jimmer"

    override fun createComponent(): JComponent {
        loadSettings()
        setupDetectButton()

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("DDL输出目录:"), outputDirField, 1, false)
            .addTooltip("相对于项目根目录，例如：.autoddl/jimmer")
            .addSeparator()

            .addComponent(autoExecuteCheckBox, 1)
            .addComponent(confirmCheckBox, 1)
            .addComponent(rollbackCheckBox, 1)
            .addSeparator()

            .addComponent(includeIndexesCheckBox, 1)
            .addComponent(includeForeignKeysCheckBox, 1)
            .addComponent(includeCommentsCheckBox, 1)
            .addSeparator()

            .addLabeledComponent(JBLabel("扫描包路径:"), scanPackagesField, 1, false)
            .addTooltip("多个包用逗号分隔，例如：com.example.entity,com.example.domain\n留空则扫描整个项目")
            .addSeparator()

            // 数据源配置区域
            .addComponent(JBLabel("<html><b>数据源配置</b></html>"), 1)
            .addTooltip("优先从 Spring 配置文件自动解析，解析失败时使用手动配置")
            
            .addLabeledComponent(JBLabel("选择数据源:"), selectedDataSourceField, 1, false)
            .addTooltip("多数据源场景下指定使用的数据源名称")
            .addComponent(detectDataSourceButton, 1)
            .addSeparator()

            .addComponent(JBLabel("<html><i>手动配置（优先级更高）:</i></html>"), 1)
            .addLabeledComponent(JBLabel("JDBC URL:"), manualJdbcUrlField, 1, false)
            .addTooltip("例如: jdbc:mysql://localhost:3306/mydb")
            .addLabeledComponent(JBLabel("用户名:"), manualJdbcUsernameField, 1, false)
            .addLabeledComponent(JBLabel("密码:"), manualJdbcPasswordField, 1, false)

            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun loadSettings() {
        outputDirField.text = settings.outputDirectory
        autoExecuteCheckBox.isSelected = settings.autoExecute
        confirmCheckBox.isSelected = settings.confirmBeforeExecute
        rollbackCheckBox.isSelected = settings.generateRollback
        includeIndexesCheckBox.isSelected = settings.includeIndexes
        includeForeignKeysCheckBox.isSelected = settings.includeForeignKeys
        includeCommentsCheckBox.isSelected = settings.includeComments
        scanPackagesField.text = settings.scanPackages
        selectedDataSourceField.text = settings.selectedDataSourceName
        manualJdbcUrlField.text = settings.manualJdbcUrl
        manualJdbcUsernameField.text = settings.manualJdbcUsername
        manualJdbcPasswordField.text = settings.manualJdbcPassword
    }

    private fun setupDetectButton() {
        detectDataSourceButton.addActionListener {
            val resolver = SpringDataSourceResolver(project)
            val dataSources = resolver.resolveDataSources()
            
            if (dataSources.isEmpty()) {
                selectedDataSourceField.text = "(未检测到数据源，请手动配置)"
            } else {
                val names = dataSources.joinToString(", ") { it.name }
                selectedDataSourceField.text = dataSources.first().name
                selectedDataSourceField.toolTipText = "检测到数据源: $names"
                
                // 如果手动配置为空，自动填充第一个数据源
                if (manualJdbcUrlField.text.isBlank()) {
                    val first = dataSources.first()
                    manualJdbcUrlField.text = first.url
                    manualJdbcUsernameField.text = first.username
                    manualJdbcPasswordField.text = first.password
                }
            }
        }
    }

    override fun isModified(): Boolean {
        return outputDirField.text != settings.outputDirectory ||
                autoExecuteCheckBox.isSelected != settings.autoExecute ||
                confirmCheckBox.isSelected != settings.confirmBeforeExecute ||
                rollbackCheckBox.isSelected != settings.generateRollback ||
                includeIndexesCheckBox.isSelected != settings.includeIndexes ||
                includeForeignKeysCheckBox.isSelected != settings.includeForeignKeys ||
                includeCommentsCheckBox.isSelected != settings.includeComments ||
                scanPackagesField.text != settings.scanPackages ||
                selectedDataSourceField.text != settings.selectedDataSourceName ||
                manualJdbcUrlField.text != settings.manualJdbcUrl ||
                manualJdbcUsernameField.text != settings.manualJdbcUsername ||
                String(manualJdbcPasswordField.password) != settings.manualJdbcPassword
    }

    override fun apply() {
        settings.outputDirectory = outputDirField.text
        settings.autoExecute = autoExecuteCheckBox.isSelected
        settings.confirmBeforeExecute = confirmCheckBox.isSelected
        settings.generateRollback = rollbackCheckBox.isSelected
        settings.includeIndexes = includeIndexesCheckBox.isSelected
        settings.includeForeignKeys = includeForeignKeysCheckBox.isSelected
        settings.includeComments = includeCommentsCheckBox.isSelected
        settings.scanPackages = scanPackagesField.text
        settings.selectedDataSourceName = selectedDataSourceField.text
        settings.manualJdbcUrl = manualJdbcUrlField.text
        settings.manualJdbcUsername = manualJdbcUsernameField.text
        settings.manualJdbcPassword = String(manualJdbcPasswordField.password)
    }

    override fun reset() {
        loadSettings()
    }
}

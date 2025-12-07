package site.addzero.autoddl.jimmer.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Jimmer DDL 配置界面
 */
class JimmerDdlConfigurable(project: Project) : Configurable {

    private val settings = JimmerDdlSettings.getInstance(project)

    // UI 组件
    private val outputDirField = JBTextField()
    private val autoExecuteCheckBox = JBCheckBox("自动执行生成的DDL")
    private val confirmCheckBox = JBCheckBox("执行前需要确认")
    private val rollbackCheckBox = JBCheckBox("生成回滚SQL")
    private val dataSourceField = JBTextField()
    private val includeIndexesCheckBox = JBCheckBox("包含索引")
    private val includeForeignKeysCheckBox = JBCheckBox("包含外键")
    private val includeCommentsCheckBox = JBCheckBox("包含注释")
    private val scanPackagesField = JBTextField()

    override fun getDisplayName(): String = "AutoDDL Jimmer"

    override fun createComponent(): JComponent {
        // 从设置加载当前值
        outputDirField.text = settings.outputDirectory
        autoExecuteCheckBox.isSelected = settings.autoExecute
        confirmCheckBox.isSelected = settings.confirmBeforeExecute
        rollbackCheckBox.isSelected = settings.generateRollback
        dataSourceField.text = settings.dataSourceName
        includeIndexesCheckBox.isSelected = settings.includeIndexes
        includeForeignKeysCheckBox.isSelected = settings.includeForeignKeys
        includeCommentsCheckBox.isSelected = settings.includeComments
        scanPackagesField.text = settings.scanPackages

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("DDL输出目录:"), outputDirField, 1, false)
            .addTooltip("相对于项目根目录，例如：.autoddl/jimmer")
            .addSeparator()

            .addComponent(autoExecuteCheckBox, 1)
            .addComponent(confirmCheckBox, 1)
            .addComponent(rollbackCheckBox, 1)
            .addSeparator()

            .addLabeledComponent(JBLabel("数据源名称:"), dataSourceField, 1, false)
            .addTooltip("从 Database 插件中配置的数据源名称")
            .addSeparator()

            .addComponent(includeIndexesCheckBox, 1)
            .addComponent(includeForeignKeysCheckBox, 1)
            .addComponent(includeCommentsCheckBox, 1)
            .addSeparator()

            .addLabeledComponent(JBLabel("扫描包路径:"), scanPackagesField, 1, false)
            .addTooltip("多个包用逗号分隔，例如：com.example.entity,com.example.domain\n留空则扫描整个项目")

            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        return outputDirField.text != settings.outputDirectory ||
                autoExecuteCheckBox.isSelected != settings.autoExecute ||
                confirmCheckBox.isSelected != settings.confirmBeforeExecute ||
                rollbackCheckBox.isSelected != settings.generateRollback ||
                dataSourceField.text != settings.dataSourceName ||
                includeIndexesCheckBox.isSelected != settings.includeIndexes ||
                includeForeignKeysCheckBox.isSelected != settings.includeForeignKeys ||
                includeCommentsCheckBox.isSelected != settings.includeComments ||
                scanPackagesField.text != settings.scanPackages
    }

    override fun apply() {
        settings.outputDirectory = outputDirField.text
        settings.autoExecute = autoExecuteCheckBox.isSelected
        settings.confirmBeforeExecute = confirmCheckBox.isSelected
        settings.generateRollback = rollbackCheckBox.isSelected
        settings.dataSourceName = dataSourceField.text
        settings.includeIndexes = includeIndexesCheckBox.isSelected
        settings.includeForeignKeys = includeForeignKeysCheckBox.isSelected
        settings.includeComments = includeCommentsCheckBox.isSelected
        settings.scanPackages = scanPackagesField.text
    }

    override fun reset() {
        outputDirField.text = settings.outputDirectory
        autoExecuteCheckBox.isSelected = settings.autoExecute
        confirmCheckBox.isSelected = settings.confirmBeforeExecute
        rollbackCheckBox.isSelected = settings.generateRollback
        dataSourceField.text = settings.dataSourceName
        includeIndexesCheckBox.isSelected = settings.includeIndexes
        includeForeignKeysCheckBox.isSelected = settings.includeForeignKeys
        includeCommentsCheckBox.isSelected = settings.includeComments
        scanPackagesField.text = settings.scanPackages
    }
}

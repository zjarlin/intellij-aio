package site.addzero.lsi.analyzer.config

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * DDL 设置配置界面
 */
class DdlSettingsConfigurable : Configurable {

    private val enableFileChangeDetectionCheckBox = JBCheckBox("启用文件变化自动检测")

    // JDBC 配置字段
    private val jdbcUrlField = JBTextField()
    private val jdbcUsernameField = JBTextField()
    private val jdbcPasswordField = JBPasswordField()
    // 移除 jdbcDialectField，因为方言会自动从 URL 推断

    private var settings: DdlSettings = DdlSettings()

    override fun getDisplayName(): String = "LSI Code Analyzer - DDL Settings"

    override fun createComponent(): JComponent {
        // 创建表单
        val formPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(5)
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL

        // 第一行：文件变化检测
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.NORTHWEST
        formPanel.add(enableFileChangeDetectionCheckBox, gbc)

        // 第二行：说明
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        val hint = JBLabel("<html><small>启用后，当 POJO 文件发生变化时，会自动生成差量 DDL 并提醒应用到数据库</small></html>")
        formPanel.add(hint, gbc)

        // 第三行：JDBC 配置标题
        gbc.gridx = 0
        gbc.gridy = 3
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        val jdbcTitle = JBLabel("<html><b><font size='4'>JDBC 连接配置</font></b></html>")
        jdbcTitle.border = JBUI.Borders.emptyTop(10)
        formPanel.add(jdbcTitle, gbc)

        // 第四行：JDBC URL
        gbc.gridy = 4
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        formPanel.add(JBLabel("JDBC URL:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        jdbcUrlField.text = "jdbc:mysql://localhost:3306/db_name"
        formPanel.add(jdbcUrlField, gbc)

        // 第五行：用户名
        gbc.gridx = 0
        gbc.gridy = 5
        gbc.weightx = 0.0
        formPanel.add(JBLabel("用户名:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        formPanel.add(jdbcUsernameField, gbc)

        // 第六行：密码
        gbc.gridx = 0
        gbc.gridy = 6
        gbc.weightx = 0.0
        formPanel.add(JBLabel("密码:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        formPanel.add(jdbcPasswordField, gbc)

        // 第七行：说明
        gbc.gridx = 0
        gbc.gridy = 7
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        val jdbcHint = JBLabel("<html><small>如果不填写，系统将自动从 Spring Boot 配置文件中读取<br>数据库方言将自动从 JDBC URL 推断<br>支持的数据库：mysql, postgresql, oracle, sqlserver, db2, h2</small></html>")
        formPanel.add(jdbcHint, gbc)

        // 主面板
        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(10)
        mainPanel.add(formPanel, BorderLayout.NORTH)

        return mainPanel
    }

    override fun isModified(): Boolean {
        settings = DdlSettings.getInstance()
        return enableFileChangeDetectionCheckBox.isSelected != settings.enableFileChangeDetection ||
                jdbcUrlField.text != (settings.jdbcUrl ?: "") ||
                jdbcUsernameField.text != (settings.jdbcUsername ?: "") ||
                String(jdbcPasswordField.password) != (settings.jdbcPassword ?: "")
    }

    override fun apply() {
        settings = DdlSettings.getInstance()
        settings.enableFileChangeDetection = enableFileChangeDetectionCheckBox.isSelected

        // 保存 JDBC 配置
        settings.jdbcUrl = jdbcUrlField.text.takeIf { it.isNotBlank() }
        settings.jdbcUsername = jdbcUsernameField.text.takeIf { it.isNotBlank() }
        settings.jdbcPassword = String(jdbcPasswordField.password).takeIf { it.isNotBlank() }
        // jdbcDialect 会自动从 URL 推断
    }

    override fun reset() {
        settings = DdlSettings.getInstance()
        enableFileChangeDetectionCheckBox.isSelected = settings.enableFileChangeDetection

        // 重置 JDBC 配置
        jdbcUrlField.text = settings.jdbcUrl ?: ""
        jdbcUsernameField.text = settings.jdbcUsername ?: ""
        jdbcPasswordField.text = settings.jdbcPassword ?: ""
        // jdbcDialect 会自动从 URL 推断，不需要重置
    }
}
package site.addzero.maven.search.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import site.addzero.maven.search.DependencyFormat
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Maven 搜索插件设置页面
 * 
 * 位置：Settings → Tools → Maven Search
 */
class MavenSearchConfigurable : Configurable {

    private val settings = MavenSearchSettings.getInstance()

    // UI 组件
    private val formatComboBox = ComboBox(arrayOf(
        "Gradle Kotlin DSL",
        "Gradle Groovy DSL",
        "Maven XML"
    ))
    
    private val maxResultsField = JBTextField()
    private val autoCopyCheckBox = JBCheckBox("Automatically copy to clipboard")
    private val timeoutField = JBTextField()

    private var modified = false

    override fun getDisplayName(): String = "Maven Search"

    override fun createComponent(): JComponent {
        // 初始化组件值
        resetComponents()

        // 添加监听器
        formatComboBox.addActionListener { modified = true }
        maxResultsField.document.addDocumentListener(SimpleDocumentListener { modified = true })
        autoCopyCheckBox.addActionListener { modified = true }
        timeoutField.document.addDocumentListener(SimpleDocumentListener { modified = true })

        // 构建表单
        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Dependency format:", formatComboBox)
            .addTooltip("Choose the format for dependency declarations")
            .addVerticalGap(8)
            
            .addLabeledComponent("Maximum search results:", maxResultsField)
            .addTooltip("Number of results to fetch from Maven Central (1-100)")
            .addVerticalGap(8)
            
            .addComponent(autoCopyCheckBox)
            .addTooltip("Automatically copy selected dependency to clipboard")
            .addVerticalGap(8)
            
            .addLabeledComponent("Search timeout (seconds):", timeoutField)
            .addTooltip("Maximum time to wait for search results")
            .addVerticalGap(8)
            
            .addComponentFillVertically(JPanel(), 0)
            .panel

        panel.border = JBUI.Borders.empty(10)
        return panel
    }

    override fun isModified(): Boolean = modified

    override fun apply() {
        // 保存设置
        settings.dependencyFormat = when (formatComboBox.selectedIndex) {
            0 -> DependencyFormat.GRADLE_KOTLIN
            1 -> DependencyFormat.GRADLE_GROOVY
            2 -> DependencyFormat.MAVEN
            else -> DependencyFormat.GRADLE_KOTLIN
        }

        settings.maxResults = maxResultsField.text.toIntOrNull()?.coerceIn(1, 100) ?: 20
        settings.autoCopyToClipboard = autoCopyCheckBox.isSelected
        settings.searchTimeout = timeoutField.text.toIntOrNull()?.coerceIn(1, 60) ?: 10

        modified = false
    }

    override fun reset() {
        resetComponents()
        modified = false
    }

    /**
     * 重置组件值为当前设置
     */
    private fun resetComponents() {
        formatComboBox.selectedIndex = when (settings.dependencyFormat) {
            DependencyFormat.GRADLE_KOTLIN -> 0
            DependencyFormat.GRADLE_GROOVY -> 1
            DependencyFormat.MAVEN -> 2
        }

        maxResultsField.text = settings.maxResults.toString()
        autoCopyCheckBox.isSelected = settings.autoCopyToClipboard
        timeoutField.text = settings.searchTimeout.toString()
    }
}

/**
 * 简单的文档监听器
 */
private class SimpleDocumentListener(private val onChange: () -> Unit) : 
    javax.swing.event.DocumentListener {
    
    override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
    override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
    override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
}

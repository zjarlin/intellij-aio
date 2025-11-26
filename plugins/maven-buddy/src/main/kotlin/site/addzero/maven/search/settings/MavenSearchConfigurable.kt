package site.addzero.maven.search.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import site.addzero.maven.search.DependencyFormat
import site.addzero.maven.search.history.SearchHistoryService
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Maven 搜索插件设置页面
 * 
 * 位置：Settings → Tools → Maven Search
 */
class MavenSearchConfigurable : Configurable {

    private val settings = MavenSearchSettings.getInstance()
    private val historyService = SearchHistoryService.getInstance()

    // UI 组件
    private val formatComboBox = ComboBox(arrayOf(
        "Gradle Kotlin DSL",
        "Gradle Groovy DSL",
        "Maven XML"
    ))
    
    private val maxResultsField = JBTextField()
    private val pageSizeField = JBTextField()
    private val enablePaginationCheckBox = JBCheckBox("Enable pagination (load more results on demand)")
    private val autoCopyCheckBox = JBCheckBox("Automatically copy to clipboard")
    private val timeoutField = JBTextField()
    private val debounceDelayField = JBTextField()
    private val requireManualTriggerCheckBox = JBCheckBox("Require Enter key to trigger search (disable auto-search)")
    
    // 历史记录相关组件
    private val enableHistoryCheckBox = JBCheckBox("Enable search history")
    private val maxKeywordHistoryField = JBTextField()
    private val maxArtifactHistoryField = JBTextField()
    private val historyStatsLabel = JBLabel()
    private val clearKeywordHistoryButton = JButton("Clear Keywords")
    private val clearArtifactHistoryButton = JButton("Clear Artifacts")
    private val clearAllHistoryButton = JButton("Clear All")

    private var modified = false

    override fun getDisplayName(): String = "Maven Search"

    override fun createComponent(): JComponent {
        // 初始化组件值
        resetComponents()

        // 添加监听器
        val docListener = SimpleDocumentListener { modified = true }
        formatComboBox.addActionListener { modified = true }
        maxResultsField.document.addDocumentListener(docListener)
        pageSizeField.document.addDocumentListener(docListener)
        enablePaginationCheckBox.addActionListener {
            modified = true
            // 启用分页时，禁用 maxResults，启用 pageSize
            maxResultsField.isEnabled = !enablePaginationCheckBox.isSelected
            pageSizeField.isEnabled = enablePaginationCheckBox.isSelected
        }
        autoCopyCheckBox.addActionListener { modified = true }
        timeoutField.document.addDocumentListener(docListener)
        debounceDelayField.document.addDocumentListener(docListener)
        requireManualTriggerCheckBox.addActionListener { 
            modified = true
            // 当启用手动触发时，禁用防抖延迟设置
            debounceDelayField.isEnabled = !requireManualTriggerCheckBox.isSelected
        }
        
        // 历史记录监听器
        enableHistoryCheckBox.addActionListener {
            modified = true
            updateHistoryFieldsEnabled()
        }
        maxKeywordHistoryField.document.addDocumentListener(docListener)
        maxArtifactHistoryField.document.addDocumentListener(docListener)
        
        // 清除历史按钮
        clearKeywordHistoryButton.addActionListener {
            if (confirmClearHistory("keyword history")) {
                historyService.clearKeywords()
                updateHistoryStats()
            }
        }
        clearArtifactHistoryButton.addActionListener {
            if (confirmClearHistory("artifact history")) {
                historyService.clearArtifacts()
                updateHistoryStats()
            }
        }
        clearAllHistoryButton.addActionListener {
            if (confirmClearHistory("all search history")) {
                historyService.clearAll()
                updateHistoryStats()
            }
        }

        // 构建表单
        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Dependency format:", formatComboBox)
            .addTooltip("Choose the format for dependency declarations")
            .addVerticalGap(8)
            
            .addSeparator(8)
            
            .addComponent(enablePaginationCheckBox)
            .addTooltip("Enable pagination to load more results on demand. When disabled, all results are loaded at once.")
            .addVerticalGap(8)
            
            .addLabeledComponent("Page size:", pageSizeField)
            .addTooltip("Number of results per page when pagination is enabled (1-100)")
            .addVerticalGap(8)
            
            .addLabeledComponent("Maximum results (deprecated):", maxResultsField)
            .addTooltip("Number of results to fetch when pagination is disabled (1-100)")
            .addVerticalGap(8)
            
            .addComponent(autoCopyCheckBox)
            .addTooltip("Automatically copy selected dependency to clipboard")
            .addVerticalGap(8)
            
            .addLabeledComponent("Search timeout (seconds):", timeoutField)
            .addTooltip("Maximum time to wait for search results")
            .addVerticalGap(12)
            
            .addSeparator(8)
            
            .addComponent(requireManualTriggerCheckBox)
            .addTooltip("When enabled, you must press Enter to trigger search. When disabled, search starts automatically after typing stops.")
            .addVerticalGap(8)
            
            .addLabeledComponent("Debounce delay (milliseconds):", debounceDelayField)
            .addTooltip("Wait time before auto-triggering search after typing stops. Recommended: 300ms (fast), 500ms (balanced), 800ms (slow network)")
            .addVerticalGap(12)
            
            .addSeparator(8)
            
            .addComponent(enableHistoryCheckBox)
            .addTooltip("Enable search history to quickly access recently used dependencies")
            .addVerticalGap(8)
            
            .addLabeledComponent("Max keyword history:", maxKeywordHistoryField)
            .addTooltip("Maximum number of search keywords to remember (1-200)")
            .addVerticalGap(8)
            
            .addLabeledComponent("Max artifact history:", maxArtifactHistoryField)
            .addTooltip("Maximum number of selected artifacts to remember (1-500)")
            .addVerticalGap(8)
            
            .addComponent(historyStatsLabel)
            .addVerticalGap(8)
            
            .addComponent(createHistoryButtonsPanel())
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

        settings.enablePagination = enablePaginationCheckBox.isSelected
        settings.pageSize = pageSizeField.text.toIntOrNull()?.coerceIn(1, 100) ?: 20
        @Suppress("DEPRECATION")
        settings.maxResults = maxResultsField.text.toIntOrNull()?.coerceIn(1, 100) ?: 20
        settings.autoCopyToClipboard = autoCopyCheckBox.isSelected
        settings.searchTimeout = timeoutField.text.toIntOrNull()?.coerceIn(1, 60) ?: 10
        settings.debounceDelay = debounceDelayField.text.toIntOrNull()?.coerceIn(100, 2000) ?: 500
        settings.requireManualTrigger = requireManualTriggerCheckBox.isSelected
        
        // 历史设置
        historyService.enableHistory = enableHistoryCheckBox.isSelected
        historyService.maxKeywordHistorySize = maxKeywordHistoryField.text.toIntOrNull()?.coerceIn(1, 200) ?: 50
        historyService.maxArtifactHistorySize = maxArtifactHistoryField.text.toIntOrNull()?.coerceIn(1, 500) ?: 100

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

        enablePaginationCheckBox.isSelected = settings.enablePagination
        pageSizeField.text = settings.pageSize.toString()
        @Suppress("DEPRECATION")
        maxResultsField.text = settings.maxResults.toString()
        autoCopyCheckBox.isSelected = settings.autoCopyToClipboard
        timeoutField.text = settings.searchTimeout.toString()
        debounceDelayField.text = settings.debounceDelay.toString()
        requireManualTriggerCheckBox.isSelected = settings.requireManualTrigger
        
        // 根据分页设置禁用/启用对应字段
        pageSizeField.isEnabled = settings.enablePagination
        maxResultsField.isEnabled = !settings.enablePagination
        
        // 根据手动触发设置禁用/启用防抖延迟
        debounceDelayField.isEnabled = !settings.requireManualTrigger
        
        // 历史记录设置
        enableHistoryCheckBox.isSelected = historyService.enableHistory
        maxKeywordHistoryField.text = historyService.maxKeywordHistorySize.toString()
        maxArtifactHistoryField.text = historyService.maxArtifactHistorySize.toString()
        updateHistoryFieldsEnabled()
        updateHistoryStats()
    }
    
    /**
     * 创建历史按钮面板
     */
    private fun createHistoryButtonsPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            add(clearKeywordHistoryButton)
            add(clearArtifactHistoryButton)
            add(clearAllHistoryButton)
        }
    }
    
    /**
     * 更新历史字段启用状态
     */
    private fun updateHistoryFieldsEnabled() {
        val enabled = enableHistoryCheckBox.isSelected
        maxKeywordHistoryField.isEnabled = enabled
        maxArtifactHistoryField.isEnabled = enabled
        clearKeywordHistoryButton.isEnabled = enabled
        clearArtifactHistoryButton.isEnabled = enabled
        clearAllHistoryButton.isEnabled = enabled
    }
    
    /**
     * 更新历史统计信息
     */
    private fun updateHistoryStats() {
        val keywordCount = historyService.searchKeywords.size
        val artifactCount = historyService.selectedArtifacts.size
        historyStatsLabel.text = "Current: $keywordCount keywords, $artifactCount artifacts"
    }
    
    /**
     * 确认清除历史
     */
    private fun confirmClearHistory(historyType: String): Boolean {
        return Messages.showYesNoDialog(
            "Are you sure you want to clear $historyType?",
            "Clear History",
            Messages.getQuestionIcon()
        ) == Messages.YES
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

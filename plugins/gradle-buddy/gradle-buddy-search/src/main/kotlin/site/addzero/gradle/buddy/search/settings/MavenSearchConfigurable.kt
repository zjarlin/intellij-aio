package site.addzero.gradle.buddy.search.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import site.addzero.gradle.buddy.search.cache.SearchResultCacheService
import site.addzero.gradle.buddy.search.history.SearchHistoryService
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
    private val cacheService = SearchResultCacheService.getInstance()

    private val formatInfoLabel = JBLabel("Auto-detected based on project type (build.gradle.kts / build.gradle / pom.xml)")

    private val pageSizeField = JBTextField()
    private val enablePaginationCheckBox = JBCheckBox("Enable pagination (load more results on demand)")
    private val autoCopyCheckBox = JBCheckBox("Automatically copy to clipboard")
    private val timeoutField = JBTextField()
    private val debounceDelayField = JBTextField()
    private val requireManualTriggerCheckBox = JBCheckBox("Require Enter key to trigger search (disable auto-search)")

    private val enableHistoryCheckBox = JBCheckBox("Enable search history")
    private val storagePathLabel = JBLabel("Storage: ${MavenSearchSettings.configDir}")
    private val maxKeywordHistoryField = JBTextField()
    private val maxArtifactHistoryField = JBTextField()
    private val historyStatsLabel = JBLabel()
    private val clearKeywordHistoryButton = JButton("Clear Keywords")
    private val clearArtifactHistoryButton = JButton("Clear Artifacts")
    private val clearAllHistoryButton = JButton("Clear All")
    private val clearCacheButton = JButton("Clear Cache")

    private var modified = false

    override fun getDisplayName(): String = "Maven Search"

    override fun createComponent(): JComponent {
        resetComponents()

        val docListener = SimpleDocumentListener { modified = true }
        pageSizeField.document.addDocumentListener(docListener)
        enablePaginationCheckBox.addActionListener { modified = true }
        autoCopyCheckBox.addActionListener { modified = true }
        timeoutField.document.addDocumentListener(docListener)
        debounceDelayField.document.addDocumentListener(docListener)
        requireManualTriggerCheckBox.addActionListener {
            modified = true
            debounceDelayField.isEnabled = !requireManualTriggerCheckBox.isSelected
        }

        enableHistoryCheckBox.addActionListener {
            modified = true
            updateHistoryFieldsEnabled()
        }
        maxKeywordHistoryField.document.addDocumentListener(docListener)
        maxArtifactHistoryField.document.addDocumentListener(docListener)

        clearKeywordHistoryButton.addActionListener {
            if (confirmClear("keyword history")) {
                historyService.clearKeywords()
                updateHistoryStats()
            }
        }
        clearArtifactHistoryButton.addActionListener {
            if (confirmClear("artifact history")) {
                historyService.clearArtifacts()
                updateHistoryStats()
            }
        }
        clearAllHistoryButton.addActionListener {
            if (confirmClear("all search history")) {
                historyService.clearAll()
                updateHistoryStats()
            }
        }
        clearCacheButton.addActionListener {
            if (confirmClear("search cache")) {
                cacheService.clearAll()
                updateHistoryStats()
            }
        }

        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Dependency format:", formatInfoLabel)
            .addTooltip("Format is automatically detected based on your project's build files")
            .addVerticalGap(8)

            .addSeparator(8)

            .addComponent(enablePaginationCheckBox)
            .addTooltip("Enable pagination to load more results on demand")
            .addVerticalGap(8)

            .addLabeledComponent("Page size:", pageSizeField)
            .addTooltip("Number of results per page (1-100)")
            .addVerticalGap(8)

            .addComponent(autoCopyCheckBox)
            .addTooltip("Automatically copy selected dependency to clipboard")
            .addVerticalGap(8)

            .addLabeledComponent("Search timeout (seconds):", timeoutField)
            .addTooltip("Maximum time to wait for search results")
            .addVerticalGap(12)

            .addSeparator(8)

            .addComponent(requireManualTriggerCheckBox)
            .addTooltip("When enabled, you must press Enter to trigger search")
            .addVerticalGap(8)

            .addLabeledComponent("Debounce delay (milliseconds):", debounceDelayField)
            .addTooltip("Wait time before auto-triggering search. Recommended: 300-800ms")
            .addVerticalGap(12)

            .addSeparator(8)

            .addComponent(enableHistoryCheckBox)
            .addTooltip("Enable search history to quickly access recently used dependencies")
            .addVerticalGap(8)

            .addComponent(storagePathLabel)
            .addTooltip("History: JSON, Cache: SQLite")
            .addVerticalGap(8)

            .addLabeledComponent("Max keyword history:", maxKeywordHistoryField)
            .addTooltip("Maximum number of search keywords to remember (1-200)")
            .addVerticalGap(8)

            .addLabeledComponent("Max artifact history:", maxArtifactHistoryField)
            .addTooltip("Maximum number of selected artifacts to remember (1-500)")
            .addVerticalGap(8)

            .addComponent(historyStatsLabel)
            .addVerticalGap(8)

            .addComponent(createButtonsPanel())
            .addVerticalGap(8)

            .addComponentFillVertically(JPanel(), 0)
            .panel

        panel.border = JBUI.Borders.empty(10)
        return panel
    }

    override fun isModified(): Boolean = modified

    override fun apply() {
        settings.enablePagination = enablePaginationCheckBox.isSelected
        settings.pageSize = pageSizeField.text.toIntOrNull()?.coerceIn(1, 100) ?: 50
        settings.autoCopyToClipboard = autoCopyCheckBox.isSelected
        settings.searchTimeout = timeoutField.text.toIntOrNull()?.coerceIn(1, 60) ?: 10
        settings.debounceDelay = debounceDelayField.text.toIntOrNull()?.coerceIn(100, 2000) ?: 500
        settings.requireManualTrigger = requireManualTriggerCheckBox.isSelected

        historyService.enableHistory = enableHistoryCheckBox.isSelected
        historyService.maxKeywordHistorySize = maxKeywordHistoryField.text.toIntOrNull()?.coerceIn(1, 200) ?: 50
        historyService.maxArtifactHistorySize = maxArtifactHistoryField.text.toIntOrNull()?.coerceIn(1, 500) ?: 100

        modified = false
    }

    override fun reset() {
        resetComponents()
        modified = false
    }

    private fun resetComponents() {
        enablePaginationCheckBox.isSelected = settings.enablePagination
        pageSizeField.text = settings.pageSize.toString()
        autoCopyCheckBox.isSelected = settings.autoCopyToClipboard
        timeoutField.text = settings.searchTimeout.toString()
        debounceDelayField.text = settings.debounceDelay.toString()
        requireManualTriggerCheckBox.isSelected = settings.requireManualTrigger
        debounceDelayField.isEnabled = !settings.requireManualTrigger

        enableHistoryCheckBox.isSelected = historyService.enableHistory
        maxKeywordHistoryField.text = historyService.maxKeywordHistorySize.toString()
        maxArtifactHistoryField.text = historyService.maxArtifactHistorySize.toString()
        updateHistoryFieldsEnabled()
        updateHistoryStats()
    }

    private fun createButtonsPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            add(clearKeywordHistoryButton)
            add(clearArtifactHistoryButton)
            add(clearAllHistoryButton)
            add(clearCacheButton)
        }
    }

    private fun updateHistoryFieldsEnabled() {
        val enabled = enableHistoryCheckBox.isSelected
        maxKeywordHistoryField.isEnabled = enabled
        maxArtifactHistoryField.isEnabled = enabled
        clearKeywordHistoryButton.isEnabled = enabled
        clearArtifactHistoryButton.isEnabled = enabled
        clearAllHistoryButton.isEnabled = enabled
    }

    private fun updateHistoryStats() {
        val keywordCount = historyService.searchKeywords.size
        val artifactCount = historyService.selectedArtifacts.size
        val cacheStats = cacheService.stats()
        historyStatsLabel.text = "Keywords: $keywordCount, Artifacts: $artifactCount, Cache: ${cacheStats.totalEntries}"
    }

    private fun confirmClear(target: String): Boolean {
        return Messages.showYesNoDialog(
            "Are you sure you want to clear $target?",
            "Clear History",
            Messages.getQuestionIcon()
        ) == Messages.YES
    }
}

private class SimpleDocumentListener(private val onChange: () -> Unit) :
    javax.swing.event.DocumentListener {
    override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
    override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
    override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
}

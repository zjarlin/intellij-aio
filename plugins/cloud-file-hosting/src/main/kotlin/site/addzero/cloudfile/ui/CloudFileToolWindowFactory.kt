package site.addzero.cloudfile.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import site.addzero.cloudfile.sync.CloudFileSyncService
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.JComponent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JTextArea
import javax.swing.BorderFactory
import javax.swing.JOptionPane
import javax.swing.DefaultListModel

/**
 * Factory for the Cloud File Hosting tool window
 */
class CloudFileToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val mainPanel = JBTabbedPane()

        // Tab 1: Quick Actions & Status
        val quickPanel = CloudFileQuickPanel(project)
        mainPanel.addTab("Overview", quickPanel)

        // Tab 2: Browser - View hosted files
        val browserPanel = HostedFilesBrowserPanel(project)
        mainPanel.addTab("Browser", browserPanel)

        // Tab 3: Cache Status
        val cachePanel = CacheStatusPanel(project)
        mainPanel.addTab("Cache", cachePanel)

        val content = ContentFactory.getInstance().createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * Quick actions panel (original functionality)
 */
class CloudFileQuickPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val syncService = CloudFileSyncService.getInstance(project)

    init {
        // Header panel with sync buttons
        val headerPanel = JPanel(FlowLayout(FlowLayout.LEFT))

        val syncToCloudBtn = JButton("Sync to Cloud", com.intellij.icons.AllIcons.Actions.Upload)
        val syncFromCloudBtn = JButton("Sync from Cloud", com.intellij.icons.AllIcons.Actions.Download)
        val settingsBtn = JButton("Settings", com.intellij.icons.AllIcons.General.Settings)

        syncToCloudBtn.addActionListener {
            com.intellij.openapi.progress.ProgressManager.getInstance().run(
                object : com.intellij.openapi.progress.Task.Backgroundable(project, "Syncing to Cloud", true) {
                    override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                        syncService.syncToCloud(force = true, indicator = indicator)
                    }
                }
            )
        }

        syncFromCloudBtn.addActionListener {
            com.intellij.openapi.progress.ProgressManager.getInstance().run(
                object : com.intellij.openapi.progress.Task.Backgroundable(project, "Syncing from Cloud", true) {
                    override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                        syncService.syncFromCloud(indicator)
                    }
                }
            )
        }

        settingsBtn.addActionListener {
            com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(project, "Cloud File Hosting")
        }

        headerPanel.add(syncToCloudBtn)
        headerPanel.add(syncFromCloudBtn)
        headerPanel.add(settingsBtn)

        add(headerPanel, BorderLayout.NORTH)

        // Center panel with file list
        val fileListPanel = createFileListPanel()
        add(fileListPanel, BorderLayout.CENTER)

        // Status panel
        val statusPanel = createStatusPanel()
        add(statusPanel, BorderLayout.SOUTH)
    }

    private fun createFileListPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("Hosted Files")

        val model = DefaultListModel<String>()
        val list = JBList(model)

        // Populate with current rules
        val projectSettings = site.addzero.cloudfile.settings.ProjectHostingSettings.getInstance(project)
        val rules = projectSettings.getEffectiveRules(project)

        rules.forEach { rule ->
            val typeLabel = when (rule.type) {
                site.addzero.cloudfile.settings.CloudFileSettings.HostingRule.RuleType.FILE -> "[File]"
                site.addzero.cloudfile.settings.CloudFileSettings.HostingRule.RuleType.DIRECTORY -> "[Dir]"
                site.addzero.cloudfile.settings.CloudFileSettings.HostingRule.RuleType.GLOB -> "[Glob]"
            }
            model.addElement("$typeLabel ${rule.pattern}")
        }

        panel.add(JBScrollPane(list), BorderLayout.CENTER)
        return panel
    }

    private fun createStatusPanel(): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        val projectSettings = site.addzero.cloudfile.settings.ProjectHostingSettings.getInstance(project)
        val lastSync = projectSettings.state.lastSyncTimestamp

        val statusLabel = if (lastSync > 0) {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(lastSync))
            JLabel("Last sync: $date | ${projectSettings.state.syncedFiles.size} files")
        } else {
            JLabel("Not synced yet")
        }

        panel.add(statusLabel)
        return panel
    }
}

/**
 * Cache status panel
 */
class CacheStatusPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val cacheManager = site.addzero.cloudfile.cache.OfflineCacheManager.getInstance(project)

    init {
        updateStats()
    }

    private fun updateStats() {
        removeAll()

        val stats = cacheManager.getCacheStats()
        val textArea = JTextArea()
        textArea.isEditable = false
        textArea.text = buildString {
            appendLine("=== Cache Statistics ===")
            appendLine()
            appendLine("Total Files: ${stats.totalFiles}")
            appendLine("Total Size: %.2f MB".format(stats.totalSizeMB()))
            appendLine("Synced to Cloud: ${stats.syncedFiles}")
            appendLine("Pending Uploads: ${stats.pendingUploads}")
            appendLine()
            appendLine("Cache Directory:")
            appendLine(stats.cacheDirPath)
            appendLine()
            appendLine("=== Actions ===")
        }

        add(JBScrollPane(textArea), BorderLayout.CENTER)

        // Button panel
        val buttonPanel = JPanel()
        val refreshBtn = JButton("Refresh", com.intellij.icons.AllIcons.Actions.Refresh)
        val cleanupBtn = JButton("Cleanup Old Files")
        val clearBtn = JButton("Clear All Cache")

        refreshBtn.addActionListener { updateStats() }
        cleanupBtn.addActionListener {
            cacheManager.cleanupOldCache(30)
            updateStats()
        }
        clearBtn.addActionListener {
            val result = JOptionPane.showConfirmDialog(
                this,
                "Clear all local cache? This cannot be undone!",
                "Confirm",
                JOptionPane.YES_NO_OPTION
            )
            if (result == JOptionPane.YES_OPTION) {
                site.addzero.cloudfile.settings.ProjectHostingSettings.getInstance(project).let { settings ->
                    cacheManager.clearNamespaceCache(settings.getNamespace(project))
                }
                updateStats()
            }
        }

        buttonPanel.add(refreshBtn)
        buttonPanel.add(cleanupBtn)
        buttonPanel.add(clearBtn)

        add(buttonPanel, BorderLayout.SOUTH)

        revalidate()
        repaint()
    }
}

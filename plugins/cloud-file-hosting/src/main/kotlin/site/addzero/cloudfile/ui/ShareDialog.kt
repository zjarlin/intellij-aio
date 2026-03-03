package site.addzero.cloudfile.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import site.addzero.cloudfile.share.ShareLinkManager
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Dialog for generating share packages
 */
class ShareDialog(
    private val project: Project,
    private val shareManager: ShareLinkManager
) : DialogWrapper(project) {

    private val includeGlobalRulesCheck = JBCheckBox("Include Global Rules", true)
    private val includeProjectRulesCheck = JBCheckBox("Include Project Rules", true)
    private val includeFilesCheck = JBCheckBox("Include Files", false)
    private val filePatternsField = JBTextField("build-logic/**, .idea/**")

    init {
        title = "Generate Share Link"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(500, 300)

        // Options panel
        val optionsPanel = JPanel()
        optionsPanel.layout = BoxLayout(optionsPanel, BoxLayout.Y_AXIS)
        optionsPanel.border = BorderFactory.createTitledBorder("Include in Share")

        optionsPanel.add(includeGlobalRulesCheck)
        optionsPanel.add(includeProjectRulesCheck)
        optionsPanel.add(includeFilesCheck)

        val filePanel = JPanel(BorderLayout())
        filePanel.add(JLabel("File Patterns (comma-separated):"), BorderLayout.NORTH)
        filePanel.add(filePatternsField, BorderLayout.CENTER)
        filePanel.isEnabled = false

        includeFilesCheck.addActionListener {
            filePanel.isEnabled = includeFilesCheck.isSelected
            filePatternsField.isEnabled = includeFilesCheck.isSelected
        }

        optionsPanel.add(Box.createVerticalStrut(10))
        optionsPanel.add(filePanel)

        panel.add(optionsPanel, BorderLayout.NORTH)

        // Info panel
        val infoArea = JTextArea(
            """
            Share Link Information:

            • Global Rules: Applied to all projects
            • Project Rules: Specific to this project
            • Files: Actual file contents (be careful with sensitive data!)

            The recipient can import this configuration by pasting the generated link.
            """.trimIndent()
        )
        infoArea.isEditable = false
        infoArea.background = panel.background
        panel.add(JScrollPane(infoArea), BorderLayout.CENTER)

        return panel
    }

    fun getSharePackage(): ShareLinkManager.SharePackage {
        val patterns = if (includeFilesCheck.isSelected) {
            filePatternsField.text.split(",").map { it.trim() }
        } else {
            emptyList()
        }

        return shareManager.generateSharePackage(
            includeGlobalRules = includeGlobalRulesCheck.isSelected,
            includeProjectRules = includeProjectRulesCheck.isSelected,
            includeFiles = includeFilesCheck.isSelected,
            filePatterns = patterns
        )
    }
}

/**
 * Dialog for importing share packages
 */
class ImportDialog(
    private val project: Project,
    private val packageData: ShareLinkManager.SharePackage,
    private val shareManager: ShareLinkManager
) : DialogWrapper(project) {

    private val applyGlobalRulesCheck = JBCheckBox("Apply Global Rules (${packageData.globalRules.size})", false)
    private val applyProjectRulesCheck = JBCheckBox("Apply Project Rules (${packageData.projectRules.size})", true)
    private val applyFilesCheck = JBCheckBox("Apply Files (${packageData.files.size})", true)
    private val overwriteExistingCheck = JBCheckBox("Overwrite Existing", false)

    init {
        title = "Import Share Package"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(500, 400)

        // Info panel
        val infoPanel = JPanel()
        infoPanel.layout = BoxLayout(infoPanel, BoxLayout.Y_AXIS)
        infoPanel.border = BorderFactory.createTitledBorder("Package Info")

        infoPanel.add(JLabel("Shared by: ${packageData.sharedBy}"))
        infoPanel.add(JLabel("Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(packageData.createdAt))}"))
        infoPanel.add(JLabel("Storage: ${packageData.storageProvider}"))
        infoPanel.add(JLabel("Namespace: ${packageData.namespace}"))

        panel.add(infoPanel, BorderLayout.NORTH)

        // Selection panel
        val selectionPanel = JPanel()
        selectionPanel.layout = BoxLayout(selectionPanel, BoxLayout.Y_AXIS)
        selectionPanel.border = BorderFactory.createTitledBorder("Select Items to Import")

        if (packageData.globalRules.isNotEmpty()) {
            selectionPanel.add(applyGlobalRulesCheck)
        }
        if (packageData.projectRules.isNotEmpty()) {
            selectionPanel.add(applyProjectRulesCheck)
        }
        if (packageData.files.isNotEmpty()) {
            selectionPanel.add(applyFilesCheck)
        }

        selectionPanel.add(Box.createVerticalStrut(10))
        selectionPanel.add(overwriteExistingCheck)

        panel.add(selectionPanel, BorderLayout.CENTER)

        // File list (if any)
        if (packageData.files.isNotEmpty()) {
            val filePanel = JPanel(BorderLayout())
            filePanel.border = BorderFactory.createTitledBorder("Files in Package")

            val tableModel = DefaultTableModel(arrayOf("File", "Size"), 0)
            packageData.files.forEach { file ->
                val sizeStr = when {
                    file.size < 1024 -> "${file.size} B"
                    file.size < 1024 * 1024 -> "${file.size / 1024} KB"
                    else -> "${file.size / (1024 * 1024)} MB"
                }
                tableModel.addRow(arrayOf(file.relativePath, sizeStr))
            }

            val table = JBTable(tableModel)
            filePanel.add(JScrollPane(table), BorderLayout.CENTER)

            panel.add(filePanel, BorderLayout.SOUTH)
        }

        return panel
    }

    override fun doOKAction() {
        val strategy = if (overwriteExistingCheck.isSelected) {
            ShareLinkManager.ConflictStrategy.OVERWRITE
        } else {
            ShareLinkManager.ConflictStrategy.SKIP
        }

        val result = shareManager.applySharePackage(
            packageData = packageData,
            applyGlobalRules = applyGlobalRulesCheck.isSelected,
            applyProjectRules = applyProjectRulesCheck.isSelected,
            applyFiles = applyFilesCheck.isSelected,
            conflictStrategy = strategy
        )

        // Show result
        val message = buildString {
            appendLine("Applied: ${result.applied.size}")
            appendLine("Skipped: ${result.skipped.size}")
            if (result.errors.isNotEmpty()) {
                appendLine("Errors: ${result.errors.size}")
                result.errors.forEach { appendLine("  - $it") }
            }
        }

        JOptionPane.showMessageDialog(
            contentPanel,
            message,
            if (result.success) "Import Successful" else "Import Completed with Errors",
            if (result.success) JOptionPane.INFORMATION_MESSAGE else JOptionPane.WARNING_MESSAGE
        )

        super.doOKAction()
    }
}

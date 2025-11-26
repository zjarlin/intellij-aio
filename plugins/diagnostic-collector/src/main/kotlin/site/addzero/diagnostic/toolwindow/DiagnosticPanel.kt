package site.addzero.diagnostic.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import site.addzero.diagnostic.model.DiagnosticSeverity
import site.addzero.diagnostic.model.FileDiagnostics
import site.addzero.diagnostic.service.DiagnosticCollectorService
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*

class DiagnosticPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val tabbedPane = JTabbedPane()
    private val statusLabel = JBLabel("点击刷新按钮收集诊断信息（仅收集已打开过的文件）")
    private val errorPanel = FileListPanel(project, DiagnosticSeverity.ERROR) { fileName ->
        statusLabel.text = "已复制 $fileName 的诊断信息到剪贴板"
    }
    private val warningPanel = FileListPanel(project, DiagnosticSeverity.WARNING) { fileName ->
        statusLabel.text = "已复制 $fileName 的诊断信息到剪贴板"
    }
    
    private var currentDiagnostics: List<FileDiagnostics> = emptyList()
    private val diagnosticsListener: (List<FileDiagnostics>) -> Unit = { updateDiagnostics(it) }
    
    init {
        setupUI()
        setupAutoRefresh()
    }
    
    private fun setupAutoRefresh() {
        DiagnosticCollectorService.getInstance(project).addListener(diagnosticsListener)
    }
    
    private fun setupUI() {
        val toolbar = createToolbar()
        add(toolbar, BorderLayout.NORTH)
        
        tabbedPane.addTab("Errors", AllIcons.General.Error, JBScrollPane(errorPanel))
        tabbedPane.addTab("Warnings", AllIcons.General.Warning, JBScrollPane(warningPanel))
        add(tabbedPane, BorderLayout.CENTER)
        
        val statusBar = JPanel(FlowLayout(FlowLayout.LEFT))
        statusBar.add(statusLabel)
        add(statusBar, BorderLayout.SOUTH)
    }
    
    private fun createToolbar(): JToolBar {
        val toolbar = JToolBar()
        toolbar.isFloatable = false
        
        val refreshButton = JButton("刷新", AllIcons.Actions.Refresh)
        refreshButton.addActionListener { refreshDiagnostics() }
        toolbar.add(refreshButton)
        
        toolbar.addSeparator()
        
        val copyAllButton = JButton("全部复制", AllIcons.Actions.Copy)
        copyAllButton.toolTipText = "复制所有诊断信息生成AI修复提示词"
        copyAllButton.addActionListener { copyAllToClipboard() }
        toolbar.add(copyAllButton)
        
        val copyErrorsButton = JButton("复制错误", AllIcons.General.Error)
        copyErrorsButton.addActionListener { copyErrorsToClipboard() }
        toolbar.add(copyErrorsButton)
        
        val copyWarningsButton = JButton("复制警告", AllIcons.General.Warning)
        copyWarningsButton.addActionListener { copyWarningsToClipboard() }
        toolbar.add(copyWarningsButton)
        
        return toolbar
    }
    
    private fun refreshDiagnostics() {
        statusLabel.text = "正在收集诊断信息..."
        DiagnosticCollectorService.getInstance(project).collectDiagnostics { updateDiagnostics(it) }
    }
    
    private fun updateDiagnostics(diagnostics: List<FileDiagnostics>) {
        currentDiagnostics = diagnostics
        
        val service = DiagnosticCollectorService.getInstance(project)
        val errorFiles = service.getErrorFiles(diagnostics)
        val warningFiles = service.getWarningFiles(diagnostics)
        
        errorPanel.setFiles(errorFiles)
        warningPanel.setFiles(warningFiles)
        
        val errorCount = errorFiles.sumOf { it.items.size }
        val warningCount = warningFiles.sumOf { it.items.size }
        
        tabbedPane.setTitleAt(0, "Errors ($errorCount)")
        tabbedPane.setTitleAt(1, "Warnings ($warningCount)")
        
        statusLabel.text = "共 ${diagnostics.size} 个文件, $errorCount 个错误, $warningCount 个警告"
    }
    
    private fun copyAllToClipboard() {
        val content = buildPromptContent(currentDiagnostics)
        copyToClipboard(content)
    }
    
    private fun copyErrorsToClipboard() {
        val errorFiles = DiagnosticCollectorService.getInstance(project).getErrorFiles(currentDiagnostics)
        val content = buildPromptContent(errorFiles)
        copyToClipboard(content)
    }
    
    private fun copyWarningsToClipboard() {
        val warningFiles = DiagnosticCollectorService.getInstance(project).getWarningFiles(currentDiagnostics)
        val content = buildPromptContent(warningFiles)
        copyToClipboard(content)
    }
    
    private fun buildPromptContent(diagnostics: List<FileDiagnostics>): String = buildString {
        appendLine("请帮我修复以下编译问题：")
        appendLine()
        diagnostics.forEach { fileDiag ->
            append(fileDiag.toAiPrompt())
            appendLine()
        }
    }
    
    private fun copyToClipboard(content: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(content), null)
        statusLabel.text = "已复制到剪贴板"
    }
}

class FileListPanel(
    private val project: Project,
    private val severity: DiagnosticSeverity,
    private val onCopied: (String) -> Unit = {}
) : JPanel() {
    
    private val filesContainer = JPanel()
    
    init {
        layout = BorderLayout()
        filesContainer.layout = BoxLayout(filesContainer, BoxLayout.Y_AXIS)
        add(filesContainer, BorderLayout.NORTH)
    }
    
    fun clear() {
        filesContainer.removeAll()
        revalidate()
        repaint()
    }
    
    fun setFiles(files: List<FileDiagnostics>) {
        clear()
        files.forEach { fileDiag ->
            filesContainer.add(FileRow(project, fileDiag, severity) { 
                onCopied(fileDiag.file.name) 
            })
        }
        revalidate()
        repaint()
    }
}

class FileRow(
    private val project: Project,
    private val fileDiagnostics: FileDiagnostics,
    private val severity: DiagnosticSeverity,
    private val onCopied: () -> Unit = {}
) : JPanel(BorderLayout()) {
    
    init {
        border = JBUI.Borders.empty(4, 8)
        maximumSize = Dimension(Int.MAX_VALUE, 40)
        background = JBColor.background()
        
        setupUI()
        setupHoverEffect()
    }
    
    private fun setupUI() {
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        leftPanel.isOpaque = false
        
        val copyButton = JButton(AllIcons.Actions.Copy)
        copyButton.toolTipText = "复制此文件的诊断信息"
        copyButton.preferredSize = Dimension(24, 24)
        copyButton.addActionListener { copyToClipboard() }
        leftPanel.add(copyButton)
        
        val icon = when (severity) {
            DiagnosticSeverity.ERROR -> AllIcons.General.Error
            DiagnosticSeverity.WARNING -> AllIcons.General.Warning
        }
        leftPanel.add(JBLabel(icon))
        
        add(leftPanel, BorderLayout.WEST)
        
        val itemCount = fileDiagnostics.items.size
        val lines = fileDiagnostics.items.joinToString(", ") { it.lineNumber.toString() }
        val fileLabel = JBLabel("<html><b>${fileDiagnostics.file.name}</b> - $itemCount 个问题 (行: $lines)</html>")
        fileLabel.border = JBUI.Borders.emptyLeft(8)
        fileLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        fileLabel.toolTipText = "点击跳转到文件"
        fileLabel.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                navigateToFile()
            }
        })
        
        add(fileLabel, BorderLayout.CENTER)
    }
    
    private fun setupHoverEffect() {
        val defaultBg = background
        val hoverBg = JBColor.namedColor("List.hoverBackground", JBColor(0xEDF6FE, 0x464A4D))
        
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseEntered(e: java.awt.event.MouseEvent) {
                background = hoverBg
            }
            override fun mouseExited(e: java.awt.event.MouseEvent) {
                background = defaultBg
            }
        })
    }
    
    private fun navigateToFile() {
        val firstItem = fileDiagnostics.items.firstOrNull() ?: return
        val descriptor = OpenFileDescriptor(project, fileDiagnostics.file, firstItem.lineNumber - 1, 0)
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
    }
    
    private fun copyToClipboard() {
        val content = fileDiagnostics.toAiPrompt()
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(content), null)
        onCopied()
    }
}

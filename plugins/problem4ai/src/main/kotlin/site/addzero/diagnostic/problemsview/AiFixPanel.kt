package site.addzero.diagnostic.problemsview

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import site.addzero.diagnostic.config.DiagnosticExclusionConfig
import site.addzero.diagnostic.model.DiagnosticItem
import site.addzero.diagnostic.model.DiagnosticSeverity
import site.addzero.diagnostic.model.FileDiagnostics
import site.addzero.diagnostic.service.DiagnosticCollectorService
import site.addzero.diagnostic.service.GlobalDiagnosticCache
import site.addzero.diagnostic.ui.ExclusionConfigDialog
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel

/**
 * 左右分栏布局的 Problem4AI 面板
 * 左侧：文件列表（Master）
 * 右侧：问题详情（Detail）
 */
class AiFixPanel(private val project: Project) : JPanel(BorderLayout()) {

    // 左侧文件列表
    private val fileListModel = DefaultListModel<FileDiagnostics>()
    private val fileList = JBList(fileListModel)

    // 右侧问题列表
    private val problemListModel = DefaultListModel<DiagnosticItem>()
    private val problemList = JBList(problemListModel)

    private val statusLabel = JBLabel("等待扫描开始...")
    private val progressLabel = JBLabel("")

    private var currentDiagnostics: List<FileDiagnostics> = emptyList()
    private val diagnosticsListener: (List<FileDiagnostics>) -> Unit = { updatePanel(it) }
    private val progressListener: (DiagnosticCollectorService.ScanProgress) -> Unit = { updateProgress(it) }

    private val globalCache = GlobalDiagnosticCache.getInstance(project)
    private val collectorService = DiagnosticCollectorService.getInstance(project)

    init {
        setupUI()
        setupAutoRefresh()
    }

    private fun setupUI() {
        // 工具栏
        val toolbar = JToolBar()
        toolbar.isFloatable = false

        val refreshButton = JButton("刷新", AllIcons.Actions.Refresh)
        refreshButton.addActionListener { refreshDiagnostics() }
        toolbar.add(refreshButton)

        toolbar.addSeparator()

        val copyAllButton = JButton("复制全部", AllIcons.Actions.Copy)
        copyAllButton.toolTipText = "复制所有诊断信息"
        copyAllButton.addActionListener { copyAllToClipboard() }
        toolbar.add(copyAllButton)

        val copyFileButton = JButton("复制当前文件", AllIcons.Actions.Copy)
        copyFileButton.toolTipText = "复制当前选中文件的问题"
        copyFileButton.addActionListener { copyCurrentFile() }
        toolbar.add(copyFileButton)

        val copyProblemButton = JButton("复制当前问题", AllIcons.Actions.Copy)
        copyProblemButton.toolTipText = "复制当前选中的单个问题"
        copyProblemButton.addActionListener { copyCurrentProblem() }
        toolbar.add(copyProblemButton)

        toolbar.addSeparator()

        val configButton = JButton("配置", AllIcons.General.Settings)
        configButton.toolTipText = "配置排除规则"
        configButton.addActionListener { showConfigDialog() }
        toolbar.add(configButton)

        add(toolbar, BorderLayout.NORTH)

        // 左右分栏面板
        val splitter = JBSplitter(false, 0.4f) // 垂直分割，左侧占40%
        splitter.firstComponent = createFilePanel()
        splitter.secondComponent = createProblemPanel()
        add(splitter, BorderLayout.CENTER)

        // 状态栏
        val statusBar = JPanel(FlowLayout(FlowLayout.LEFT))
        statusBar.add(statusLabel)
        statusBar.add(JSeparator(SwingConstants.VERTICAL))
        statusBar.add(progressLabel)
        add(statusBar, BorderLayout.SOUTH)
    }

    private fun createFilePanel(): JComponent {
        fileList.cellRenderer = FileListRenderer()
        fileList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        fileList.addListSelectionListener { _ ->
            val selected = fileList.selectedValue
            if (selected != null) {
                updateProblemList(selected)
            } else {
                problemListModel.clear()
            }
        }

        fileList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    fileList.selectedValue?.let { fileDiag ->
                        val line = fileDiag.items.firstOrNull()?.lineNumber ?: 1
                        navigateToFile(fileDiag.file, line)
                    }
                }
            }

            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showFileContextMenu(e)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showFileContextMenu(e)
                }
            }
        })

        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("文件列表")
        panel.add(JBScrollPane(fileList), BorderLayout.CENTER)
        return panel
    }

    private fun createProblemPanel(): JComponent {
        problemList.cellRenderer = ProblemListRenderer()
        problemList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        problemList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val fileDiag = fileList.selectedValue
                    val problem = problemList.selectedValue
                    if (fileDiag != null && problem != null) {
                        navigateToFile(fileDiag.file, problem.lineNumber)
                    }
                }
            }

            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showProblemContextMenu(e)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showProblemContextMenu(e)
                }
            }
        })

        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("问题详情")
        panel.add(JBScrollPane(problemList), BorderLayout.CENTER)
        return panel
    }

    private fun setupAutoRefresh() {
        collectorService.addListener(diagnosticsListener)
        collectorService.addProgressListener(progressListener)
        // 初始加载
        loadFromGlobalCache()
    }

    private fun updateProgress(progress: DiagnosticCollectorService.ScanProgress) {
        SwingUtilities.invokeLater {
            if (progress.isScanning) {
                val percent = if (progress.totalCount > 0) {
                    (progress.scannedCount * 100 / progress.totalCount)
                } else 0
                progressLabel.text = "正在扫描: ${progress.currentFile} (${progress.scannedCount}/${progress.totalCount}, ${percent}%)"
                progressLabel.icon = AllIcons.Actions.Refresh
            } else {
                progressLabel.text = "扫描完成"
                progressLabel.icon = null
            }
        }
    }

    private fun loadFromGlobalCache() {
        val diagnostics = globalCache.getAllDiagnostics()
        updatePanel(diagnostics)
    }

    private fun updatePanel(diagnostics: List<FileDiagnostics>) {
        SwingUtilities.invokeLater {
            currentDiagnostics = diagnostics

            // 保存当前选中的文件
            val selectedFile = fileList.selectedValue?.file

            // 更新文件列表
            fileListModel.clear()
            diagnostics.sortedBy { it.file.path }.forEach { fileListModel.addElement(it) }

            // 恢复选中或默认选中第一个
            if (selectedFile != null) {
                val index = (0 until fileListModel.size()).find { fileListModel.getElementAt(it).file == selectedFile }
                if (index != null) {
                    fileList.selectedIndex = index
                }
            } else if (fileListModel.size() > 0) {
                fileList.selectedIndex = 0
            }

            // 更新状态
            val errorCount = diagnostics.sumOf { it.items.count { item -> item.severity == DiagnosticSeverity.ERROR } }
            val warningCount = diagnostics.sumOf { it.items.count { item -> item.severity == DiagnosticSeverity.WARNING } }

            statusLabel.text = if (diagnostics.isEmpty()) {
                "扫描完成: 未发现错误或警告"
            } else {
                "共 ${diagnostics.size} 个文件, $errorCount 个错误, $warningCount 个警告"
            }
        }
    }

    private fun updateProblemList(fileDiagnostics: FileDiagnostics) {
        problemListModel.clear()
        fileDiagnostics.items.sortedBy { it.lineNumber }.forEach { problemListModel.addElement(it) }
    }

    private fun refreshDiagnostics() {
        // 触发增量扫描处理队列中的文件
        collectorService.triggerIncrementalScan()
    }

    private fun navigateToFile(file: VirtualFile, line: Int) {
        val descriptor = OpenFileDescriptor(project, file, line - 1, 0)
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
    }

    private fun copyAllToClipboard() {
        if (currentDiagnostics.isEmpty()) {
            statusLabel.text = "没有可复制的内容"
            return
        }
        val content = buildPromptContent(currentDiagnostics)
        copyToClipboard(content)
    }

    private fun copyCurrentFile() {
        val fileDiag = fileList.selectedValue
        if (fileDiag == null) {
            statusLabel.text = "请先选择一个文件"
            return
        }
        val content = buildPromptContent(listOf(fileDiag))
        copyToClipboard(content)
    }

    private fun copyCurrentProblem() {
        val fileDiag = fileList.selectedValue
        val problem = problemList.selectedValue
        if (fileDiag == null || problem == null) {
            statusLabel.text = "请先选择一个文件和问题"
            return
        }
        val singleItemDiag = fileDiag.copy(items = listOf(problem))
        val content = buildPromptContent(listOf(singleItemDiag))
        copyToClipboard(content)
    }

    private fun buildPromptContent(diagnostics: List<FileDiagnostics>): String = buildString {
        appendLine("请帮我修复以下编译问题：")
        appendLine()
        diagnostics.forEach { fileDiag ->
            appendLine("=== 文件: ${fileDiag.file.name} ===")
            fileDiag.items.forEachIndexed { index, item ->
                appendLine("问题${index + 1}:")
                appendLine("  行号: ${item.lineNumber}")
                appendLine("  类型: ${if (item.severity == DiagnosticSeverity.ERROR) "错误" else "警告"}")
                appendLine("  内容: ${item.message}")
            }
            appendLine()
        }
    }

    private fun copyToClipboard(content: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(content), null)
        statusLabel.text = "已复制到剪贴板"
    }

    private fun showConfigDialog() {
        val config = DiagnosticExclusionConfig.getInstance(project)
        val dialog = ExclusionConfigDialog(project, config)
        if (dialog.showAndGet()) {
            refreshDiagnostics()
        }
    }

    // 文件列表渲染器
    private class FileListRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?, value: Any?, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val fileDiag = value as? FileDiagnostics
            if (fileDiag != null) {
                val errorCount = fileDiag.items.count { it.severity == DiagnosticSeverity.ERROR }
                val warningCount = fileDiag.items.count { it.severity == DiagnosticSeverity.WARNING }
                text = "${fileDiag.file.name} (${errorCount}E, ${warningCount}W)"
                icon = when {
                    errorCount > 0 -> AllIcons.General.Error
                    warningCount > 0 -> AllIcons.General.Warning
                    else -> AllIcons.FileTypes.Unknown
                }
            }
            return component
        }
    }

    // 问题列表渲染器
    private class ProblemListRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?, value: Any?, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val item = value as? DiagnosticItem
            if (item != null) {
                text = "行 ${item.lineNumber}: ${item.message}"
                icon = if (item.severity == DiagnosticSeverity.ERROR) {
                    AllIcons.General.Error
                } else {
                    AllIcons.General.Warning
                }
            }
            return component
        }
    }

    // ==================== 右键菜单 ====================

    private fun showFileContextMenu(e: MouseEvent) {
        val index = fileList.locationToIndex(e.point)
        if (index >= 0) {
            fileList.selectedIndex = index
        }

        val fileDiag = fileList.selectedValue ?: return

        val popup = JPopupMenu()

        // 排除此文件
        popup.add(JMenuItem("排除此文件").apply {
            addActionListener { excludeFile(fileDiag.file) }
        })

        // 排除同名文件
        popup.add(JMenuItem("排除所有 ${fileDiag.file.name}").apply {
            addActionListener { excludePattern("**/${fileDiag.file.name}") }
        })

        // 排除此目录
        popup.add(JMenuItem("排除此目录").apply {
            addActionListener { excludePattern("${fileDiag.file.parent?.path}/**") }
        })

        popup.show(fileList, e.x, e.y)
    }

    private fun showProblemContextMenu(e: MouseEvent) {
        val index = problemList.locationToIndex(e.point)
        if (index >= 0) {
            problemList.selectedIndex = index
        }

        val problem = problemList.selectedValue ?: return
        val fileDiag = fileList.selectedValue ?: return

        val popup = JPopupMenu()

        // 排除此错误消息
        popup.add(JMenuItem("排除此类错误").apply {
            addActionListener { excludeErrorMessage(problem.message) }
        })

        // 排除此文件
        popup.add(JMenuItem("排除此文件").apply {
            addActionListener { excludeFile(fileDiag.file) }
        })

        popup.show(problemList, e.x, e.y)
    }

    private fun excludeFile(file: VirtualFile) {
        val config = DiagnosticExclusionConfig.getInstance(project)
        config.addCustomPattern(file.path)
        refreshDiagnostics()
        statusLabel.text = "已排除文件: ${file.name}"
    }

    private fun excludePattern(pattern: String) {
        val config = DiagnosticExclusionConfig.getInstance(project)
        config.addCustomPattern(pattern)
        refreshDiagnostics()
        statusLabel.text = "已排除: $pattern"
    }

    private fun excludeErrorMessage(message: String) {
        // TODO: 实现按错误消息排除，需要在 DiagnosticItem 中添加 messagePattern 字段
        statusLabel.text = "按错误消息排除功能待实现"
    }
}

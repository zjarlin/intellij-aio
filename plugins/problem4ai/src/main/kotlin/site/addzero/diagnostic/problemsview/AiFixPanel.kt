package site.addzero.diagnostic.problemsview

import com.intellij.icons.AllIcons
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
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
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel

/**
 * 左右分栏布局的 Problem4AI 面板
 * 左侧：文件列表（Master）
 * 右侧：问题详情（Detail）
 */
class AiFixPanel(private val project: Project) : JPanel(BorderLayout()) {
    companion object {
        private val LOG: Logger = Logger.getInstance(AiFixPanel::class.java)
    }

    private data class CliTarget(
        val label: String,
        val commandTemplate: String
    )

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

    private val exclusionConfig = DiagnosticExclusionConfig.getInstance(project)
    private val globalCache = GlobalDiagnosticCache.getInstance(project)
    private val collectorService = DiagnosticCollectorService.getInstance(project)
    private lateinit var cliButtonPanel: JPanel

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

        cliButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
        }
        toolbar.add(cliButtonPanel)
        rebuildCliButtons()

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
        setupEditorSelectionSync()
        LOG.info("[Problem4AI][Panel] listeners registered for project=${project.name}")
        // 初始加载
        loadFromGlobalCache()
        // 面板初始化时兜底触发一次全量扫描
        LOG.info("[Problem4AI][Panel] trigger full scan on panel init")
        collectorService.performFullScan()
    }

    private fun setupEditorSelectionSync() {
        val connection = project.messageBus.connect(project)
        connection.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    LOG.debug("[Problem4AI][Panel] editor selection changed: ${event.newFile?.path ?: "<none>"}")
                    syncSelectionToFile(event.newFile)
                }
            }
        )
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
        LOG.info("[Problem4AI][Panel] load cache diagnostics=${diagnostics.size}")
        updatePanel(diagnostics)
    }

    private fun updatePanel(diagnostics: List<FileDiagnostics>) {
        SwingUtilities.invokeLater {
            currentDiagnostics = diagnostics

            // 保存当前选中的文件
            val selectedFile = fileList.selectedValue?.file
            val activeEditorFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()

            // 更新文件列表
            fileListModel.clear()
            diagnostics.sortedBy { it.file.path }.forEach { fileListModel.addElement(it) }
            LOG.debug(
                "[Problem4AI][Panel] rebuild list diagnostics=${diagnostics.size} modelSize=${fileListModel.size()} activeEditor=${activeEditorFile?.path ?: "<none>"}"
            )

            // 优先选中当前编辑器标签页对应文件
            val selectedByEditor = syncSelectionToFile(activeEditorFile, clearWhenMissing = true)

            // 如果当前编辑器文件不在问题列表，再回退到旧选中或首项
            if (!selectedByEditor) {
                val restoredByPrevious = syncSelectionToFile(selectedFile)
                if (!restoredByPrevious && fileListModel.size() > 0) {
                    fileList.selectedIndex = 0
                }
            }

            // 更新状态
            val errorCount = diagnostics.sumOf { it.items.count { item -> item.severity == DiagnosticSeverity.ERROR } }
            val warningCount = diagnostics.sumOf { it.items.count { item -> item.severity == DiagnosticSeverity.WARNING } }

            statusLabel.text = if (diagnostics.isEmpty()) {
                "扫描完成: 未发现错误或警告"
            } else {
                "共 ${diagnostics.size} 个文件, $errorCount 个错误, $warningCount 个警告"
            }
            LOG.debug(
                "[Problem4AI][Panel] status updated files=${diagnostics.size} errors=$errorCount warnings=$warningCount selected=${fileList.selectedValue?.file?.path ?: "<none>"}"
            )
        }
    }

    private fun syncSelectionToFile(file: VirtualFile?, clearWhenMissing: Boolean = false): Boolean {
        if (file == null) {
            LOG.debug("[Problem4AI][Panel] syncSelection skip: null file")
            return false
        }

        val index = (0 until fileListModel.size()).firstOrNull { fileListModel.getElementAt(it).file == file }
        if (index == null) {
            if (clearWhenMissing) {
                fileList.clearSelection()
                problemListModel.clear()
                LOG.debug("[Problem4AI][Panel] syncSelection clear because missing file=${file.path}")
            }
            return false
        }

        if (fileList.selectedIndex != index) {
            fileList.selectedIndex = index
            fileList.ensureIndexIsVisible(index)
            LOG.debug("[Problem4AI][Panel] syncSelection selected index=$index file=${file.path}")
        }
        return true
    }

    private fun updateProblemList(fileDiagnostics: FileDiagnostics) {
        problemListModel.clear()
        fileDiagnostics.items.sortedBy { it.lineNumber }.forEach { problemListModel.addElement(it) }
    }

    private fun refreshDiagnostics() {
        LOG.info("[Problem4AI][Panel] manual refresh clicked -> full scan")
        collectorService.performFullScan()
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

    private fun rebuildCliButtons() {
        if (!::cliButtonPanel.isInitialized) {
            return
        }
        cliButtonPanel.removeAll()

        parseCliTargets().forEach { target ->
            val button = JButton(target.label, AllIcons.RunConfigurations.TestState.Run).apply {
                toolTipText = "执行: ${target.commandTemplate}（支持 {input} / {project}）"
                addActionListener { sendToCliByPrefix(target) }
            }
            cliButtonPanel.add(button)
        }

        cliButtonPanel.revalidate()
        cliButtonPanel.repaint()
    }

    private fun sendToCliByPrefix(target: CliTarget) {
        val payload = buildPayloadForCli()
        if (payload == null) {
            statusLabel.text = "没有可发送的问题内容"
            return
        }

        val commandTemplate = target.commandTemplate
        val label = target.label

        if (commandTemplate.isBlank()) {
            statusLabel.text = "CLI前缀为空，无法执行"
            return
        }

        if (label.isBlank()) {
            statusLabel.text = "CLI标签为空，无法执行"
            return
        }

        statusLabel.text = "正在执行 $label ..."
        LOG.info("[Problem4AI][Panel] execute CLI label=$label template=$commandTemplate")

        ApplicationManager.getApplication().executeOnPooledThread {
            val tempFile = Files.createTempFile("problem4ai-cli-", ".txt")
            try {
                Files.write(tempFile, payload.toByteArray(StandardCharsets.UTF_8))

                val basePath = project.basePath ?: System.getProperty("user.home")
                val command = resolveCliCommand(commandTemplate, tempFile, basePath)
                if (command.isBlank()) {
                    SwingUtilities.invokeLater {
                        statusLabel.text = "$label 命令为空，取消执行"
                    }
                    runCatching { Files.deleteIfExists(tempFile) }
                    return@executeOnPooledThread
                }

                val terminalCommand = buildTerminalCommand(command, tempFile, basePath)
                val executedInTerminal = runOnEdtAndGet {
                    executeInIdeaTerminal(label, basePath, terminalCommand)
                }

                if (executedInTerminal) {
                    scheduleTempFileCleanup(tempFile)
                    SwingUtilities.invokeLater {
                        statusLabel.text = "已在 Terminal 执行 $label"
                    }
                    return@executeOnPooledThread
                }

                LOG.warn("[Problem4AI][Panel] terminal execution unavailable, fallback to background process")
                executeInBackgroundProcess(label, basePath, command, tempFile)
            } catch (e: Exception) {
                LOG.warn("[Problem4AI][Panel] execute CLI command failed", e)
                SwingUtilities.invokeLater {
                    statusLabel.text = "$label 执行异常: ${e.message}"
                    Messages.showErrorDialog(project, e.message ?: "未知异常", "发送到CLI: $label")
                }
                runCatching { Files.deleteIfExists(tempFile) }
            }
        }
    }

    private fun executeInIdeaTerminal(label: String, workDir: String, command: String): Boolean {
        return try {
            val managerClass = Class.forName("org.jetbrains.plugins.terminal.TerminalToolWindowManager")
            val getInstanceMethod = managerClass.getMethod("getInstance", Project::class.java)
            val manager = getInstanceMethod.invoke(null, project) ?: return false

            val toolWindow = runCatching {
                managerClass.getMethod("getToolWindow").invoke(manager) as? com.intellij.openapi.wm.ToolWindow
            }.getOrNull()
            toolWindow?.show()
            toolWindow?.activate(null, true, true)

            val widget = createTerminalWidget(managerClass, manager, label, workDir) ?: return false
            val executeMethod = widget.javaClass.methods.firstOrNull {
                it.name == "sendCommandToExecute" && it.parameterCount == 1
            } ?: return false
            executeMethod.invoke(widget, command)
            widget.javaClass.methods.firstOrNull {
                it.name == "requestFocus" && it.parameterCount == 0
            }?.invoke(widget)
            true
        } catch (t: Throwable) {
            LOG.warn("[Problem4AI][Panel] execute in terminal failed", t)
            false
        }
    }

    private fun createTerminalWidget(managerClass: Class<*>, manager: Any, label: String, workDir: String): Any? {
        val tabName = "Problem4AI-$label"
        val methods = managerClass.methods

        methods.firstOrNull { it.name == "createShellWidget" && it.parameterCount == 4 }?.let {
            return it.invoke(manager, workDir, tabName, true, true)
        }
        methods.firstOrNull { it.name == "createLocalShellWidget" && it.parameterCount == 4 }?.let {
            return it.invoke(manager, workDir, tabName, true, true)
        }
        methods.firstOrNull { it.name == "createLocalShellWidget" && it.parameterCount == 3 }?.let {
            return it.invoke(manager, workDir, tabName, true)
        }
        methods.firstOrNull { it.name == "createLocalShellWidget" && it.parameterCount == 2 }?.let {
            return it.invoke(manager, workDir, tabName)
        }
        methods.firstOrNull { it.name == "createNewSession" && it.parameterCount == 0 }?.let {
            return it.invoke(manager)
        }

        return null
    }

    private fun buildTerminalCommand(command: String, inputFile: Path, projectPath: String): String {
        val inputPath = inputFile.toAbsolutePath().toString()
        return if (isWindowsShell()) {
            val escapedInput = inputPath.replace("\"", "\"\"")
            val escapedProject = projectPath.replace("\"", "\"\"")
            "set \"PROBLEM4AI_INPUT=$escapedInput\" && set \"PROBLEM4AI_PROJECT=$escapedProject\" && $command"
        } else {
            "PROBLEM4AI_INPUT=${quoteForShell(inputPath)} PROBLEM4AI_PROJECT=${quoteForShell(projectPath)} $command"
        }
    }

    private fun executeInBackgroundProcess(label: String, basePath: String, command: String, tempFile: Path) {
        try {
            val commandLine = buildShellCommand(command).withWorkDirectory(basePath)
            commandLine.environment["PROBLEM4AI_INPUT"] = tempFile.toAbsolutePath().toString()
            commandLine.environment["PROBLEM4AI_PROJECT"] = basePath

            val output = CapturingProcessHandler(commandLine).runProcess(120_000)
            SwingUtilities.invokeLater {
                when {
                    output.isTimeout -> {
                        statusLabel.text = "CLI执行超时（120s）"
                        Messages.showWarningDialog(project, "命令执行超时（120s）", "发送到CLI: $label")
                    }
                    output.exitCode == 0 -> {
                        statusLabel.text = "$label 执行成功"
                        if (output.stdout.isNotBlank()) {
                            Messages.showInfoMessage(project, output.stdout.take(2000), "$label 输出（前2000字符）")
                        }
                    }
                    else -> {
                        statusLabel.text = "$label 执行失败: exit=${output.exitCode}"
                        val errorText = (output.stderr.ifBlank { output.stdout }).take(2000)
                        Messages.showErrorDialog(project, errorText.ifBlank { "命令执行失败" }, "发送到CLI: $label")
                    }
                }
            }
        } finally {
            runCatching { Files.deleteIfExists(tempFile) }
        }
    }

    private fun scheduleTempFileCleanup(tempFile: Path) {
        AppExecutorUtil.getAppScheduledExecutorService().schedule(
            { runCatching { Files.deleteIfExists(tempFile) } },
            10,
            TimeUnit.MINUTES
        )
    }

    private fun <T> runOnEdtAndGet(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) {
            return action()
        }
        var value: T? = null
        var error: Throwable? = null
        ApplicationManager.getApplication().invokeAndWait {
            try {
                value = action()
            } catch (t: Throwable) {
                error = t
            }
        }
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun parseCliTargets(): List<CliTarget> {
        return exclusionConfig.getAiCliPrefixes()
            .mapNotNull { parseCliTarget(it) }
    }

    private fun parseCliTarget(raw: String): CliTarget? {
        val line = raw.trim()
        if (line.isBlank()) {
            return null
        }

        val parts = line.split("|").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            return null
        }

        val label = parts.first()
        if (parts.size == 1) {
            return CliTarget(label = label, commandTemplate = label)
        }

        val commandByOs = mutableMapOf<String, String>()
        parts.drop(1).forEach { segment ->
            val index = segment.indexOf(':')
            if (index <= 0 || index >= segment.lastIndex) {
                return@forEach
            }
            val osKey = normalizeOsKey(segment.substring(0, index))
            val command = segment.substring(index + 1).trim()
            if (command.isNotBlank()) {
                commandByOs[osKey] = command
            }
        }

        val current = currentOsKey()
        val selected = commandByOs[current]
            ?: commandByOs["default"]
            ?: commandByOs["all"]
            ?: label

        return CliTarget(label = label, commandTemplate = selected)
    }

    private fun currentOsKey(): String {
        val name = System.getProperty("os.name").lowercase()
        return when {
            name.contains("win") -> "win"
            name.contains("mac") || name.contains("darwin") -> "mac"
            else -> "linux"
        }
    }

    private fun normalizeOsKey(raw: String): String {
        return when (raw.trim().lowercase()) {
            "windows", "win" -> "win"
            "mac", "macos", "osx", "darwin" -> "mac"
            "linux", "unix" -> "linux"
            "default", "all", "*" -> "default"
            else -> raw.trim().lowercase()
        }
    }

    private fun resolveCliCommand(commandTemplate: String, inputPath: Path, projectPath: String): String {
        val quotedInput = quoteForShell(inputPath.toAbsolutePath().toString())
        val quotedProject = quoteForShell(projectPath)
        var command = commandTemplate
            .replace("{input}", quotedInput)
            .replace("{project}", quotedProject)
            .trim()

        val hasInputBinding = commandTemplate.contains("{input}") ||
            commandTemplate.contains("\$PROBLEM4AI_INPUT") ||
            commandTemplate.contains("%PROBLEM4AI_INPUT%") ||
            commandTemplate.contains("\${PROBLEM4AI_INPUT}")

        if (!hasInputBinding) {
            command += if (isWindowsShell()) {
                " < \"%PROBLEM4AI_INPUT%\""
            } else {
                " < \"\$PROBLEM4AI_INPUT\""
            }
        }

        return command
    }

    private fun quoteForShell(value: String): String {
        return if (isWindowsShell()) {
            "\"${value.replace("\"", "\\\"")}\""
        } else {
            "'${value.replace("'", "'\"'\"'")}'"
        }
    }

    private fun isWindowsShell(): Boolean {
        return System.getProperty("os.name").lowercase().contains("windows")
    }

    private fun buildShellCommand(command: String): GeneralCommandLine {
        return if (isWindowsShell()) {
            GeneralCommandLine("cmd.exe", "/c", command)
        } else {
            GeneralCommandLine("/bin/sh", "-lc", command)
        }
    }
    private fun buildPayloadForCli(): String? {
        val selectedFileDiagnostics = fileList.selectedValue
        val selectedProblem = problemList.selectedValue

        return when {
            selectedFileDiagnostics != null && selectedProblem != null -> {
                val singleItemDiagnostics = selectedFileDiagnostics.copy(items = listOf(selectedProblem))
                buildPromptContent(listOf(singleItemDiagnostics))
            }
            selectedFileDiagnostics != null -> buildPromptContent(listOf(selectedFileDiagnostics))
            currentDiagnostics.isNotEmpty() -> buildPromptContent(currentDiagnostics)
            else -> null
        }
    }

    private fun buildPromptContent(diagnostics: List<FileDiagnostics>): String = buildString {
        diagnostics.forEach { fileDiag ->
            appendLine("problem in \"${fileDiag.file.path}\"")
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
        val dialog = ExclusionConfigDialog(project, exclusionConfig)
        if (dialog.showAndGet()) {
            rebuildCliButtons()
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
        exclusionConfig.addCustomPattern(file.path)
        refreshDiagnostics()
        statusLabel.text = "已排除文件: ${file.name}"
    }

    private fun excludePattern(pattern: String) {
        exclusionConfig.addCustomPattern(pattern)
        refreshDiagnostics()
        statusLabel.text = "已排除: $pattern"
    }

    private fun excludeErrorMessage(message: String) {
        // TODO: 实现按错误消息排除，需要在 DiagnosticItem 中添加 messagePattern 字段
        statusLabel.text = "按错误消息排除功能待实现"
    }
}

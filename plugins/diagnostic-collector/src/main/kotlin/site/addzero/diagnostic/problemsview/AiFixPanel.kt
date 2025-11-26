package site.addzero.diagnostic.problemsview

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import site.addzero.diagnostic.model.DiagnosticSeverity
import site.addzero.diagnostic.model.FileDiagnostics
import site.addzero.diagnostic.service.DiagnosticCollectorService
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

class AiFixPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val tree: Tree
    private val treeModel: DefaultTreeModel
    private val rootNode = DefaultMutableTreeNode("Problems")
    private val statusLabel = JBLabel("点击刷新按钮收集诊断信息")
    
    private var currentDiagnostics: List<FileDiagnostics> = emptyList()
    private val diagnosticsListener: (List<FileDiagnostics>) -> Unit = { updateTree(it) }
    
    init {
        treeModel = DefaultTreeModel(rootNode)
        tree = Tree(treeModel)
        tree.isRootVisible = false
        tree.cellRenderer = DiagnosticCellRenderer()
        
        setupUI()
        setupAutoRefresh()
    }
    
    private fun setupUI() {
        val toolbar = JToolBar()
        toolbar.isFloatable = false
        
        val refreshButton = JButton("刷新", AllIcons.Actions.Refresh)
        refreshButton.addActionListener { refreshDiagnostics() }
        toolbar.add(refreshButton)
        
        toolbar.addSeparator()
        
        val copyAllButton = JButton("复制全部", AllIcons.Actions.Copy)
        copyAllButton.toolTipText = "复制所有诊断信息生成AI修复提示词"
        copyAllButton.addActionListener { copyAllToClipboard() }
        toolbar.add(copyAllButton)
        
        val copyErrorsButton = JButton("复制错误", AllIcons.General.Error)
        copyErrorsButton.addActionListener { copyErrorsToClipboard() }
        toolbar.add(copyErrorsButton)
        
        val copyWarningsButton = JButton("复制警告", AllIcons.General.Warning)
        copyWarningsButton.addActionListener { copyWarningsToClipboard() }
        toolbar.add(copyWarningsButton)
        
        add(toolbar, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)
        
        val statusBar = JPanel(FlowLayout(FlowLayout.LEFT))
        statusBar.add(statusLabel)
        add(statusBar, BorderLayout.SOUTH)
        
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    handleDoubleClick(e)
                }
            }
        })
    }
    
    private fun setupAutoRefresh() {
        DiagnosticCollectorService.getInstance(project).addListener(diagnosticsListener)
    }
    
    private fun refreshDiagnostics() {
        statusLabel.text = "正在收集诊断信息..."
        DiagnosticCollectorService.getInstance(project).collectDiagnostics { updateTree(it) }
    }
    
    private fun updateTree(diagnostics: List<FileDiagnostics>) {
        SwingUtilities.invokeLater {
            currentDiagnostics = diagnostics
            rootNode.removeAllChildren()
            
            val service = DiagnosticCollectorService.getInstance(project)
            val errorFiles = service.getErrorFiles(diagnostics)
            val warningFiles = service.getWarningFiles(diagnostics)
            
            // 错误节点
            if (errorFiles.isNotEmpty()) {
                val errorsNode = DefaultMutableTreeNode(CategoryNode("Errors", errorFiles.sumOf { it.items.size }))
                errorFiles.forEach { fileDiag ->
                    val fileNode = DefaultMutableTreeNode(FileNode(fileDiag, DiagnosticSeverity.ERROR))
                    fileDiag.items.forEach { item ->
                        fileNode.add(DefaultMutableTreeNode(ItemNode(item.lineNumber, item.message, item.severity)))
                    }
                    errorsNode.add(fileNode)
                }
                rootNode.add(errorsNode)
            }
            
            // 警告节点
            if (warningFiles.isNotEmpty()) {
                val warningsNode = DefaultMutableTreeNode(CategoryNode("Warnings", warningFiles.sumOf { it.items.size }))
                warningFiles.forEach { fileDiag ->
                    val fileNode = DefaultMutableTreeNode(FileNode(fileDiag, DiagnosticSeverity.WARNING))
                    fileDiag.items.forEach { item ->
                        fileNode.add(DefaultMutableTreeNode(ItemNode(item.lineNumber, item.message, item.severity)))
                    }
                    warningsNode.add(fileNode)
                }
                rootNode.add(warningsNode)
            }
            
            treeModel.reload()
            TreeUtil.expandAll(tree)
            
            val errorCount = errorFiles.sumOf { it.items.size }
            val warningCount = warningFiles.sumOf { it.items.size }
            statusLabel.text = "共 ${diagnostics.size} 个文件, $errorCount 个错误, $warningCount 个警告"
        }
    }
    
    private fun handleDoubleClick(e: MouseEvent) {
        val path = tree.getPathForLocation(e.x, e.y)
        val node = path?.lastPathComponent as? DefaultMutableTreeNode ?: return
        
        when (val userObject = node.userObject) {
            is FileNode -> navigateToFile(userObject.fileDiagnostics.file, userObject.fileDiagnostics.items.firstOrNull()?.lineNumber ?: 1)
            is ItemNode -> {
                val parentNode = node.parent as? DefaultMutableTreeNode
                val fileNode = parentNode?.userObject as? FileNode
                if (fileNode != null) {
                    navigateToFile(fileNode.fileDiagnostics.file, userObject.lineNumber)
                }
            }
        }
    }
    
    private fun navigateToFile(file: VirtualFile, line: Int) {
        val descriptor = OpenFileDescriptor(project, file, line - 1, 0)
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
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
    
    // 数据节点类
    data class CategoryNode(val name: String, val count: Int)
    data class FileNode(val fileDiagnostics: FileDiagnostics, val severity: DiagnosticSeverity)
    data class ItemNode(val lineNumber: Int, val message: String, val severity: DiagnosticSeverity)
    
    private class DiagnosticCellRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree?,
            value: Any?,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): Component {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            
            val node = value as? DefaultMutableTreeNode ?: return this
            
            when (val userObject = node.userObject) {
                is CategoryNode -> {
                    icon = if (userObject.name == "Errors") AllIcons.General.Error else AllIcons.General.Warning
                    text = "${userObject.name} (${userObject.count})"
                }
                is FileNode -> {
                    icon = if (userObject.severity == DiagnosticSeverity.ERROR) AllIcons.General.Error else AllIcons.General.Warning
                    text = "${userObject.fileDiagnostics.file.name} - ${userObject.fileDiagnostics.items.size} 个问题"
                }
                is ItemNode -> {
                    icon = if (userObject.severity == DiagnosticSeverity.ERROR) AllIcons.General.Error else AllIcons.General.Warning
                    text = "行 ${userObject.lineNumber}: ${userObject.message}"
                }
            }
            
            return this
        }
    }
}

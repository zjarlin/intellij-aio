package site.addzero.shitcode.toolwindow

import site.addzero.shitcode.service.CacheUpdateListener
import site.addzero.shitcode.service.ShitCodeCacheService
import site.addzero.shitcode.settings.ShitCodeSettingsService
import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JToolBar
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class ShitCodeToolWindow : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.setIcon(AllIcons.General.Warning)
        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(
            ShitCodePanel(project),
            "垃圾代码",
            false
        )
        contentManager.addContent(content)
    }
}

class ShitCodePanel(private val project: Project) : JPanel(BorderLayout()) {
    private val rootNode = DefaultMutableTreeNode("垃圾代码列表")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)
    private val cacheService = ShitCodeCacheService.getInstance(project)
    
    // 缓存更新监听器
    private val cacheUpdateListener = CacheUpdateListener {
        refreshTree()
    }

    init {
        tree.isRootVisible = true
        tree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION

        val toolbar = JToolBar()
        val refreshButton = JButton("刷新")
        val deleteButton = JButton("删除选中")
        val deleteAllButton = JButton("全部删除")
        toolbar.add(refreshButton)
        toolbar.add(deleteButton)
        toolbar.add(deleteAllButton)

        refreshButton.addActionListener {
            // 触发重新扫描
            cacheService.performFullScan()
        }

        deleteButton.addActionListener {
            deleteSelectedNodes()
        }

        deleteAllButton.addActionListener {
            val elements = cacheService.getAllElementsList()
            if (elements.isEmpty()) {
                Messages.showInfoMessage("没有找到需要删除的元素", "提示")
                return@addActionListener
            }

            val result = Messages.showYesNoDialog(
                project,
                "确定要删除全部${elements.size}个元素吗？",
                "确认删除",
                Messages.getQuestionIcon()
            )

            if (result == Messages.YES) {
                WriteCommandAction.runWriteCommandAction(project) {
                    try {
                        elements.forEach { it.psiElement.delete() }
                        // 重新扫描以更新缓存
                        cacheService.performFullScan()
                        Messages.showInfoMessage("删除成功", "提示")
                    } catch (e: Exception) {
                        Messages.showErrorDialog("删除失败: ${e.message}", "错误")
                    }
                }
            }
        }

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val path = tree.getPathForLocation(e.x, e.y)
                    path?.let { handleTreeNodeDoubleClick(it) }
                }
            }
        })

        add(toolbar, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)

        // 注册缓存更新监听器
        cacheService.addListener(cacheUpdateListener)
        
        // 初始加载
        refreshTree()
    }

    private fun refreshTree() {
        rootNode.removeAllChildren()
        
        // 从缓存获取数据
        val elementsMap = cacheService.getAllElements()
        @Suppress("UNUSED_VARIABLE")
        val statistics = cacheService.getStatistics()
        
        // 更新根节点显示统计信息
        val totalElements = elementsMap.values.sumOf { it.size }
        rootNode.userObject = "垃圾代码列表 ($totalElements 个元素)"

        elementsMap.forEach { (_, elements) ->
            val fileName = elements.firstOrNull()?.fileName ?: "Unknown File"
            val fileNode = DefaultMutableTreeNode("$fileName (${elements.size})")
            
            elements.forEach { element ->
                val elementNode = DefaultMutableTreeNode(ElementInfo(element.psiElement, element.name))
                fileNode.add(elementNode)
            }
            
            rootNode.add(fileNode)
        }

        treeModel.reload()
        tree.expandRow(0)
    }



    private fun handleTreeNodeDoubleClick(path: TreePath) {
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val userObject = node.userObject
        if (userObject is ElementInfo) {
            val element = userObject.element
            if (element is NavigatablePsiElement) {
                element.navigate(true)
            }
        }
    }

    private fun deleteSelectedNodes() {
        val paths = tree.selectionPaths ?: return
        val elementsToDelete = mutableListOf<PsiElement>()

        for (path in paths) {
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: continue
            val userObject = node.userObject
            if (userObject is ElementInfo) {
                elementsToDelete.add(userObject.element)
            }
        }

        if (elementsToDelete.isEmpty()) {
            Messages.showInfoMessage("请选择要删除的元素", "提示")
            return
        }

        val result = Messages.showYesNoDialog(
            project,
            "确定要删除选中的${elementsToDelete.size}个元素吗？",
            "确认删除",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            var success = true
            var errorMessage: String? = null

            WriteCommandAction.runWriteCommandAction(project) {
                try {
                    elementsToDelete.forEach { it.delete() }
                    // 重新扫描以更新缓存
                    cacheService.performFullScan()
                } catch (e: Exception) {
                    success = false
                    errorMessage = e.message
                }
            }

            if (success) {
                Messages.showInfoMessage("删除成功", "提示")
            } else {
                Messages.showErrorDialog("删除失败: $errorMessage", "错误")
            }
        }
    }
}

data class ElementInfo(val element: PsiElement, val displayName: String? = null) {
    override fun toString(): String {
        return displayName ?: when (element) {
            is PsiClass -> "类: ${element.name}"
            is PsiMethod -> "方法: ${element.name}"
            is PsiField -> "字段: ${element.name}"
            else -> element.toString()
        }
    }
}

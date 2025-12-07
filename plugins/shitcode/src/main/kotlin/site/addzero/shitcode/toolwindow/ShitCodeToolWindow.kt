package site.addzero.shitcode.toolwindow

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
import com.intellij.psi.util.PsiTreeUtil
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
    private val tree: Tree
    private val treeModel: DefaultTreeModel
    private val rootNode = DefaultMutableTreeNode("垃圾代码列表")

    init {
        treeModel = DefaultTreeModel(rootNode)
        tree = Tree(treeModel)
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
            refreshTree()
        }

        deleteButton.addActionListener {
            deleteSelectedNodes()
        }

        deleteAllButton.addActionListener {
            val elements = findAnnotatedElements()
            if (elements.isEmpty()) return@addActionListener

            val result = Messages.showYesNoDialog(
                project,
                "确定要删除全部${elements.size}个元素吗？",
                "确认删除",
                Messages.getQuestionIcon()
            )

            if (result == Messages.YES) {
                WriteCommandAction.runWriteCommandAction(project) {
                    try {
                        elements.forEach { it.delete() }
                        refreshTree()
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

        refreshTree()
    }

    private fun refreshTree() {
        rootNode.removeAllChildren()
        val elements = findAnnotatedElements()

        elements.groupBy { it.containingFile }.forEach { (file, elements) ->
            val fileNode = DefaultMutableTreeNode(file?.name ?: "Unknown File")
            elements.forEach { element ->
                val elementNode = DefaultMutableTreeNode(ElementInfo(element))
                fileNode.add(elementNode)
            }
            rootNode.add(fileNode)
        }

        treeModel.reload()
        tree.expandRow(0)
    }

    private fun findAnnotatedElements(): List<PsiElement> {
        if (DumbService.getInstance(project).isDumb) {
            Messages.showWarningDialog(
                project,
                "索引正在构建中，请稍后再试",
                "提示"
            )
            return emptyList()
        }

        val elements = mutableListOf<PsiElement>()
        // 使用 projectScope 并排除库文件
        val scope = GlobalSearchScope.projectScope(project)
        val psiManager = PsiManager.getInstance(project)
        val annotationName = ShitCodeSettingsService.getInstance().state.shitAnnotation

        ApplicationManager.getApplication().runReadAction {
            // 扫描 Kotlin 文件
            val ktFiles = com.intellij.psi.search.FileTypeIndex.getFiles(
                KotlinFileType.INSTANCE,
                scope
            )

            for (virtualFile in ktFiles) {
                // 跳过构建目录和测试资源
                if (virtualFile.path.contains("/build/") || 
                    virtualFile.path.contains("/out/") ||
                    virtualFile.path.contains("/.gradle/")) {
                    continue
                }
                
                val ktFile = psiManager.findFile(virtualFile) as? KtFile ?: continue
                
                // 扫描类
                ktFile.declarations.forEach { declaration ->
                    when (declaration) {
                        is KtClass -> {
                            if (hasAnnotation(declaration, annotationName)) {
                                elements.add(declaration)
                            }
                            // 扫描类中的成员
                            declaration.declarations.forEach { member ->
                                when (member) {
                                    is KtFunction -> {
                                        if (hasAnnotation(member, annotationName)) {
                                            elements.add(member)
                                        }
                                    }
                                    is KtProperty -> {
                                        if (hasAnnotation(member, annotationName)) {
                                            elements.add(member)
                                        }
                                    }
                                }
                            }
                        }
                        is KtFunction -> {
                            if (hasAnnotation(declaration, annotationName)) {
                                elements.add(declaration)
                            }
                        }
                        is KtProperty -> {
                            if (hasAnnotation(declaration, annotationName)) {
                                elements.add(declaration)
                            }
                        }
                    }
                }
            }

            // 扫描 Java 文件
            val javaFiles = com.intellij.psi.search.FileTypeIndex.getFiles(
                JavaFileType.INSTANCE,
                scope
            )

            for (virtualFile in javaFiles) {
                // 跳过构建目录和测试资源
                if (virtualFile.path.contains("/build/") || 
                    virtualFile.path.contains("/out/") ||
                    virtualFile.path.contains("/.gradle/")) {
                    continue
                }
                
                val javaFile = psiManager.findFile(virtualFile) as? PsiJavaFile ?: continue
                
                javaFile.classes.forEach { psiClass ->
                    if (hasJavaAnnotation(psiClass, annotationName)) {
                        elements.add(psiClass)
                    }
                    
                    // 扫描方法
                    psiClass.methods.forEach { method ->
                        if (hasJavaAnnotation(method, annotationName)) {
                            elements.add(method)
                        }
                    }
                    
                    // 扫描字段
                    psiClass.fields.forEach { field ->
                        if (hasJavaAnnotation(field, annotationName)) {
                            elements.add(field)
                        }
                    }
                }
            }
        }

        return elements
    }
    
    /**
     * 检查 Kotlin 元素是否有指定注解
     */
    private fun hasAnnotation(element: KtAnnotated, annotationName: String): Boolean {
        return element.annotationEntries.any { annotation ->
            val shortName = annotation.shortName?.asString()
            val fullName = annotation.typeReference?.text
            
            // 匹配短名称或完全限定名
            shortName == annotationName || 
            fullName == annotationName ||
            fullName?.endsWith(".$annotationName") == true
        }
    }
    
    /**
     * 检查 Java 元素是否有指定注解
     */
    private fun hasJavaAnnotation(element: PsiModifierListOwner, annotationName: String): Boolean {
        val annotations = element.modifierList?.annotations ?: return false
        return annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            val shortName = annotation.nameReferenceElement?.referenceName
            
            // 匹配短名称或完全限定名
            shortName == annotationName ||
            qualifiedName == annotationName ||
            qualifiedName?.endsWith(".$annotationName") == true
        }
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

        if (elementsToDelete.isEmpty()) return

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
                    refreshTree()
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

data class ElementInfo(val element: PsiElement) {
    override fun toString(): String {
        return when (element) {
            is KtClass -> "类: ${element.name}"
            is KtFunction -> "函数: ${element.name}"
            is KtProperty -> "属性: ${element.name}"
            is PsiClass -> "类: ${element.name}"
            is PsiMethod -> "方法: ${element.name}"
            is PsiField -> "字段: ${element.name}"
            else -> element.text
        }
    }
}

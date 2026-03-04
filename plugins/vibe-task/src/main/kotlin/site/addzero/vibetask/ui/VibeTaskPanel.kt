package site.addzero.vibetask.ui

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.showYesNoDialog
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.Content
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import site.addzero.vibetask.model.ProjectModule
import javax.swing.UIManager
import site.addzero.vibetask.model.VibeTask
import site.addzero.vibetask.service.VibeTaskService
import site.addzero.vibetask.actions.GeneratePromptAction
import site.addzero.vibetask.settings.TaskViewSettings
import site.addzero.vibetask.settings.TaskViewRule
import site.addzero.vibetask.settings.CustomView
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.tree.*

/**
 * 左右分栏的任务面板
 * 左侧：可编辑的视图树
 * 右侧：任务列表
 */
class VibeTaskPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val service = VibeTaskService.getInstance(project)
    private val settings = TaskViewSettings.getInstance()

    // 左侧视图树
    private lateinit var viewTree: JTree
    private val treeModel: DefaultTreeModel
    private val rootNode = DefaultMutableTreeNode("ROOT")

    // 右侧任务列表
    private val listModel = CollectionListModel<VibeTask>()
    private val taskList = JBList(listModel)

    // 当前选中的视图
    private var currentView: ViewNode = ViewNode.AllTasks

    // 当前状态筛选
    private var currentStatusFilter: String = "全部"

    // 检测到的模块
    private var detectedModules: List<ProjectModule> = emptyList()

    /**
     * 视图节点类型
     */
    sealed class ViewNode {
        abstract val name: String
        abstract val icon: String
        abstract val isEditable: Boolean
        abstract val isDeletable: Boolean

        object Global : ViewNode() {
            override val name = "全局备忘"
            override val icon = "🌍"
            override val isEditable = false
            override val isDeletable = false
        }

        object AllTasks : ViewNode() {
            override val name = "全部任务"
            override val icon = "✨"
            override val isEditable = false
            override val isDeletable = false
        }

        object ProjectLevel : ViewNode() {
            override val name = "项目级任务"
            override val icon = "📁"
            override val isEditable = false
            override val isDeletable = false
        }

        data class RuleGroup(
            val rule: TaskViewRule,
            val modules: List<ProjectModule> = emptyList()
        ) : ViewNode() {
            override val name get() = rule.name
            override val icon get() = rule.icon
            override val isEditable = true
            override val isDeletable = true
        }

        data class Module(
            val module: ProjectModule
        ) : ViewNode() {
            override val name get() = module.name
            override val icon get() = when (module.type) {
                ProjectModule.ModuleType.PLUGIN -> "🔌"
                ProjectModule.ModuleType.LIB -> "📚"
                ProjectModule.ModuleType.APP -> "🚀"
                ProjectModule.ModuleType.UNKNOWN -> "📁"
            }
            override val isEditable = false
            override val isDeletable = false
        }

        data class Custom(
            override val name: String,
            override val icon: String = "📂",
            val modulePaths: List<String> = emptyList(),
            val id: String = UUID.randomUUID().toString()
        ) : ViewNode() {
            override val isEditable = true
            override val isDeletable = true
        }

        // 模块任务分组（未分类模块）
        object ModuleTasks : ViewNode() {
            override val name = "模块任务"
            override val icon = "📦"
            override val isEditable = false
            override val isDeletable = false
        }
    }

    init {
        background = JBColor.namedColor("Panel.background", UIUtil.getPanelBackground())
        border = EmptyBorder(0, 0, 0, 0)

        treeModel = DefaultTreeModel(rootNode)

        // 先初始化 UI，再刷新数据
        initUI()
        // 使用 SwingUtilities.invokeLater 确保 UI 完全初始化后再加载数据
        SwingUtilities.invokeLater {
            refreshModules()
            refreshTasks()
        }
    }

    private fun refreshModules() {
        detectedModules = service.getProjectModules()
        rebuildViewTree()
    }

    private fun initUI() {
        // 创建左右分栏
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            dividerLocation = 200
            dividerSize = 4
            isOneTouchExpandable = true
        }

        // 左侧视图树
        val leftPanel = createLeftPanel()
        splitPane.leftComponent = leftPanel

        // 右侧任务列表
        val rightPanel = createRightPanel()
        splitPane.rightComponent = rightPanel

        add(splitPane, BorderLayout.CENTER)
    }

    private fun createLeftPanel(): JComponent {
        // 工具栏
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply {
            background = JBColor.namedColor("Panel.background", UIUtil.getPanelBackground())
            border = JBUI.Borders.empty(4)

            add(JButton("⟳").apply {
                preferredSize = Dimension(28, 24)
                toolTipText = "刷新模块"
                addActionListener {
                    refreshModules()
                    refreshTasks()
                }
            })

            add(JButton("+").apply {
                preferredSize = Dimension(28, 24)
                toolTipText = "添加自定义视图"
                addActionListener { addCustomView() }
            })

            add(JButton("✎").apply {
                preferredSize = Dimension(28, 24)
                toolTipText = "编辑选中视图"
                addActionListener { editSelectedView() }
            })

            add(JButton("✕").apply {
                preferredSize = Dimension(28, 24)
                toolTipText = "删除选中视图"
                addActionListener { deleteSelectedView() }
            })

            add(JButton("⚙").apply {
                preferredSize = Dimension(28, 24)
                toolTipText = "视图规则设置"
                addActionListener { openSettings() }
            })
        }

        // 视图树
        viewTree = JTree(treeModel).apply {
            isRootVisible = false
            showsRootHandles = true
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            rowHeight = 28

            // 自定义渲染
            cellRenderer = object : DefaultTreeCellRenderer() {
                override fun getTreeCellRendererComponent(
                    tree: JTree,
                    value: Any,
                    sel: Boolean,
                    expanded: Boolean,
                    leaf: Boolean,
                    row: Int,
                    hasFocus: Boolean
                ): Component {
                    super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

                    val node = value as? DefaultMutableTreeNode
                    val viewNode = node?.userObject as? ViewNode

                    viewNode?.let {
                        text = "${it.icon} ${it.name}"
                        icon = null  // 使用文本图标
                    }

                    return this
                }
            }

            // 选择监听器
            addTreeSelectionListener { event ->
                val node = event.path.lastPathComponent as? DefaultMutableTreeNode
                val viewNode = node?.userObject as? ViewNode
                viewNode?.let {
                    currentView = it
                    refreshTasks()
                }
            }

            // 双击编辑
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        editSelectedView()
                    }
                }
            })
        }

        // 展开所有节点
        expandAllNodes(viewTree, 0, viewTree.rowCount)

        // 默认选择"全部任务"
        selectView(ViewNode.AllTasks)

        return JPanel(BorderLayout()).apply {
            background = JBColor.namedColor("Panel.background", UIUtil.getPanelBackground())
            border = JBUI.Borders.empty(0)

            add(toolbar, BorderLayout.NORTH)
            add(JBScrollPane(viewTree), BorderLayout.CENTER)
        }
    }

    private fun createRightPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        // 工具栏
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply {
            background = JBColor.namedColor("Panel.background", UIUtil.getPanelBackground())
            border = JBUI.Borders.empty(4, 8)

            add(JLabel("任务列表").apply {
                font = font.deriveFont(Font.BOLD)
            })

            add(Box.createHorizontalStrut(20))

            // 状态筛选
            add(JLabel("状态:"))
            val statusCombo = JComboBox(arrayOf("全部", "待办", "进行中", "已完成", "已取消"))
            statusCombo.addActionListener {
                if (it.actionCommand == "comboBoxChanged") {
                    currentStatusFilter = statusCombo.selectedItem as? String ?: "全部"
                    refreshTasks()
                }
            }
            add(statusCombo)

            add(Box.createHorizontalGlue())

            // 操作按钮
            add(JButton("+ 添加").apply {
                addActionListener { showAddTaskDialog() }
            })

            add(JButton("↑ 分享").apply {
                addActionListener { shareSelectedTasks() }
            })

            add(JButton("↓ 导入").apply {
                addActionListener { showImportDialog() }
            })

            add(JButton("🤖 提示词").apply {
                toolTipText = "生成 AI 提示词 (选中任务或当前模块未完成任务)"
                addActionListener { generatePrompt() }
            })
        }

        // 任务列表
        taskList.apply {
            cellRenderer = TaskCellRenderer()
            selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        selectedValue?.let { editTask(it) }
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        showContextMenu(e)
                    }
                }
            })

            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    when {
                        e.keyCode == KeyEvent.VK_DELETE -> selectedValue?.let { deleteTask(it) }
                        e.keyCode == KeyEvent.VK_ENTER -> selectedValue?.let { toggleTaskStatus(it) }
                        e.keyCode == KeyEvent.VK_C && e.isControlDown -> {
                            // Ctrl+C 复制
                            val selected = selectedValuesList
                            if (selected.isNotEmpty()) {
                                copyTasksToClipboard(selected)
                            }
                        }
                    }
                }
            })
        }

        // 底部状态栏
        val statusBar = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            background = JBColor.namedColor("Panel.background", UIUtil.getPanelBackground())
            border = JBUI.Borders.empty(4, 8)
            add(JLabel("双击编辑 | 右键菜单 | Delete删除 | Ctrl+C复制 | Ctrl+Click多选").apply {
                foreground = JBColor.GRAY
                font = font.deriveFont(Font.PLAIN, 11f)
            })
        }

        panel.add(toolbar, BorderLayout.NORTH)
        panel.add(JBScrollPane(taskList), BorderLayout.CENTER)
        panel.add(statusBar, BorderLayout.SOUTH)

        return panel
    }

    private fun rebuildViewTree() {
        rootNode.removeAllChildren()

        // 1. 内置视图
        rootNode.add(createTreeNode(ViewNode.Global))
        rootNode.add(createTreeNode(ViewNode.ProjectLevel))

        // 2. 按规则分组的模块（显示所有启用的规则，即使没有匹配模块）
        val groupedModules = settings.groupModulesByRules(detectedModules)
        val rules = settings.getRules()

        rules.forEach { rule ->
            val modules = groupedModules[rule.id] ?: emptyList()
            // 始终显示启用的规则，即使没有匹配的模块
            val ruleNode = createTreeNode(ViewNode.RuleGroup(rule, modules))
            modules.forEach { module ->
                ruleNode.add(createTreeNode(ViewNode.Module(module)))
            }
            rootNode.add(ruleNode)
        }

        // 3. 模块任务（未分类模块放在这里）
        val uncategorized = groupedModules["uncategorized"] ?: emptyList()
        if (uncategorized.isNotEmpty()) {
            val moduleTasksNode = createTreeNode(ViewNode.ModuleTasks)
            uncategorized.forEach { module ->
                moduleTasksNode.add(createTreeNode(ViewNode.Module(module)))
            }
            rootNode.add(moduleTasksNode)
        }

        // 4. 全部任务
        rootNode.add(createTreeNode(ViewNode.AllTasks))

        treeModel.reload()
        expandAllNodes(viewTree, 0, viewTree.rowCount)
    }

    private fun createTreeNode(viewNode: ViewNode): DefaultMutableTreeNode {
        return DefaultMutableTreeNode(viewNode)
    }

    private fun expandAllNodes(tree: JTree, startingIndex: Int, rowCount: Int) {
        for (i in startingIndex until rowCount) {
            tree.expandRow(i)
        }
        if (tree.rowCount != rowCount) {
            expandAllNodes(tree, rowCount, tree.rowCount)
        }
    }

    private fun selectView(viewNode: ViewNode) {
        currentView = viewNode
        val enumeration = rootNode.depthFirstEnumeration()
        while (enumeration.hasMoreElements()) {
            val node = enumeration.nextElement() as? DefaultMutableTreeNode ?: continue
            if (node.userObject == viewNode) {
                viewTree.selectionPath = TreePath(node.path)
                refreshTasks()
                return
            }
        }
    }

    private fun refreshTasks() {
        // 根据当前视图获取任务
        val allTasks = when (val view = currentView) {
            is ViewNode.Global -> service.getGlobalTasks()
            is ViewNode.ProjectLevel -> service.getProjectLevelTasks()
            is ViewNode.AllTasks -> service.getAllTasks()
            is ViewNode.RuleGroup -> {
                val modules = view.modules
                service.getProjectTasks().filter { task ->
                    modules.any { it.path == task.modulePath }
                }
            }
            is ViewNode.Module -> {
                service.getModuleTasks(view.module.path)
            }
            is ViewNode.ModuleTasks -> {
                // 模块任务视图显示所有项目级模块任务
                service.getProjectTasks().filter { it.modulePath.isNotBlank() }
            }
            is ViewNode.Custom -> {
                service.getProjectTasks().filter { it.modulePath in view.modulePaths }
            }
        }

        // 应用状态筛选
        val filteredTasks = if (currentStatusFilter == "全部") {
            allTasks
        } else {
            val status = when (currentStatusFilter) {
                "待办" -> VibeTask.TaskStatus.TODO
                "进行中" -> VibeTask.TaskStatus.IN_PROGRESS
                "已完成" -> VibeTask.TaskStatus.DONE
                "已取消" -> VibeTask.TaskStatus.CANCELLED
                else -> null
            }
            if (status != null) {
                allTasks.filter { it.status == status }
            } else {
                allTasks
            }
        }

        listModel.removeAll()
        listModel.add(filteredTasks.sortedByDescending { it.createdAt })
    }

    // ========== 操作方法 ==========

    private fun showAddTaskDialog(task: VibeTask? = null, isEdit: Boolean = false) {
        val defaultModule = when (currentView) {
            is ViewNode.Module -> (currentView as ViewNode.Module).module
            else -> null
        }

        val dialog = AddTaskDialog(
            project, task, isEdit,
            currentView is ViewNode.Global,
            defaultModule, detectedModules
        )

        if (dialog.showAndGet()) {
            val content = dialog.getTaskContent()
            val isGlobal = dialog.isGlobal()
            val priority = dialog.getPriority()
            val selectedModule = dialog.getSelectedModule()
            val assignees = dialog.getAssignees()

            if (content.isNotBlank()) {
                if (isEdit && task != null) {
                    val updated = task.copy(
                        content = content,
                        projectPath = if (isGlobal) "" else service.currentProjectPath,
                        projectName = if (isGlobal) "" else service.currentProjectName,
                        moduleName = selectedModule?.name ?: "",
                        modulePath = selectedModule?.path ?: "",
                        priority = priority,
                        assignees = assignees
                    )
                    service.updateTask(updated)
                } else {
                    val newTask = service.addTask(content, isGlobal, priority, selectedModule)
                    // 如果有负责人，更新任务
                    if (assignees.isNotEmpty()) {
                        service.updateTask(newTask.copy(assignees = assignees))
                    }
                }
                refreshTasks()
            }
        }
    }

    private fun editTask(task: VibeTask) {
        showAddTaskDialog(task, true)
    }

    private fun deleteTask(task: VibeTask) {
        if (Messages.showYesNoDialog(
                "确定删除这个任务?\n${task.content}",
                "删除任务",
                Messages.getQuestionIcon()
            ) == Messages.YES) {
            service.deleteTask(task.id)
            refreshTasks()
        }
    }

    private fun toggleTaskStatus(task: VibeTask) {
        when (task.status) {
            VibeTask.TaskStatus.TODO -> service.startTask(task)
            VibeTask.TaskStatus.IN_PROGRESS -> service.completeTask(task)
            VibeTask.TaskStatus.DONE -> service.reopenTask(task)
            VibeTask.TaskStatus.CANCELLED -> service.reopenTask(task)
        }
        refreshTasks()
    }

    private fun showContextMenu(e: MouseEvent) {
        val popup = JPopupMenu()

        val selectedTasks = taskList.selectedValuesList
        if (selectedTasks.isEmpty()) return

        // 复制功能
        popup.add(JMenuItem("📋 复制内容").apply {
            addActionListener { copyTasksToClipboard(selectedTasks) }
        })

        popup.addSeparator()

        popup.add(JMenuItem("编辑").apply {
            addActionListener { selectedTasks.firstOrNull()?.let { editTask(it) } }
        })

        popup.add(JMenuItem("开始").apply {
            addActionListener { selectedTasks.forEach { if (it.status == VibeTask.TaskStatus.TODO) service.startTask(it) }; refreshTasks() }
        })

        popup.add(JMenuItem("完成").apply {
            addActionListener { selectedTasks.forEach { if (it.status != VibeTask.TaskStatus.DONE) service.completeTask(it) }; refreshTasks() }
        })

        popup.addSeparator()

        popup.add(JMenuItem("分享").apply {
            addActionListener { shareSelectedTasks() }
        })

        popup.add(JMenuItem("删除").apply {
            addActionListener { selectedTasks.forEach { service.deleteTask(it.id) }; refreshTasks() }
        })

        popup.show(e.component, e.x, e.y)
    }

    private fun shareSelectedTasks() {
        val selected = taskList.selectedValuesList
        if (selected.isEmpty()) {
            Messages.showInfoMessage(project, "请先选择要分享的任务", "提示")
            return
        }
        ShareTaskDialog(project, selected).show()
    }

    /**
     * 显示导入对话框
     */
    private fun showImportDialog() {
        val dialog = ImportTaskDialog(project)
        if (dialog.showAndGet()) {
            val importedCount = dialog.getImportedCount()
            if (importedCount > 0) {
                Messages.showInfoMessage(project, "成功导入 $importedCount 个任务", "导入成功")
                refreshTasks()
            } else if (importedCount == 0) {
                Messages.showInfoMessage(project, "没有导入任何任务", "提示")
            } else {
                Messages.showErrorDialog(project, "导入失败，请检查数据格式", "导入失败")
            }
        }
    }

    /**
     * 复制任务内容到剪贴板
     */
    private fun copyTasksToClipboard(tasks: List<VibeTask>) {
        if (tasks.isEmpty()) return

        val content = buildString {
            if (tasks.size == 1) {
                // 单个任务：复制简洁格式
                val task = tasks.first()
                appendLine("[${task.status.name}] ${task.content}")
                if (task.assignees.isNotEmpty()) {
                    appendLine("负责人: ${task.assignees.joinToString(", ")}")
                }
                appendLine("模块: ${task.getScopeDisplay()}")
            } else {
                // 多个任务：复制列表格式
                appendLine("📋 Vibe Tasks (${tasks.size} 个)")
                appendLine()
                tasks.forEach { task ->
                    val icon = when (task.status) {
                        VibeTask.TaskStatus.TODO -> "⏳"
                        VibeTask.TaskStatus.IN_PROGRESS -> "▶️"
                        VibeTask.TaskStatus.DONE -> "✅"
                        VibeTask.TaskStatus.CANCELLED -> "❌"
                    }
                    appendLine("$icon ${task.content}")
                    if (task.assignees.isNotEmpty()) {
                        appendLine("   👤 ${task.assignees.joinToString(", ")}")
                    }
                }
            }
        }

        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(content), null)

        // 显示通知
        val message = if (tasks.size == 1) "已复制任务内容" else "已复制 ${tasks.size} 个任务"
        NotificationGroupManager.getInstance()
            .getNotificationGroup("VibeTask Notifications")
            ?.createNotification(message, NotificationType.INFORMATION)
            ?.notify(project)
    }

    // ========== 视图操作 ==========

    private fun addCustomView() {
        val name = Messages.showInputDialog(project, "视图名称:", "添加自定义视图", Messages.getQuestionIcon())
            ?: return

        if (name.isBlank()) {
            Messages.showErrorDialog(project, "视图名称不能为空", "错误")
            return
        }

        // 创建新规则
        val rule = TaskViewRule(
            name = name,
            type = TaskViewRule.RuleType.CONTAINS,
            pattern = name.lowercase(),
            icon = "📂"
        )
        settings.addRule(rule)
        rebuildViewTree()

        Messages.showInfoMessage(project, "已添加视图: $name", "成功")
    }

    private fun editSelectedView() {
        val selectedNode = viewTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            ?: return
        val viewNode = selectedNode.userObject as? ViewNode
            ?: return

        if (!viewNode.isEditable) {
            Messages.showInfoMessage(project, "该视图不可编辑", "提示")
            return
        }

        when (viewNode) {
            is ViewNode.RuleGroup -> {
                // 编辑规则
                val newName = Messages.showInputDialog(
                    project,
                    "视图名称:",
                    "编辑视图",
                    Messages.getQuestionIcon(),
                    viewNode.rule.name,
                    null
                ) ?: return

                if (newName.isBlank()) {
                    Messages.showErrorDialog(project, "视图名称不能为空", "错误")
                    return
                }

                val updatedRule = viewNode.rule.copy(name = newName)
                settings.updateRule(updatedRule)
                rebuildViewTree()
            }
            is ViewNode.Custom -> {
                // 编辑自定义视图
                val newName = Messages.showInputDialog(
                    project,
                    "视图名称:",
                    "编辑视图",
                    Messages.getQuestionIcon(),
                    viewNode.name,
                    null
                ) ?: return

                if (newName.isBlank()) {
                    Messages.showErrorDialog(project, "视图名称不能为空", "错误")
                    return
                }

                // 创建新的 CustomView 并更新
                val updatedView = CustomView(
                    id = viewNode.id,
                    name = newName,
                    icon = viewNode.icon,
                    modulePaths = viewNode.modulePaths.toMutableList()
                )
                settings.updateCustomView(updatedView)
                rebuildViewTree()
            }
            else -> {
                Messages.showInfoMessage(project, "该视图不可编辑", "提示")
            }
        }
    }

    private fun deleteSelectedView() {
        val selectedNode = viewTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            ?: return
        val viewNode = selectedNode.userObject as? ViewNode
            ?: return

        if (!viewNode.isDeletable) {
            Messages.showInfoMessage(project, "该视图不可删除", "提示")
            return
        }

        val result = Messages.showYesNoDialog(
            "确定要删除视图 \"${viewNode.name}\" 吗?",
            "删除视图",
            Messages.getQuestionIcon()
        )

        if (result != Messages.YES) return

        when (viewNode) {
            is ViewNode.RuleGroup -> {
                settings.removeRule(viewNode.rule.id)
                rebuildViewTree()
                Messages.showInfoMessage(project, "已删除视图: ${viewNode.name}", "成功")
            }
            is ViewNode.Custom -> {
                settings.removeCustomView(viewNode.id)
                rebuildViewTree()
                Messages.showInfoMessage(project, "已删除视图: ${viewNode.name}", "成功")
            }
            else -> {
                Messages.showInfoMessage(project, "该视图不可删除", "提示")
            }
        }
    }

    private fun openSettings() {
        com.intellij.openapi.options.ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, "Vibe Task 视图规则")
    }

    /**
     * 生成 AI 提示词
     */
    private fun generatePrompt() {
        val selectedTasks = taskList.selectedValuesList

        // 确定要包含的任务
        val tasksToInclude = if (selectedTasks.isNotEmpty()) {
            selectedTasks
        } else {
            // 获取当前视图下的未完成任务
            val currentTasks = listModel.toList()
                .filter { it.status != VibeTask.TaskStatus.DONE && it.status != VibeTask.TaskStatus.CANCELLED }
            if (currentTasks.isEmpty()) {
                Messages.showInfoMessage(project, "当前视图没有未完成的任务", "提示")
                return
            }
            currentTasks
        }

        val module = getCurrentModuleContext()
        val prompt = buildAIPrompt(tasksToInclude, module)

        // 复制到剪贴板
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(prompt), null)

        // 显示通知
        val message = if (selectedTasks.isNotEmpty()) {
            "已复制 ${selectedTasks.size} 个选中任务的 AI 提示词"
        } else {
            "已复制 ${tasksToInclude.size} 个未完成任务的 AI 提示词"
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("VibeTask Notifications")
            ?.createNotification(message, NotificationType.INFORMATION)
            ?.notify(project)
    }

    /**
     * 构建 AI 提示词
     */
    private fun buildAIPrompt(tasks: List<VibeTask>, module: ProjectModule?): String {
        return buildString {
            appendLine("# Vibe Coding 任务")
            appendLine()

            if (module != null) {
                appendLine("## 当前模块: ${module.name}")
                appendLine("路径: ${module.path}")
                appendLine("类型: ${module.type}")
                appendLine()
            } else {
                appendLine("## 项目: ${project.name}")
                appendLine()
            }

            appendLine("## 待办任务 (${tasks.size}个)")
            appendLine()

            // 按优先级分组
            val highPriority = tasks.filter { it.priority == VibeTask.Priority.HIGH }
            val mediumPriority = tasks.filter { it.priority == VibeTask.Priority.MEDIUM }
            val lowPriority = tasks.filter { it.priority == VibeTask.Priority.LOW }

            if (highPriority.isNotEmpty()) {
                appendLine("### 🔴 高优先级")
                highPriority.forEachIndexed { index, task ->
                    appendLine("${index + 1}. ${task.content}")
                }
                appendLine()
            }

            if (mediumPriority.isNotEmpty()) {
                appendLine("### 🟡 中优先级")
                mediumPriority.forEachIndexed { index, task ->
                    appendLine("${index + 1}. ${task.content}")
                }
                appendLine()
            }

            if (lowPriority.isNotEmpty()) {
                appendLine("### 🟢 低优先级")
                lowPriority.forEachIndexed { index, task ->
                    appendLine("${index + 1}. ${task.content}")
                }
                appendLine()
            }

            appendLine("## 实现要求")
            appendLine()
            appendLine("请根据以上任务:")
            appendLine("1. 分析代码结构和现有实现")
            appendLine("2. 优先处理高优先级任务")
            appendLine("3. 保持代码风格一致性")
            appendLine("4. 编写必要的注释")
            appendLine("5. 如有问题请明确提出")
        }
    }

    // ========== 公共方法 ==========

    /**
     * 获取当前选中的任务列表
     */
    fun getSelectedTasks(): List<VibeTask> {
        return taskList.selectedValuesList
    }

    /**
     * 获取当前模块上下文
     */
    fun getCurrentModuleContext(): ProjectModule? {
        return when (val view = currentView) {
            is ViewNode.Module -> view.module
            else -> null
        }
    }

    // ========== 列表渲染器 ==========

    private inner class TaskCellRenderer : JPanel(BorderLayout()), ListCellRenderer<VibeTask> {
        private val checkBox = JCheckBox()
        private val contentLabel = JLabel()
        private val infoLabel = JLabel()

        init {
            isOpaque = true
            border = JBUI.Borders.empty(6, 12)

            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                add(checkBox, BorderLayout.WEST)
                add(JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(contentLabel, BorderLayout.NORTH)
                    add(infoLabel, BorderLayout.SOUTH)
                }, BorderLayout.CENTER)
            }, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: JList<out VibeTask>,
            value: VibeTask,
            index: Int,
            isSelected: Boolean,
            hasFocus: Boolean
        ): Component {
            background = if (isSelected)
                JBColor.namedColor("List.selectionBackground", UIManager.getColor("List.selectionBackground"))
            else
                JBColor.namedColor("Panel.background", UIManager.getColor("Panel.background"))

            checkBox.isSelected = value.status == VibeTask.TaskStatus.DONE
            checkBox.isEnabled = false

            contentLabel.apply {
                text = value.content
                font = UIManager.getFont("Label.font")
                foreground = if (isSelected)
                    JBColor.namedColor("List.selectionForeground", UIManager.getColor("List.selectionForeground"))
                else if (value.status == VibeTask.TaskStatus.DONE) JBColor.GRAY
                else JBColor.namedColor("Label.foreground", UIManager.getColor("Label.foreground"))
            }

            val dateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(Date(value.createdAt))
            val statusIcon = when (value.status) {
                VibeTask.TaskStatus.TODO -> "⏳"
                VibeTask.TaskStatus.IN_PROGRESS -> "▶️"
                VibeTask.TaskStatus.DONE -> "✅"
                VibeTask.TaskStatus.CANCELLED -> "❌"
            }
            val priorityIcon = when (value.priority) {
                VibeTask.Priority.HIGH -> "🔴"
                VibeTask.Priority.MEDIUM -> "🟡"
                VibeTask.Priority.LOW -> "🟢"
            }
            val assigneeStr = if (value.assignees.isNotEmpty()) " ${value.getAssigneeDisplay()}" else ""

            infoLabel.apply {
                text = "$statusIcon $priorityIcon ${value.getScopeDisplay()}$assigneeStr · $dateStr"
                font = UIManager.getFont("Label.font")?.deriveFont(Font.PLAIN, 11f)
                foreground = if (isSelected)
                    JBColor.namedColor("List.selectionForeground", UIManager.getColor("List.selectionForeground"))
                else JBColor.GRAY
            }

            return this
        }
    }
}
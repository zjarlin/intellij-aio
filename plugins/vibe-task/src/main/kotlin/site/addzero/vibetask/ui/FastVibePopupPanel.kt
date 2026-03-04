package site.addzero.vibetask.ui

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import site.addzero.vibetask.model.ProjectModule
import site.addzero.vibetask.model.VibeTask
import site.addzero.vibetask.service.VibeTaskService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*

/**
 * Fast Vibe 弹出面板
 * 仿照 IDEA 任务下拉设计
 */
class FastVibePopupPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val service = VibeTaskService.getInstance(project)
    private var popup: JBPopup? = null

    // 当前检测到的模块
    private var currentModule: ProjectModule? = null

    // UI 组件
    private lateinit var moduleLabel: JBLabel
    private lateinit var taskList: JBList<VibeTask>
    private lateinit var listModel: CollectionListModel<VibeTask>
    private lateinit var quickInput: JBTextArea

    init {
        background = JBColor.namedColor("Panel.background")
        initUI()
        detectCurrentModule()
    }

    private fun initUI() {
        // 顶部：模块信息
        val headerPanel = JPanel(BorderLayout()).apply {
            background = JBColor.namedColor("Panel.background")
            border = JBUI.Borders.empty(8, 12)

            moduleLabel = JBLabel("Detecting module...").apply {
                font = font.deriveFont(Font.BOLD, 13f)
                icon = AllIcons.Actions.Lightning
            }

            add(moduleLabel, BorderLayout.CENTER)

            // 打开面板按钮
            add(JButton("打开面板").apply {
                isOpaque = false
                isContentAreaFilled = false
                border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
                addActionListener {
                    popup?.cancel()
                    openVibeTaskPanel()
                }
            }, BorderLayout.EAST)
        }

        // 中部：未完成任务列表
        listModel = CollectionListModel()
        taskList = JBList(listModel).apply {
            cellRenderer = TaskItemRenderer()
            selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
            visibleRowCount = 5
        }

        val listPanel = JPanel(BorderLayout()).apply {
            background = JBColor.namedColor("Panel.background")
            border = JBUI.Borders.empty(0, 8)

            add(JBLabel("Pending tasks:").apply {
                foreground = JBColor.GRAY
                border = JBUI.Borders.empty(4, 4)
            }, BorderLayout.NORTH)

            add(JBScrollPane(taskList).apply {
                preferredSize = Dimension(380, 120)
                border = BorderFactory.createLineBorder(JBColor.border())
            }, BorderLayout.CENTER)
        }

        // 底部：快速输入和操作
        val footerPanel = JPanel(BorderLayout()).apply {
            background = JBColor.namedColor("Panel.background")
            border = JBUI.Borders.empty(8, 12)

            // 快速输入区
            val inputPanel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(0, 0, 8, 0)

                quickInput = JBTextArea(2, 30).apply {
                    lineWrap = true
                    wrapStyleWord = true
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(JBColor.border()),
                        BorderFactory.createEmptyBorder(4, 6, 4, 6)
                    )
                    font = font.deriveFont(12f)

                    addKeyListener(object : KeyAdapter() {
                        override fun keyPressed(e: KeyEvent) {
                            if (e.keyCode == KeyEvent.VK_ENTER && e.isControlDown) {
                                e.consume()
                                addQuickTask()
                            }
                        }
                    })
                }

                add(quickInput, BorderLayout.CENTER)
            }

            // 按钮行
            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                isOpaque = false

                add(JButton("生成提示词").apply {
                    icon = AllIcons.Actions.IntentionBulb
                    addActionListener { generatePrompt() }
                })

                add(JButton("添加").apply {
                    icon = AllIcons.General.Add
                    addActionListener { addQuickTask() }
                })
            }

            add(inputPanel, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }

        // 组合
        val contentPanel = JPanel(BorderLayout()).apply {
            background = JBColor.namedColor("Panel.background")
            add(headerPanel, BorderLayout.NORTH)
            add(listPanel, BorderLayout.CENTER)
            add(footerPanel, BorderLayout.SOUTH)
        }

        add(contentPanel, BorderLayout.CENTER)

        preferredSize = Dimension(420, 320)
    }

    /**
     * 检测当前模块
     */
    fun detectCurrentModule() {
        // 获取当前打开的文件
        val fileEditorManager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
        val currentFile = fileEditorManager.selectedFiles.firstOrNull()

        currentModule = currentFile?.let { file ->
            service.getModuleForFile(file.path)
        }

        refreshUI()
    }

    private fun refreshUI() {
        // 更新模块标签
        moduleLabel.text = if (currentModule != null) {
            "📦 ${currentModule?.name}"
        } else {
            "📁 ${project.name} (项目级)"
        }

        // 刷新任务列表
        refreshTaskList()
    }

    private fun refreshTaskList() {
        listModel.removeAll()

        val tasks = if (currentModule != null) {
            service.getModuleTasks(currentModule!!.path)
                .filter { it.status != VibeTask.TaskStatus.DONE && it.status != VibeTask.TaskStatus.CANCELLED }
        } else {
            service.getProjectTasks()
                .filter { it.status != VibeTask.TaskStatus.DONE && it.status != VibeTask.TaskStatus.CANCELLED }
        }

        listModel.add(tasks.sortedByDescending { it.priority.ordinal })
    }

    /**
     * 快速添加任务
     */
    private fun addQuickTask() {
        val content = quickInput.text.trim()
        if (content.isEmpty()) return

        service.addTask(
            content = content,
            isGlobal = false,
            priority = VibeTask.Priority.MEDIUM,
            module = currentModule
        )

        quickInput.text = ""
        refreshTaskList()

        // 显示通知
        NotificationGroupManager.getInstance()
            .getNotificationGroup("VibeTask Notifications")
            ?.createNotification(
                "已添加到 ${currentModule?.name ?: "项目"}",
                NotificationType.INFORMATION
            )
            ?.notify(project)
    }

    /**
     * 生成 AI 提示词
     */
    private fun generatePrompt() {
        val selectedTasks = taskList.selectedValuesList

        val tasksToInclude = if (selectedTasks.isNotEmpty()) {
            selectedTasks
        } else {
            listModel.toList()
        }

        tasksToInclude.ifEmpty {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("VibeTask Notifications")
                ?.createNotification(
                    "No pending tasks",
                    NotificationType.WARNING
                )
                ?.notify(project)
            return
        }

        val prompt = buildPrompt(tasksToInclude, currentModule)

        // 复制到剪贴板
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(prompt), null)

        popup?.cancel()

        NotificationGroupManager.getInstance()
            .getNotificationGroup("VibeTask Notifications")
            ?.createNotification(
                "已复制 AI 提示词 (${tasksToInclude.size}个任务)",
                NotificationType.INFORMATION
            )
            ?.notify(project)
    }

    private fun buildPrompt(tasks: List<VibeTask>, module: ProjectModule?): String {
        return buildString {
            appendLine("# Vibe Coding 任务")
            appendLine()

            if (module != null) {
                appendLine("## 当前模块: ${module.name}")
                appendLine("路径: ${module.path}")
                appendLine()
            }

            appendLine("## 待办任务")
            appendLine()

            tasks.forEachIndexed { index, task ->
                val icon = when (task.priority) {
                    VibeTask.Priority.HIGH -> "🔴"
                    VibeTask.Priority.MEDIUM -> "🟡"
                    VibeTask.Priority.LOW -> "🟢"
                }
                appendLine("${index + 1}. $icon ${task.content}")
            }

            appendLine()
            appendLine("## 要求")
            appendLine("- 请分析当前模块代码结构")
            appendLine("- 优先处理高优先级任务")
            appendLine("- 保持代码风格一致性")
            appendLine("- 提供清晰的实现思路")
        }
    }

    /**
     * 打开 Vibe Task 面板
     */
    private fun openVibeTaskPanel() {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Vibe Task")
        toolWindow?.show {
            // 可以在这里设置选中的模块
        }
    }

    /**
     * 显示弹出面板
     */
    fun showPopup(parentComponent: JComponent) {
        detectCurrentModule()

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(this, quickInput)
            .setTitle("Fast Vibe")
            .setMovable(true)
            .setResizable(true)
            .setRequestFocus(true)
            .createPopup()

        popup?.showUnderneathOf(parentComponent)
    }

    /**
     * 任务项渲染器
     */
    private inner class TaskItemRenderer : JPanel(BorderLayout()), ListCellRenderer<VibeTask> {
        private val checkBox = JCheckBox()
        private val contentLabel = JLabel()
        private val infoLabel = JLabel()

        init {
            isOpaque = true
            border = JBUI.Borders.empty(4, 8)

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
            list: JList<out VibeTask>?,
            value: VibeTask,
            index: Int,
            isSelected: Boolean,
            hasFocus: Boolean
        ): JComponent {
            background = if (isSelected)
                JBColor.namedColor("List.selectionBackground")
            else
                JBColor.namedColor("Panel.background")

            checkBox.isSelected = value.status == VibeTask.TaskStatus.DONE
            checkBox.isEnabled = false

            contentLabel.text = value.content
            contentLabel.foreground = if (isSelected)
                JBColor.namedColor("List.selectionForeground")
            else
                JBColor.namedColor("Label.foreground")

            val priorityIcon = when (value.priority) {
                VibeTask.Priority.HIGH -> "🔴"
                VibeTask.Priority.MEDIUM -> "🟡"
                VibeTask.Priority.LOW -> "🟢"
            }
            infoLabel.text = "$priorityIcon ${value.status}"
            infoLabel.foreground = JBColor.GRAY
            infoLabel.font = infoLabel.font.deriveFont(Font.PLAIN, 11f)

            return this
        }
    }
}
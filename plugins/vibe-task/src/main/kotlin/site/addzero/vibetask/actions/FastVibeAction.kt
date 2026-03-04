package site.addzero.vibetask.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import site.addzero.vibetask.model.ProjectModule
import site.addzero.vibetask.model.VibeTask
import site.addzero.vibetask.service.VibeTaskService
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * Fast Vibe Action - 根据当前打开的文件快速记录模块想法
 */
class FastVibeAction : AnAction(
    "Fast Vibe",
    "根据当前文件所属模块快速记录想法",
    com.intellij.icons.AllIcons.Actions.Lightning
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = VibeTaskService.getInstance(project)

        // 获取当前打开的文件
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)

        // 检测当前文件所属的模块
        val currentModule = virtualFile?.let { file ->
            service.getModuleForFile(file.path)
        }

        // 获取所有模块供选择
        val allModules = service.getProjectModules()

        // 显示快速记录对话框
        val dialog = FastVibeDialog(project, currentModule, allModules)
        if (dialog.showAndGet()) {
            val content = dialog.getContent()
            val selectedModule = dialog.getSelectedModule()
            val priority = dialog.getPriority()

            if (content.isNotBlank()) {
                service.addTask(
                    content = content.trim(),
                    isGlobal = false,
                    priority = priority,
                    module = selectedModule
                )

                Messages.showInfoMessage(
                    project,
                    "已记录到模块: ${selectedModule?.name ?: "项目级"}",
                    "Fast Vibe"
                )
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
}

/**
 * Fast Vibe 对话框
 */
class FastVibeDialog(
    private val project: Project,
    private val currentModule: ProjectModule?,
    private val allModules: List<ProjectModule>
) : DialogWrapper(project) {

    private val contentArea = JBTextArea(5, 40).apply {
        lineWrap = true
        wrapStyleWord = true
    }

    private val moduleCombo = JComboBox<ProjectModuleWrapper>().apply {
        // 添加所有模块选项
        allModules.forEach { module ->
            addItem(ProjectModuleWrapper(module))
        }

        // 默认选中当前模块
        currentModule?.let { current ->
            for (i in 0 until itemCount) {
                if (getItemAt(i)?.module?.path == current.path) {
                    selectedIndex = i
                    break
                }
            }
        }
    }

    private val priorityCombo = JComboBox(arrayOf(
        PriorityWrapper("🔴 高", VibeTask.Priority.HIGH),
        PriorityWrapper("🟡 中", VibeTask.Priority.MEDIUM),
        PriorityWrapper("🟢 低", VibeTask.Priority.LOW)
    )).apply {
        selectedIndex = 1 // 默认中优先级
    }

    init {
        title = "Fast Vibe - 快速记录"
        init()

        // 设置焦点到文本区域
        contentArea.requestFocus()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(8)))

        // 当前模块提示
        val moduleInfo = if (currentModule != null) {
            "检测到模块: ${currentModule.name}"
        } else {
            "未检测到特定模块，将记录为项目级任务"
        }

        // 顶部信息栏
        val topPanel = JPanel(BorderLayout()).apply {
            add(JBLabel(moduleInfo).apply {
                font = font.deriveFont(java.awt.Font.BOLD)
            }, BorderLayout.NORTH)
        }

        // 模块选择
        val modulePanel = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            add(JBLabel("模块:"), BorderLayout.WEST)
            add(moduleCombo, BorderLayout.CENTER)
        }

        // 优先级选择
        val priorityPanel = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            add(JBLabel("优先级:"), BorderLayout.WEST)
            add(priorityCombo, BorderLayout.CENTER)
        }

        // 选项面板
        val optionsPanel = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            add(modulePanel, BorderLayout.CENTER)
            add(priorityPanel, BorderLayout.EAST)
        }

        // 内容输入区域
        val contentPanel = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(4))).apply {
            add(JBLabel("想法/需求:"), BorderLayout.NORTH)
            add(JScrollPane(contentArea), BorderLayout.CENTER)
        }

        // 组合所有面板
        val centerPanel = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(8)
            add(topPanel, BorderLayout.NORTH)
            add(contentPanel, BorderLayout.CENTER)
            add(optionsPanel, BorderLayout.SOUTH)
        }

        panel.add(centerPanel, BorderLayout.CENTER)

        // 设置首选大小
        panel.preferredSize = Dimension(450, 280)

        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = contentArea

    fun getContent(): String = contentArea.text

    fun getSelectedModule(): ProjectModule? {
        return (moduleCombo.selectedItem as? ProjectModuleWrapper)?.module
    }

    fun getPriority(): VibeTask.Priority {
        return (priorityCombo.selectedItem as? PriorityWrapper)?.priority ?: VibeTask.Priority.MEDIUM
    }

    /**
     * 包装类用于 ComboBox 显示
     */
    private data class ProjectModuleWrapper(val module: ProjectModule) {
        override fun toString(): String = module.getDisplayName()
    }

    private data class PriorityWrapper(val display: String, val priority: VibeTask.Priority) {
        override fun toString(): String = display
    }
}
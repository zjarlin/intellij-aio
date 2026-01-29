package site.addzero.gradle.buddy.filter

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import org.jetbrains.plugins.gradle.util.GradleConstants
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * 当前模块 Gradle 任务工具窗口
 */
class CurrentModuleGradleTasksToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CurrentModuleTasksPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * 显示当前模块任务的面板
 */
class CurrentModuleTasksPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val taskListModel = DefaultListModel<GradleTaskItem>()
    private val taskList = JBList(taskListModel)
    private val moduleLabel = JLabel("未选择模块")
    private var currentModulePath: String? = null

    init {
        setupUI()
        setupListeners()
        refreshTasks()
    }

    private fun setupUI() {
        // 顶部工具栏
        val toolbar = JPanel(BorderLayout())
        moduleLabel.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)
        toolbar.add(moduleLabel, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0))
        toolbar.add(buttonPanel, BorderLayout.EAST)
        add(toolbar, BorderLayout.NORTH)

        // 任务列表
        taskList.cellRenderer = TaskListCellRenderer()
        taskList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val selected = taskList.selectedValue
                    if (selected != null) {
                        runTask(selected)
                    }
                }
            }
        })

        add(JBScrollPane(taskList), BorderLayout.CENTER)

        // 底部提示
        val hint = JLabel("双击运行任务")
        hint.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)
        add(hint, BorderLayout.SOUTH)
    }

    private fun setupListeners() {
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    refreshTasks()
                }

                override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
                    refreshTasks()
                }
            }
        )
    }

    private fun refreshTasks() {
        val modulePath = detectCurrentModulePath()

        if (modulePath == currentModulePath) return
        currentModulePath = modulePath

        taskListModel.clear()

        if (modulePath == null) {
            moduleLabel.text = "未检测到模块"
            return
        }

        moduleLabel.text = "模块: $modulePath"

        // 动态获取 Gradle 任务
        val tasks = getGradleTasksForModule(modulePath)

        if (tasks.isEmpty()) {
            // 回退到常用任务列表
            getFallbackTasks(modulePath).forEach { taskListModel.addElement(it) }
        } else {
            tasks.sortedWith(compareBy({ it.group }, { it.name }))
                .forEach { taskListModel.addElement(it) }
        }
    }

    private fun getGradleTasksForModule(modulePath: String): List<GradleTaskItem> {
        val basePath = project.basePath ?: return emptyList()

        // 计算模块的绝对路径
        val moduleAbsolutePath = if (modulePath == ":") {
            basePath
        } else {
            "$basePath/${modulePath.trimStart(':').replace(':', '/')}"
        }

        return try {
            val projectDataManager = ProjectDataManager.getInstance()
            val projectInfo = projectDataManager.getExternalProjectData(project, GradleConstants.SYSTEM_ID, basePath)
            val projectStructure = projectInfo?.externalProjectStructure ?: return emptyList()

            // 获取所有任务数据
            ExternalSystemApiUtil.findAll(projectStructure, ProjectKeys.TASK)
                .mapNotNull { node ->
                    val taskData = node.data
                    val taskPath = resolveTaskPath(taskData)
                    val matchesPath = when {
                        modulePath == ":" -> taskData.linkedExternalProjectPath == basePath ||
                            taskPath?.startsWith(":") == true
                        else -> taskData.linkedExternalProjectPath == moduleAbsolutePath ||
                            taskPath == modulePath ||
                            taskPath?.startsWith("$modulePath:") == true
                    }
                    if (!matchesPath) return@mapNotNull null
                    GradleTaskItem(
                        name = taskData.name,
                        modulePath = modulePath,
                        group = taskData.group ?: "other",
                        description = taskData.description ?: ""
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun resolveTaskPath(taskData: Any): String? {
        return try {
            val method = taskData.javaClass.methods.firstOrNull {
                it.name == "getPath" && it.parameterCount == 0
            } ?: return null
            (method.invoke(taskData) as? String)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun getFallbackTasks(modulePath: String): List<GradleTaskItem> {
        val defaultTasks = GradleBuddySettingsService.getInstance(project).getDefaultTasks()
        return defaultTasks.map { taskName ->
            GradleTaskItem(taskName, modulePath, "default", "")
        }
    }

    private fun detectCurrentModulePath(): String? {
        val editor = FileEditorManager.getInstance(project).selectedEditor ?: return null
        val file = editor.file ?: return null

        val basePath = project.basePath ?: return null
        val filePath = file.path

        if (!filePath.startsWith(basePath)) return null

        var currentPath = file.parent
        while (currentPath != null && currentPath.path.startsWith(basePath)) {
            val hasBuildFile = currentPath.findChild("build.gradle.kts") != null ||
                              currentPath.findChild("build.gradle") != null

            if (hasBuildFile) {
                val moduleRelativePath = currentPath.path.removePrefix(basePath).trimStart('/')
                return if (moduleRelativePath.isEmpty()) {
                    ":"
                } else {
                    ":" + moduleRelativePath.replace('/', ':')
                }
            }
            currentPath = currentPath.parent
        }

        return null
    }

    private fun runTask(task: GradleTaskItem) {
        val projectPath = project.basePath ?: return

        val settings = ExternalSystemTaskExecutionSettings().apply {
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
            externalProjectPath = projectPath
            taskNames = listOf(task.fullPath)
        }

        ExternalSystemUtil.runTask(
            settings,
            DefaultRunExecutor.EXECUTOR_ID,
            project,
            GradleConstants.SYSTEM_ID,
            null,
            ProgressExecutionMode.IN_BACKGROUND_ASYNC
        )
    }
}

data class GradleTaskItem(
    val name: String,
    val modulePath: String,
    val group: String,
    val description: String
) {
    val fullPath: String
        get() = if (modulePath == ":") ":$name" else "$modulePath:$name"
}

class TaskListCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): java.awt.Component {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

        if (value is GradleTaskItem) {
            text = if (value.description.isNotEmpty()) "${value.name} - ${value.description}" else value.name
            icon = AllIcons.Nodes.RunnableMark
        }

        return this
    }
}

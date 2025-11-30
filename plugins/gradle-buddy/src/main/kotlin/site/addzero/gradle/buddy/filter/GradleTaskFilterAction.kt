package site.addzero.gradle.buddy.filter

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.icons.AllIcons

/**
 * 过滤 Gradle 任务，只显示当前编辑器文件所属模块的任务
 */
class GradleTaskFilterAction : AnAction(
    "Filter Gradle Tasks by Current Module",
    "Show only Gradle tasks for the module of the current editor file",
    AllIcons.General.Filter
), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val currentModule = getCurrentModulePath(project)
        if (currentModule == null) {
            // 没有打开的文件，显示全部
            GradleTaskFilterService.getInstance(project).clearFilter()
        } else {
            GradleTaskFilterService.getInstance(project).setFilter(currentModule)
        }
        
        // 刷新 Gradle 工具窗口
        refreshGradleToolWindow(project)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        e.presentation.isEnabledAndVisible = true
        
        val service = GradleTaskFilterService.getInstance(project)
        val currentFilter = service.getCurrentFilter()
        
        if (currentFilter != null) {
            e.presentation.text = "Filtering: $currentFilter (Click to clear)"
            e.presentation.icon = AllIcons.General.Filter
        } else {
            e.presentation.text = "Filter by Current Module"
            e.presentation.icon = AllIcons.General.Filter
        }
    }

    private fun getCurrentModulePath(project: Project): String? {
        val editor = FileEditorManager.getInstance(project).selectedEditor ?: return null
        val file = editor.file ?: return null
        
        val basePath = project.basePath ?: return null
        val filePath = file.path
        
        if (!filePath.startsWith(basePath)) return null
        
        // 查找包含 build.gradle 的最近父目录
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

    private fun refreshGradleToolWindow(project: Project) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Gradle")
        toolWindow?.activate(null)
    }
}

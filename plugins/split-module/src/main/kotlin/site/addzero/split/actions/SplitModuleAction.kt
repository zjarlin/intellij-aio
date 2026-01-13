package site.addzero.split.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.split.services.ModuleSplitter
import site.addzero.split.services.PathCalculator
import site.addzero.split.ui.ModuleNameDialog

/**
 * Split Module 右键菜单操作
 */
class SplitModuleAction : AnAction() {

  private val logger = Logger.getInstance(SplitModuleAction::class.java)

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return

    // 获取源模块
    val sourceModule = findSourceModule(project, selectedFiles.toList()) ?: return

    // 生成默认模块名称
    val defaultName = PathCalculator.generateDefaultModuleName(sourceModule.name)

    // 显示输入对话框
    val dialog = ModuleNameDialog(project, defaultName)
    if (!dialog.showAndGet()) {
      return
    }

    val newModuleName = dialog.getModuleName()

    // 执行拆分
    val splitter = ModuleSplitter(project)
    splitter.split(sourceModule, selectedFiles.toList(), newModuleName)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)

    // 检查是否可用
    val enabled = project != null &&
      selectedFiles != null &&
      selectedFiles.isNotEmpty() &&
      areFilesInSameModule(project, selectedFiles.toList())

    e.presentation.isEnabledAndVisible = enabled
  }

  /**
   * 检查所有文件是否属于同一个模块
   */
  private fun areFilesInSameModule(project: Project, files: List<VirtualFile>): Boolean {
    if (files.isEmpty()) return false

    val firstModule = findSourceModule(project, listOf(files.first())) ?: return false

    return files.all { file ->
      val module = findSourceModule(project, listOf(file))
      module != null && module.path == firstModule.path
    }
  }

  /**
   * 查找文件所属的模块根目录 (Gradle 或 Maven)
   */
  private fun findSourceModule(project: Project, files: List<VirtualFile>): VirtualFile? {
    if (files.isEmpty()) return null

    val file = files.first()
    var current: VirtualFile? = file.parent

    while (current != null) {
      // 检查常见的构建文件
      val isModuleRoot = current.findChild("build.gradle.kts") != null ||
        current.findChild("build.gradle") != null ||
        current.findChild("pom.xml") != null

      if (isModuleRoot) {
        return current
      }

      // 如果到达项目根目录，停止
      if (current.path == project.basePath) {
        break
      }

      current = current.parent
    }

    return null
  }
}

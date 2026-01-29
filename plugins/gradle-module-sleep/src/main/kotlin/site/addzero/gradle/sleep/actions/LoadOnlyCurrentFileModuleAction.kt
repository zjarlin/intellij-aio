package site.addzero.gradle.sleep.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import site.addzero.gradle.sleep.GradleModuleSleepService
import site.addzero.gradle.sleep.ModuleSleepIcons

class LoadOnlyCurrentFileModuleAction : AnAction(
  "Sleep other modules (keep this file only)",
  "Keep only the module of the current file and unload others",
  ModuleSleepIcons.KeepFile
), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    if (!project.service<GradleModuleSleepService>().isFeatureAvailable()) return
    val fileEditorManager = FileEditorManager.getInstance(project)

    fileEditorManager.openFiles
      .filter { it != file }
      .forEach { fileEditorManager.closeFile(it) }

    ModuleSleepActionExecutor.loadOnlyCurrentFile(project, file)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.icon = ModuleSleepIcons.KeepFile
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null &&
        project.service<GradleModuleSleepService>().isFeatureAvailable() &&
        e.getData(CommonDataKeys.VIRTUAL_FILE) != null
  }
}

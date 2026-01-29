package site.addzero.gradle.sleep

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider
import site.addzero.gradle.sleep.settings.ModuleSleepSettingsService

class ModuleSleepFloatingToolbarProvider :
  AbstractFloatingToolbarProvider("GradleModuleSleep.FloatingToolbarGroup") {

  override fun isApplicable(dataContext: DataContext): Boolean {
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
    val file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext) ?: return false
    if (!file.isValid || file.isDirectory) return false
    val basePath = project.basePath ?: return false
    if (!file.path.startsWith(basePath)) return false
    if (!project.service<GradleModuleSleepService>().isFeatureAvailable()) return false
    if (!ModuleSleepSettingsService.getInstance(project).isFloatingToolbarEnabled()) return false
    return super.isApplicable(dataContext) && editor.project == project
  }
}

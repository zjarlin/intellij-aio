package site.addzero.gradle.sleep.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import site.addzero.gradle.sleep.ModuleSleepIcons
import site.addzero.gradle.sleep.ui.ModuleSleepPopupLauncher

class ModuleSleepPopupAction : AnAction(
  "Gradle Module Sleep",
  "Open the Gradle Module Sleep panel",
  ModuleSleepIcons.Panel
), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

    ModuleSleepPopupLauncher.show(project, file, editor)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val editor = e.getData(CommonDataKeys.EDITOR)
    e.presentation.isEnabledAndVisible = project != null && editor != null
  }
}

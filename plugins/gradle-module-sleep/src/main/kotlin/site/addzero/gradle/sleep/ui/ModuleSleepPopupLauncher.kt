package site.addzero.gradle.sleep.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager

object ModuleSleepPopupLauncher {

  fun show(project: Project, editor: Editor? = null) {
    val panel = ModuleSleepPopupPanel.create(project)
    val popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(panel, panel)
      .setRequestFocus(true)
      .setResizable(true)
      .setMovable(true)
      .setCancelOnClickOutside(true)
      .createPopup()

    if (editor != null) {
      popup.showInBestPositionFor(editor)
      return
    }

    val window = WindowManager.getInstance().getIdeFrame(project)?.component
    if (window != null) {
      popup.showInCenterOf(window)
    } else {
      popup.showInFocusCenter()
    }
  }
}

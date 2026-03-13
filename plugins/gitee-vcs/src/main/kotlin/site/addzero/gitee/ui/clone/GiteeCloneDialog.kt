package site.addzero.gitee.ui.clone

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import javax.swing.Action
import javax.swing.JComponent

/**
 * Dialog used by the menu action to clone a Gitee repository.
 */
class GiteeCloneDialog(project: Project?) : DialogWrapper(project) {

    private val clonePanel: GiteeClonePanel

    init {
        clonePanel = GiteeClonePanel(project) {
            isOKActionEnabled = clonePanel.isCloneEnabled()
        }

        title = "Clone from Gitee"
        setOKButtonText("Clone")
        init()
        clonePanel.loadRepositoriesIfNeeded()
        isOKActionEnabled = clonePanel.isCloneEnabled()
    }

    override fun createCenterPanel(): JComponent = clonePanel.getComponent()

    override fun getPreferredFocusedComponent(): JComponent = clonePanel.getPreferredFocusedComponent()

    override fun doValidateAll(): List<ValidationInfo> = clonePanel.validateAll()

    override fun doOKAction() {
        val validations = doValidateAll()
        if (validations.isNotEmpty()) {
            startTrackingValidation()
            return
        }

        super.doOKAction()
        clonePanel.doClone(listener = null)
    }

    override fun createActions(): Array<Action> = arrayOf(okAction, cancelAction)
}

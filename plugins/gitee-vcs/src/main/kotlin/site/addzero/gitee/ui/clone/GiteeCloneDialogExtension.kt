package site.addzero.gitee.ui.clone

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Welcome-screen clone extension so Gitee appears in "Get from Version Control".
 */
class GiteeCloneDialogExtension : VcsCloneDialogExtension {

    companion object {
        private val GITEE_ICON = IconLoader.getIcon("/META-INF/pluginIcon.svg", GiteeCloneDialogExtension::class.java)
    }

    override fun getName(): String = "Gitee"

    override fun getIcon(): Icon = GITEE_ICON

    override fun getTooltip(): String = "Clone repositories from Gitee"

    override fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent {
        return Component(project)
    }

    private class Component(project: Project?) : VcsCloneDialogExtensionComponent() {

        private val clonePanel: GiteeClonePanel

        init {
            clonePanel = GiteeClonePanel(project) {
                dialogStateListener.onOkActionEnabled(clonePanel.isCloneEnabled())
            }
        }

        override fun getView(): JComponent = clonePanel.getComponent()

        override fun doClone(checkoutListener: CheckoutProvider.Listener) {
            clonePanel.doClone(checkoutListener)
        }

        override fun doValidateAll(): List<ValidationInfo> = clonePanel.validateAll()

        override fun getPreferredFocusedComponent(): JComponent = clonePanel.getPreferredFocusedComponent()

        override fun onComponentSelected() {
            clonePanel.loadRepositoriesIfNeeded()
            dialogStateListener.onOkActionEnabled(clonePanel.isCloneEnabled())
        }
    }
}

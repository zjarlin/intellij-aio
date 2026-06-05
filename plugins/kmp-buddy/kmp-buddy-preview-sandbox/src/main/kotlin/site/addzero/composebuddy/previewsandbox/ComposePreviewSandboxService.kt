package site.addzero.composebuddy.previewsandbox

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher
import java.util.EventListener

@Service(Service.Level.PROJECT)
class ComposePreviewSandboxService(
    private val project: Project,
) {
    private val dispatcher = EventDispatcher.create(ComposePreviewSandboxListener::class.java)
    private var currentSession: ComposePreviewSandboxSession? = null

    fun currentSession(): ComposePreviewSandboxSession? = currentSession

    fun updateSession(session: ComposePreviewSandboxSession) {
        currentSession = session
        dispatcher.multicaster.sessionChanged(session)
    }

    fun refreshCurrentPreview() {
        val anchor = ReadAction.compute<com.intellij.psi.PsiElement?, Throwable> {
            val function = currentSession?.previewFunctionPointer?.element ?: return@compute null
            function.nameIdentifier ?: function
        } ?: return
        ComposePreviewSandboxLauncher.open(anchor)
    }

    fun addListener(
        parentDisposable: Disposable,
        listener: ComposePreviewSandboxListener,
    ) {
        dispatcher.addListener(listener, parentDisposable)
    }

    fun showCurrentSessionLater() {
        val session = currentSession ?: return
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                dispatcher.multicaster.sessionChanged(session)
            }
        }
    }

    companion object {
        fun getInstance(project: Project): ComposePreviewSandboxService = project.service()
    }
}

interface ComposePreviewSandboxListener : EventListener {
    fun sessionChanged(session: ComposePreviewSandboxSession?)
}

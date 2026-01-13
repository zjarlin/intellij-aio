package site.addzero.autoupdate

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState

/**
 * Git Push Detector - Listens for Push actions to trigger Update Project before push
 */
@Service(Service.Level.PROJECT)
class GitPushDetectorService(private val project: Project) : Disposable {

    private val log = Logger.getInstance(GitPushDetectorService::class.java)
    private var isUpdating = false

    fun initialize() {
        // Subscribe to actions
        project.messageBus.connect(this).subscribe(AnActionListener.TOPIC, object : AnActionListener {
            override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
                if (isUpdating) return

                val actionId = ActionManager.getInstance().getId(action) ?: ""

                // Broad detection: match any action that contains "Push"
                // This covers Vcs.Push, Git.Push, CommitAndPush, GitHub.Push etc.
                if (actionId.contains("Push", ignoreCase = true) || actionId.contains("Checkin", ignoreCase = true)) {
                    log.info("Detected push-related action: $actionId")
                    handlePushActionDetected(event)
                }
            }
        })

        log.info("GitPushDetectorService initialized")
    }

    private fun handlePushActionDetected(originalEvent: AnActionEvent) {
        val settings = AutoUpdateSettings.getInstance()
        if (!settings.autoPullBeforePush) return

        // Use a small delay/invokeLater to avoid interfering with the action's initial setup
        ApplicationManager.getApplication().invokeLater({
            val updateAction = ActionManager.getInstance().getAction("Vcs.UpdateProject")
            if (updateAction != null) {
                isUpdating = true
                try {
                    // Create a proper event with current context
                    val event = AnActionEvent.createFromAnAction(
                        updateAction,
                        originalEvent.inputEvent,
                        originalEvent.place,
                        originalEvent.dataContext
                    )
                    log.info("Invoking native Update Project before $originalEvent")
                    ActionUtil.performActionDumbAwareWithCallbacks(updateAction, event)
                } catch (e: Exception) {
                    log.error("Failed to trigger Update Project", e)
                } finally {
                    isUpdating = false
                }
            }
        }, ModalityState.nonModal())
    }

    override fun dispose() {
    }

    companion object {
        fun getInstance(project: Project): GitPushDetectorService =
            project.getService(GitPushDetectorService::class.java)
    }
}

class GitPushListenerStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        GitPushDetectorService.getInstance(project).initialize()
    }
}

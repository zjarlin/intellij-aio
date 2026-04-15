package site.addzero.gradle.buddy.filter

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.util.GradleConstants
import site.addzero.gradle.buddy.i18n.GradleBuddyActionI18n
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import com.intellij.execution.executors.DefaultRunExecutor
import site.addzero.gradle.buddy.util.GradlePublishExecutionSupport
import site.addzero.gradle.buddy.util.GradlePublishTaskTracker

class RunPublishTasksAction : AnAction() {

    init {
        syncPresentation()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val targets = resolveTargets(e)
        if (targets.isEmpty()) {
            notify(
                project = project,
                title = GradleBuddyBundle.message("action.run.publish.tasks.none.title"),
                content = GradleBuddyBundle.message("action.run.publish.tasks.none.content"),
                type = NotificationType.WARNING,
            )
            return
        }

        val version = GradlePublishExecutionSupport.requestVersion(project, targets.size) ?: return
        val groupedTargets = targets.groupBy { it.rootPath }.toSortedMap()
        val tracker = GradlePublishTaskTracker(project)
        val failures = mutableListOf<Pair<String, String>>()

        targets.forEach { target ->
            val request = GradlePublishExecutionSupport.PublishTaskRequest(
                rootPath = target.rootPath,
                modulePath = target.modulePath,
                taskName = target.taskPath,
                version = version,
            )
            tracker.registerScheduledTask(request)

            runCatching {
                ExternalSystemUtil.runTask(
                    GradlePublishExecutionSupport.createTaskSettings(request),
                    DefaultRunExecutor.EXECUTOR_ID,
                    project,
                    GradleConstants.SYSTEM_ID,
                )
            }.onFailure { error ->
                tracker.markLaunchFailure(request)
                failures += target.modulePath to (error.message ?: error.javaClass.simpleName)
            }
        }

        val startedCount = targets.size - failures.size
        if (startedCount > 0) {
            val notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("GradleBuddy")
                .createNotification(
                    GradleBuddyBundle.message("action.run.publish.tasks.started.title"),
                    GradleBuddyBundle.message(
                        "action.run.publish.tasks.started.content",
                        version,
                        startedCount,
                        groupedTargets.size,
                    ),
                    NotificationType.INFORMATION,
                )

            notification.addAction(object : AnAction(GradleBuddyBundle.message("publish.cancel.all")) {
                override fun actionPerformed(e: AnActionEvent) {
                    val cancellingCount = tracker.requestCancelAll()
                    notification.expire()
                    notify(
                        project = project,
                        title = GradleBuddyBundle.message("publish.cancel.requested.title"),
                        content = GradleBuddyBundle.message(
                            "publish.cancel.requested.content",
                            cancellingCount,
                        ),
                        type = NotificationType.WARNING,
                    )
                }
            })

            notification.notify(project)
        }

        if (failures.isNotEmpty()) {
            val (modulePath, reason) = failures.first()
            notify(
                project = project,
                title = GradleBuddyBundle.message("action.run.publish.tasks.failed.title"),
                content = GradleBuddyBundle.message(
                    "action.run.publish.tasks.failed.content",
                    modulePath,
                    reason,
                    failures.size,
                ),
                type = NotificationType.ERROR,
            )
        }
    }

    override fun update(e: AnActionEvent) {
        syncPresentation(e.presentation)
        val project = e.project
        val selections = getSelections(e)
        e.presentation.isEnabledAndVisible = project != null &&
            selections.isNotEmpty() &&
            GradlePublishSelectionResolver.canResolve(project, selections)
    }

    private fun syncPresentation(presentation: com.intellij.openapi.actionSystem.Presentation? = null) {
        GradleBuddyActionI18n.sync(
            this,
            presentation,
            "action.run.publish.tasks.title",
            "action.run.publish.tasks.description",
        )
    }

    private fun resolveTargets(e: AnActionEvent): List<GradlePublishSelectionResolver.PublishTarget> {
        val project = e.project ?: return emptyList()
        return GradlePublishSelectionResolver.resolve(project, getSelections(e))
    }

    private fun getSelections(e: AnActionEvent): List<VirtualFile> {
        return e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
            ?.toList()
            ?.filterNotNull()
            ?.takeIf { it.isNotEmpty() }
            ?: e.getData(CommonDataKeys.VIRTUAL_FILE)?.let(::listOf).orEmpty()
    }

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(title, content, type)
            .notify(project)
    }
}

package site.addzero.gradle.buddy.util

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.gradle.util.GradleConstants
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object GradlePublishExecutionSupport {

    private val VERSION_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

    fun requestVersion(project: Project, publishCount: Int): String? {
        val defaultVersion = defaultVersion()
        return Messages.showInputDialog(
            project,
            GradleBuddyBundle.message(
                "publish.version.dialog.message",
                publishCount,
                defaultVersion,
            ),
            GradleBuddyBundle.message("publish.version.dialog.title"),
            Messages.getQuestionIcon(),
            defaultVersion,
            null,
        )?.trim()?.ifBlank { defaultVersion }
    }

    fun defaultVersion(): String = LocalDate.now().format(VERSION_FORMATTER)

    fun buildScriptParameters(version: String): String = "-Pversion=$version"

    fun buildCommandWithVersion(command: String, version: String): String = "$command ${buildScriptParameters(version)}"
}

class GradlePublishTaskTracker(
    rootPaths: Collection<String>,
    expectedTaskCount: Int,
) : ExternalSystemTaskNotificationListener {

    private val normalizedRootPaths = rootPaths.map(::normalizePath).toSet()
    private val trackedTaskIds = linkedSetOf<ExternalSystemTaskId>()
    private val finishedTaskIds = linkedSetOf<ExternalSystemTaskId>()
    private val lock = Any()

    @Volatile
    private var disposed = false

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var expectedTaskCount: Int = expectedTaskCount.coerceAtLeast(0)

    init {
        ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(this)
        if (this.expectedTaskCount == 0) {
            dispose()
        }
    }

    fun updateExpectedTaskCount(value: Int) {
        val shouldDispose = synchronized(lock) {
            expectedTaskCount = value.coerceAtLeast(0)
            shouldDisposeLocked()
        }
        if (shouldDispose) {
            dispose()
        }
    }

    fun requestCancelAll(): Int {
        val taskIds = synchronized(lock) {
            cancelRequested = true
            trackedTaskIds.toList()
        }
        taskIds.forEach(::cancelTask)
        return taskIds.size
    }

    override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        if (disposed || id.projectSystemId != GradleConstants.SYSTEM_ID || id.type != ExternalSystemTaskType.EXECUTE_TASK) {
            return
        }

        val normalizedWorkingDir = workingDir?.takeIf(String::isNotBlank)?.let(::normalizePath) ?: return
        val shouldCancel = synchronized(lock) {
            when {
                normalizedWorkingDir !in normalizedRootPaths -> false
                trackedTaskIds.size >= expectedTaskCount -> false
                !trackedTaskIds.add(id) -> false
                else -> cancelRequested
            }
        }

        if (shouldCancel) {
            cancelTask(id)
        }
    }

    override fun onEnd(id: ExternalSystemTaskId) = markStopped(id)

    override fun onSuccess(id: ExternalSystemTaskId) = markStopped(id)

    override fun onFailure(id: ExternalSystemTaskId, e: Exception) = markStopped(id)

    override fun onCancel(id: ExternalSystemTaskId) = markStopped(id)

    private fun markStopped(id: ExternalSystemTaskId) {
        val shouldDispose = synchronized(lock) {
            if (id in trackedTaskIds) {
                finishedTaskIds.add(id)
            }
            shouldDisposeLocked()
        }
        if (shouldDispose) {
            dispose()
        }
    }

    private fun shouldDisposeLocked(): Boolean {
        if (expectedTaskCount == 0) {
            return true
        }
        return trackedTaskIds.size >= expectedTaskCount && finishedTaskIds.containsAll(trackedTaskIds)
    }

    private fun cancelTask(id: ExternalSystemTaskId) {
        ExternalSystemProcessingManager.getInstance()
            .findTask(id)
            ?.cancel()
    }

    private fun dispose() {
        if (disposed) {
            return
        }
        disposed = true
        ExternalSystemProgressNotificationManager.getInstance().removeNotificationListener(this)
    }

    private fun normalizePath(path: String): String = File(path).absoluteFile.toPath().normalize().toString()
}

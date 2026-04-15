package site.addzero.gradle.buddy.util

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.gradle.util.GradleConstants
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque

object GradlePublishExecutionSupport {

    private val VERSION_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private const val MAX_SSL_RETRY_COUNT = 1

    data class PublishTaskRequest(
        val rootPath: String,
        val modulePath: String,
        val taskName: String,
        val version: String,
    ) {
        val executionName: String
            get() = "Gradle Buddy Publish $modulePath ($version)"
    }

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

    fun createTaskSettings(request: PublishTaskRequest): ExternalSystemTaskExecutionSettings {
        return ExternalSystemTaskExecutionSettings().apply {
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
            externalProjectPath = request.rootPath
            taskNames = listOf(request.taskName)
            scriptParameters = buildScriptParameters(request.version)
            executionName = request.executionName
        }
    }

    fun shouldRetrySslFailure(error: Throwable?, taskOutput: String): Boolean {
        val combinedText = buildString {
            append(taskOutput)
            var current: Throwable? = error
            val visited = mutableSetOf<Throwable>()
            while (current != null && visited.add(current)) {
                append('\n')
                append(current.javaClass.name)
                append(": ")
                append(current.message.orEmpty())
                current = current.cause
            }
        }.lowercase()

        return combinedText.contains("ssl peer shut down incorrectly")
            || combinedText.contains("sslhandshakeexception")
            || combinedText.contains("received fatal alert")
            || combinedText.contains("remote host terminated the handshake")
            || combinedText.contains("eofexception")
    }

    fun maxSslRetryCount(): Int = MAX_SSL_RETRY_COUNT
}

class GradlePublishTaskTracker(
    private val project: Project,
) : ExternalSystemTaskNotificationListener {

    private val trackedTaskIds = linkedSetOf<ExternalSystemTaskId>()
    private val finishedTaskIds = linkedSetOf<ExternalSystemTaskId>()
    private val pendingRequestsByRoot = linkedMapOf<String, ArrayDeque<GradlePublishExecutionSupport.PublishTaskRequest>>()
    private val requestByTaskId = linkedMapOf<ExternalSystemTaskId, GradlePublishExecutionSupport.PublishTaskRequest>()
    private val outputByTaskId = linkedMapOf<ExternalSystemTaskId, StringBuilder>()
    private val retryCountByRequestKey = linkedMapOf<String, Int>()
    private val lock = Any()

    @Volatile
    private var disposed = false

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var expectedTaskCount: Int = 0

    init {
        ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(this)
    }

    fun registerScheduledTask(
        request: GradlePublishExecutionSupport.PublishTaskRequest,
        addToFront: Boolean = false,
    ) {
        val normalizedRoot = normalizePath(request.rootPath)
        synchronized(lock) {
            val queue = pendingRequestsByRoot.getOrPut(normalizedRoot) { ArrayDeque() }
            if (addToFront) {
                queue.addFirst(request)
            } else {
                queue.addLast(request)
            }
            expectedTaskCount++
        }
    }

    fun markLaunchFailure(request: GradlePublishExecutionSupport.PublishTaskRequest) {
        val shouldDispose = synchronized(lock) {
            removePendingRequestLocked(request)
            expectedTaskCount = (expectedTaskCount - 1).coerceAtLeast(0)
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
            val requestQueue = pendingRequestsByRoot[normalizedWorkingDir] ?: return@synchronized false
            if (requestQueue.isEmpty()) {
                pendingRequestsByRoot.remove(normalizedWorkingDir)
                return@synchronized false
            }
            val request = requestQueue.removeFirst()
            if (requestQueue.isEmpty()) {
                pendingRequestsByRoot.remove(normalizedWorkingDir)
            }
            trackedTaskIds.add(id)
            requestByTaskId[id] = request
            cancelRequested
        }

        if (shouldCancel) {
            cancelTask(id)
        }
    }

    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
        synchronized(lock) {
            if (id !in trackedTaskIds) {
                return
            }
            val buffer = outputByTaskId.getOrPut(id) { StringBuilder() }
            buffer.append(text)
            if (buffer.length > MAX_OUTPUT_BUFFER_LENGTH) {
                buffer.delete(0, buffer.length - MAX_OUTPUT_BUFFER_LENGTH)
            }
        }
    }

    override fun onEnd(id: ExternalSystemTaskId) = markStopped(id)

    override fun onSuccess(id: ExternalSystemTaskId) = markStopped(id)

    override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
        val retryRequest = synchronized(lock) {
            val request = requestByTaskId[id] ?: return@synchronized null
            val taskOutput = outputByTaskId[id]?.toString().orEmpty()
            if (cancelRequested || !GradlePublishExecutionSupport.shouldRetrySslFailure(e, taskOutput)) {
                return@synchronized null
            }

            val requestKey = request.requestKey()
            val currentRetryCount = retryCountByRequestKey[requestKey] ?: 0
            if (currentRetryCount >= GradlePublishExecutionSupport.maxSslRetryCount()) {
                return@synchronized null
            }

            retryCountByRequestKey[requestKey] = currentRetryCount + 1
            registerRetryLocked(request)
            request
        }

        if (retryRequest != null) {
            scheduleRetry(retryRequest)
        }
        markStopped(id)
    }

    override fun onCancel(id: ExternalSystemTaskId) = markStopped(id)

    private fun markStopped(id: ExternalSystemTaskId) {
        val shouldDispose = synchronized(lock) {
            if (id in trackedTaskIds) {
                finishedTaskIds.add(id)
            }
            requestByTaskId.remove(id)
            outputByTaskId.remove(id)
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

    private fun registerRetryLocked(request: GradlePublishExecutionSupport.PublishTaskRequest) {
        val normalizedRoot = normalizePath(request.rootPath)
        val queue = pendingRequestsByRoot.getOrPut(normalizedRoot) { ArrayDeque() }
        queue.addFirst(request)
        expectedTaskCount++
    }

    private fun removePendingRequestLocked(request: GradlePublishExecutionSupport.PublishTaskRequest) {
        val normalizedRoot = normalizePath(request.rootPath)
        val queue = pendingRequestsByRoot[normalizedRoot] ?: return
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val candidate = iterator.next()
            if (candidate.requestKey() == request.requestKey()) {
                iterator.remove()
                break
            }
        }
        if (queue.isEmpty()) {
            pendingRequestsByRoot.remove(normalizedRoot)
        }
    }

    private fun scheduleRetry(request: GradlePublishExecutionSupport.PublishTaskRequest) {
        notify(
            title = GradleBuddyBundle.message("publish.retry.started.title"),
            content = GradleBuddyBundle.message(
                "publish.retry.started.content",
                request.modulePath,
                GradlePublishExecutionSupport.maxSslRetryCount(),
            ),
            type = NotificationType.WARNING,
        )

        runCatching {
            ExternalSystemUtil.runTask(
                GradlePublishExecutionSupport.createTaskSettings(request),
                DefaultRunExecutor.EXECUTOR_ID,
                project,
                GradleConstants.SYSTEM_ID,
            )
        }.onFailure { error ->
            markLaunchFailure(request)
            notify(
                title = GradleBuddyBundle.message("publish.retry.failed.title"),
                content = GradleBuddyBundle.message(
                    "publish.retry.failed.content",
                    request.modulePath,
                    error.message ?: error.javaClass.simpleName,
                ),
                type = NotificationType.ERROR,
            )
        }
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

    private fun GradlePublishExecutionSupport.PublishTaskRequest.requestKey(): String {
        return normalizePath(rootPath) + "|" + taskName + "|" + modulePath + "|" + version
    }

    private fun notify(title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(title, content, type)
            .notify(project)
    }

    companion object {
        private const val MAX_OUTPUT_BUFFER_LENGTH = 16_384
    }
}

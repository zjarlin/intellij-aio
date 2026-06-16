package site.addzero.dioxus.buddy.runner

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import site.addzero.dioxus.buddy.model.DioxusPreviewTarget
import site.addzero.dioxus.buddy.preview.DioxusPreviewSandboxWriter

object DioxusPreviewRunner {
    fun run(
        project: Project,
        target: DioxusPreviewTarget,
    ) {
        try {
            val sandbox = DioxusPreviewSandboxWriter.write(target)
            val commandLine = createServeCommand(target, sandbox.rootPath.toString())
            val processHandler = ColoredProcessHandler(commandLine)
            val console = TextConsoleBuilderFactory.getInstance()
                .createBuilder(project)
                .console
            console.attachToProcess(processHandler)

            val descriptor = RunContentDescriptor(
                console,
                processHandler,
                console.component,
                "Dioxus preview - ${target.displayName}",
            )
            RunContentManager.getInstance(project)
                .showRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor)
            processHandler.startNotify()
            BrowserUtil.browse(sandbox.url)
            notify(
                project = project,
                title = "Dioxus preview started",
                message = "${target.functionName} is serving at ${sandbox.url}",
                type = NotificationType.INFORMATION,
            )
        } catch (e: Exception) {
            notify(
                project = project,
                title = "Unable to run Dioxus preview",
                message = e.message ?: e.javaClass.simpleName,
                type = NotificationType.ERROR,
            )
        }
    }

    private fun createServeCommand(
        target: DioxusPreviewTarget,
        workDirectory: String,
    ): GeneralCommandLine {
        return if (SystemInfo.isWindows) {
            GeneralCommandLine("cmd")
                .withWorkDirectory(workDirectory)
                .withCharset(Charsets.UTF_8)
                .withParameters(
                    "/c",
                    "dx serve --platform web --port ${target.previewPort}",
                )
        } else {
            GeneralCommandLine("sh")
                .withWorkDirectory(workDirectory)
                .withCharset(Charsets.UTF_8)
                .withParameters(
                    "-lc",
                    "dx serve --platform web --port ${target.previewPort}",
                )
        }
    }

    private fun notify(
        project: Project,
        title: String,
        message: String,
        type: NotificationType,
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("DioxusBuddy")
            .createNotification(title, message, type)
            .notify(project)
    }
}

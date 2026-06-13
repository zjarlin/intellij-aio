package site.addzero.cargo.buddy.runner

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import site.addzero.cargo.buddy.model.CargoCrate

object CargoCommandRunner {
    fun run(
        project: Project,
        cargoCrate: CargoCrate,
        commandName: String,
    ) {
        try {
            val commandLine = createCommandLine(cargoCrate, commandName)
            val processHandler = ColoredProcessHandler(commandLine)
            val console = TextConsoleBuilderFactory.getInstance()
                .createBuilder(project)
                .console
            console.attachToProcess(processHandler)

            val displayName = "cargo $commandName - ${cargoCrate.displayName}"
            val descriptor = RunContentDescriptor(
                console,
                processHandler,
                console.component,
                displayName,
            )
            RunContentManager.getInstance(project)
                .showRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor)
            processHandler.startNotify()
        } catch (e: Exception) {
            notifyError(project, commandName, e)
        }
    }

    private fun createCommandLine(
        cargoCrate: CargoCrate,
        commandName: String,
    ): GeneralCommandLine {
        return GeneralCommandLine("cargo")
            .withWorkDirectory(cargoCrate.rootPath)
            .withCharset(Charsets.UTF_8)
            .withParameters(commandName, "--manifest-path", cargoCrate.manifestPath)
    }

    private fun notifyError(
        project: Project,
        commandName: String,
        error: Exception,
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("CargoBuddy")
            .createNotification(
                "Unable to run cargo $commandName",
                error.message ?: error.javaClass.simpleName,
                NotificationType.ERROR,
            )
            .notify(project)
    }
}

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
import com.intellij.openapi.util.SystemInfo
import site.addzero.cargo.buddy.model.CargoCrate
import site.addzero.cargo.buddy.publish.CargoPublishPlan

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

    fun runPublishPlan(
        project: Project,
        cargoCrate: CargoCrate,
        publishPlan: CargoPublishPlan,
    ) {
        try {
            val commandLine = createPublishPlanCommandLine(cargoCrate, publishPlan)
            val processHandler = ColoredProcessHandler(commandLine)
            val console = TextConsoleBuilderFactory.getInstance()
                .createBuilder(project)
                .console
            console.attachToProcess(processHandler)

            val displayName = "cargo publish deps - ${cargoCrate.displayName}"
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
            notifyError(project, "publish deps", e)
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

    private fun createPublishPlanCommandLine(
        cargoCrate: CargoCrate,
        publishPlan: CargoPublishPlan,
    ): GeneralCommandLine {
        val script = publishPlan.packages.joinToString(separator = " && ") { cargoPackage ->
            "echo ${shellQuote("Publishing ${cargoPackage.displayName}")} && " +
                "cargo publish --manifest-path ${shellQuote(cargoPackage.manifestPath)}"
        }
        return if (SystemInfo.isWindows) {
            GeneralCommandLine("cmd")
                .withWorkDirectory(cargoCrate.workspaceRootPath)
                .withCharset(Charsets.UTF_8)
                .withParameters("/c", script)
        } else {
            GeneralCommandLine("sh")
                .withWorkDirectory(cargoCrate.workspaceRootPath)
                .withCharset(Charsets.UTF_8)
                .withParameters("-lc", script)
        }
    }

    private fun shellQuote(value: String): String {
        return if (SystemInfo.isWindows) {
            "\"${value.replace("\"", "\\\"")}\""
        } else {
            "'${value.replace("'", "'\"'\"'")}'"
        }
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

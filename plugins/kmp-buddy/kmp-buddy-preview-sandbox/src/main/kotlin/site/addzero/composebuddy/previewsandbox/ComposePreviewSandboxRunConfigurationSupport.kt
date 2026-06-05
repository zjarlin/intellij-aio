package site.addzero.composebuddy.previewsandbox

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object ComposePreviewSandboxRunConfigurationSupport {
    fun run(
        project: Project,
        session: ComposePreviewSandboxSession,
        onStatus: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val target = ComposePreviewSandboxRunTarget(session)
        val commandLine = createCommandLine(project, session, target)
        val output = StringBuilder()
        val processHandler = OSProcessHandler(commandLine)

        processHandler.addProcessListener(
            object : ProcessAdapter() {
                override fun startNotified(event: ProcessEvent) {
                    publish(
                        project,
                        onStatus,
                        "Gradle :${target.taskName} launched with ${PreviewSandboxGradleJvm.JAVA_HOME_ENV}=${session.snapshot.gradleJavaHome}.",
                    )
                }

                override fun onTextAvailable(
                    event: ProcessEvent,
                    outputType: Key<*>,
                ) {
                    output.append(event.text)
                    val line = event.text.trim()
                    if (line.isNotBlank() && outputType === ProcessOutputTypes.STDERR) {
                        publish(project, onStatus, line)
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    if (event.exitCode == 0) {
                        publish(project, onStatus, "Compose preview window closed.")
                        return
                    }

                    publish(
                        project,
                        onError,
                        buildFailureMessage(
                            exitCode = event.exitCode,
                            commandLine = commandLine.commandLineString,
                            output = output.toString(),
                        ),
                    )
                }
            },
        )
        processHandler.startNotify()
    }

    private fun createCommandLine(
        project: Project,
        session: ComposePreviewSandboxSession,
        target: ComposePreviewSandboxRunTarget,
    ): GeneralCommandLine {
        val javaHome = session.snapshot.gradleJavaHome
        val parameters = buildList {
            add("-p")
            add(target.externalProjectPath)
            if (javaHome.isNotBlank()) {
                add(PreviewSandboxGradleJvm.gradleSystemPropertyArgument(javaHome))
            }
            add(target.taskName)
            add("--no-configuration-cache")
        }

        return GeneralCommandLine(resolveGradleCommand(project, session))
            .withParameters(parameters)
            .withWorkDirectory(session.written.rootDirectory.toString())
            .withCharset(Charsets.UTF_8)
            .apply {
                if (javaHome.isNotBlank()) {
                    environment[PreviewSandboxGradleJvm.JAVA_HOME_ENV] = javaHome
                }
            }
    }

    private fun resolveGradleCommand(
        project: Project,
        session: ComposePreviewSandboxSession,
    ): String {
        val basePath = project.basePath
        val wrapperName = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            "gradlew.bat"
        } else {
            "gradlew"
        }
        val projectWrapper = basePath
            ?.let(Paths::get)
            ?.resolve(wrapperName)
            ?.takeIf { path -> path.isExecutableFile() }
        if (projectWrapper != null) {
            return projectWrapper.toString()
        }

        val sandboxWrapper = session.written.rootDirectory
            .resolve(wrapperName)
            .takeIf { path -> path.isExecutableFile() }
        if (sandboxWrapper != null) {
            return sandboxWrapper.toString()
        }

        return if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            "gradle.bat"
        } else {
            "gradle"
        }
    }

    private fun buildFailureMessage(
        exitCode: Int,
        commandLine: String,
        output: String,
    ): String {
        return buildString {
            appendLine("Unable to launch the external Compose preview window.")
            appendLine()
            appendLine("Gradle preview run failed with exit code $exitCode.")
            appendLine()
            appendLine(commandLine)
            if (output.isNotBlank()) {
                appendLine()
                appendLine("output:")
                appendLine(output.trimEnd())
            }
        }.trim()
    }

    private fun publish(
        project: Project,
        callback: (String) -> Unit,
        message: String,
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                callback(message)
            }
        }
    }

    private fun Path.isExecutableFile(): Boolean {
        return Files.isRegularFile(this) && Files.isExecutable(this)
    }
}

private data class ComposePreviewSandboxRunTarget(
    val externalProjectPath: String,
    val taskName: String,
) {
    constructor(session: ComposePreviewSandboxSession) : this(
        externalProjectPath = session.written.rootDirectory.toString(),
        taskName = "run",
    )
}

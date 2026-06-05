package site.addzero.composebuddy.previewsandbox

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.SwingUtilities

object ComposePreviewSandboxEmbeddedRenderer {
    fun render(
        project: Project,
        session: ComposePreviewSandboxSession,
        onStatus: (String) -> Unit,
        onRendered: (JComponent) -> Unit,
        onError: (String) -> Unit,
    ) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Render KMP Buddy Compose Preview", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Building preview sandbox"
                    publishStatus(project, onStatus, "Building minimal Compose preview sandbox...")
                    try {
                        writeClasspath(project, session, indicator)
                        indicator.text = "Loading preview component"
                        publishStatus(project, onStatus, "Loading Compose preview panel...")
                        val component = loadPreviewPanel(session)
                        ApplicationManager.getApplication().invokeLater {
                            if (!project.isDisposed) {
                                onRendered(component)
                            }
                        }
                    } catch (exception: ProcessCanceledException) {
                        throw exception
                    } catch (throwable: Throwable) {
                        publishStatus(
                            project = project,
                            onStatus = onError,
                            message = ComposePreviewSandboxErrorFormatter.format(
                                context = "Unable to render the graphical Compose preview in panel.",
                                throwable = throwable,
                            ),
                        )
                    }
                }
            },
        )
    }

    private fun writeClasspath(
        project: Project,
        session: ComposePreviewSandboxSession,
        indicator: ProgressIndicator,
    ) {
        val gradleCommand = resolveGradleCommand(project)
        val commandLine = GeneralCommandLine(gradleCommand)
            .withWorkDirectory(project.basePath ?: session.written.rootDirectory.toString())
            .withCharset(Charsets.UTF_8)
            .apply {
                addParameter("-p")
                addParameter(session.written.rootDirectory.toString())
            }
        session.snapshot.gradleJavaHome
            .takeIf(String::isNotBlank)
            ?.let { javaHome ->
                commandLine.addParameter(PreviewSandboxGradleJvm.gradleSystemPropertyArgument(javaHome))
                commandLine.environment[PreviewSandboxGradleJvm.JAVA_HOME_ENV] = javaHome
            }
        commandLine.addParameter("writeKmpBuddyPreviewClasspath")
        commandLine.addParameter("--no-configuration-cache")

        val output = CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator)
        if (output.exitCode != 0) {
            val message = buildString {
                appendLine("Gradle preview build failed with exit code ${output.exitCode}.")
                appendLine()
                appendLine(commandLine.commandLineString)
                appendLine()
                if (output.stderr.isNotBlank()) {
                    appendLine("stderr:")
                    appendLine(output.stderr.trimEnd())
                    appendLine()
                }
                if (output.stdout.isNotBlank()) {
                    appendLine("stdout:")
                    appendLine(output.stdout.trimEnd())
                }
            }.trim()
            throw IllegalStateException(message)
        }
    }

    private fun loadPreviewPanel(session: ComposePreviewSandboxSession): JComponent {
        val classpathText = String(Files.readAllBytes(session.written.classpathFile), Charsets.UTF_8)
        val urls = classpathText
            .split(File.pathSeparator)
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { path -> Paths.get(path).toUri().toURL() }
            .toTypedArray()
        val loader = URLClassLoader(urls, ComposePreviewSandboxEmbeddedRenderer::class.java.classLoader)
        val runnerClass = loader.loadClass(session.written.runnerMainClass)
        val factoryMethod = runnerClass.getDeclaredMethod("createKmpBuddyPreviewPanel")
        return invokeAndWaitOnEdt {
            factoryMethod.invoke(null) as? JComponent
                ?: throw IllegalStateException("Generated preview factory did not return a Swing component.")
        }
    }

    private fun <T : Any> invokeAndWaitOnEdt(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) {
            return action()
        }

        val result = AtomicReference<T>()
        val failure = AtomicReference<Throwable>()
        SwingUtilities.invokeAndWait {
            try {
                result.set(action())
            } catch (throwable: Throwable) {
                failure.set(throwable)
            }
        }
        failure.get()?.let { throwable -> throw throwable }
        return requireNotNull(result.get()) {
            "EDT preview factory did not produce a result."
        }
    }

    private fun resolveGradleCommand(project: Project): String {
        val basePath = project.basePath ?: return "gradle"
        val wrapperName = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            "gradlew.bat"
        } else {
            "gradlew"
        }
        val wrapper = Paths.get(basePath).resolve(wrapperName)
        return wrapper.takeIf { path -> path.isExecutableFile() }?.toString() ?: "gradle"
    }

    private fun Path.isExecutableFile(): Boolean {
        return Files.isRegularFile(this) && Files.isExecutable(this)
    }

    private fun publishStatus(
        project: Project,
        onStatus: (String) -> Unit,
        message: String,
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                onStatus(message)
            }
        }
    }
}

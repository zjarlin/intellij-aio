package site.addzero.deploy.pipeline

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 构建工具类型
 */
sealed class BuildTool {
    abstract val displayName: String

    data class Maven(
        val pomPath: String,
        val goals: String = "clean package -DskipTests"
    ) : BuildTool() {
        override val displayName = "Maven"
    }

    data class Gradle(
        val projectPath: String,
        val tasks: String = "clean build -x test"
    ) : BuildTool() {
        override val displayName = "Gradle"
    }

    data object None : BuildTool() {
        override val displayName = "None"
    }
}

/**
 * 构建结果
 */
data class BuildResult(
    val success: Boolean,
    val exitCode: Int,
    val output: String,
    val duration: Long
)

/**
 * 构建执行器
 */
class BuildExecutor(
    private val project: Project,
    private val sessionService: DeploySessionService,
    private val sessionId: String
) {
    private val log = Logger.getInstance(BuildExecutor::class.java)

    fun execute(
        buildTool: BuildTool,
        onProgress: (Double, String) -> Unit
    ): BuildResult {
        return when (buildTool) {
            is BuildTool.Maven -> executeMaven(buildTool, onProgress)
            is BuildTool.Gradle -> executeGradle(buildTool, onProgress)
            is BuildTool.None -> BuildResult(true, 0, "No build required", 0)
        }
    }

    private fun executeMaven(maven: BuildTool.Maven, onProgress: (Double, String) -> Unit): BuildResult {
        val startTime = System.currentTimeMillis()
        val pomFile = File(maven.pomPath)
        val workDir = if (pomFile.isFile) pomFile.parentFile else pomFile

        addLog(LogLevel.INFO, "Starting Maven build: ${maven.goals}")
        onProgress(0.1, "Starting Maven...")

        val mvnCmd = findMavenExecutable(workDir)
        val commandLine = GeneralCommandLine().apply {
            workDirectory = workDir
            exePath = mvnCmd
            addParameters(maven.goals.split(" ").filter { it.isNotBlank() })
            environment["JAVA_HOME"] = System.getProperty("java.home")
        }

        return executeProcess(commandLine, startTime, onProgress)
    }

    private fun executeGradle(gradle: BuildTool.Gradle, onProgress: (Double, String) -> Unit): BuildResult {
        val startTime = System.currentTimeMillis()
        val projectDir = File(gradle.projectPath)

        addLog(LogLevel.INFO, "Starting Gradle build: ${gradle.tasks}")
        onProgress(0.1, "Starting Gradle...")

        val gradleCmd = findGradleExecutable(projectDir)
        val commandLine = GeneralCommandLine().apply {
            workDirectory = projectDir
            exePath = gradleCmd
            addParameters(gradle.tasks.split(" ").filter { it.isNotBlank() })
            environment["JAVA_HOME"] = System.getProperty("java.home")
        }

        return executeProcess(commandLine, startTime, onProgress)
    }

    private fun executeProcess(
        commandLine: GeneralCommandLine,
        startTime: Long,
        onProgress: (Double, String) -> Unit
    ): BuildResult {
        val outputBuilder = StringBuilder()
        val success = AtomicBoolean(true)
        val exitCode = AtomicInteger(0)
        val latch = CountDownLatch(1)
        val lineCount = AtomicInteger(0)

        try {
            val processHandler = OSProcessHandler(commandLine)

            processHandler.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    val text = event.text
                    outputBuilder.append(text)

                    val currentLine = lineCount.incrementAndGet()
                    val estimatedProgress = (0.1 + 0.8 * (currentLine / 100.0).coerceAtMost(1.0))

                    val trimmedText = text.trim()
                    if (trimmedText.isNotEmpty()) {
                        if (outputType === ProcessOutputTypes.STDERR) {
                            if (trimmedText.contains("ERROR") || trimmedText.contains("FAILURE")) {
                                addLog(LogLevel.ERROR, trimmedText)
                            } else {
                                addLog(LogLevel.WARN, trimmedText)
                            }
                        } else {
                            when {
                                trimmedText.contains("BUILD SUCCESS") -> addLog(LogLevel.INFO, "Build successful")
                                trimmedText.contains("BUILD FAILURE") -> addLog(LogLevel.ERROR, "Build failed")
                                trimmedText.contains("Compiling") -> {
                                    addLog(LogLevel.INFO, trimmedText)
                                    onProgress(estimatedProgress, "Compiling...")
                                }
                                trimmedText.contains("Downloading") -> {
                                    addLog(LogLevel.DEBUG, trimmedText)
                                    onProgress(estimatedProgress, "Downloading dependencies...")
                                }
                                trimmedText.contains(":test") || trimmedText.contains("Running test") -> {
                                    addLog(LogLevel.INFO, trimmedText)
                                    onProgress(estimatedProgress, "Running tests...")
                                }
                                trimmedText.contains(":jar") || trimmedText.contains("Building jar") -> {
                                    addLog(LogLevel.INFO, trimmedText)
                                    onProgress(0.9, "Packaging...")
                                }
                            }
                        }
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    exitCode.set(event.exitCode)
                    success.set(event.exitCode == 0)
                    latch.countDown()
                }
            })

            processHandler.startNotify()

            val completed = latch.await(30, TimeUnit.MINUTES)
            if (!completed) {
                processHandler.destroyProcess()
                addLog(LogLevel.ERROR, "Build timed out after 30 minutes")
                return BuildResult(false, -1, "Build timed out", System.currentTimeMillis() - startTime)
            }

            val duration = System.currentTimeMillis() - startTime
            if (success.get()) {
                addLog(LogLevel.INFO, "Build completed in ${duration / 1000}s")
                onProgress(1.0, "Build completed")
            } else {
                addLog(LogLevel.ERROR, "Build failed with exit code ${exitCode.get()}")
            }

            return BuildResult(success.get(), exitCode.get(), outputBuilder.toString(), duration)

        } catch (e: Exception) {
            log.error("Build execution failed", e)
            addLog(LogLevel.ERROR, "Build failed: ${e.message}")
            return BuildResult(false, -1, e.message ?: "Unknown error", System.currentTimeMillis() - startTime)
        }
    }

    private fun findMavenExecutable(workDir: File): String {
        val mvnw = File(workDir, if (isWindows()) "mvnw.cmd" else "mvnw")
        if (mvnw.exists() && mvnw.canExecute()) {
            return mvnw.absolutePath
        }
        return if (isWindows()) "mvn.cmd" else "mvn"
    }

    private fun findGradleExecutable(workDir: File): String {
        val gradlew = File(workDir, if (isWindows()) "gradlew.bat" else "gradlew")
        if (gradlew.exists() && gradlew.canExecute()) {
            return gradlew.absolutePath
        }
        return if (isWindows()) "gradle.bat" else "gradle"
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("windows")

    private fun addLog(level: LogLevel, message: String) {
        sessionService.addLog(sessionId, DeployPhase.BUILD, level, message)
    }
}

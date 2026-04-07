package site.addzero.gradle.buddy.intentions.projectdep

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleConstants
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import site.addzero.gradle.buddy.migration.PublishCommandEntry
import site.addzero.gradle.buddy.migration.PublishCommandQueueService
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import java.awt.datatransfer.StringSelection
import java.io.File

object ProjectDependencySourcePublishSupport {

    data class PublishTarget(
        val moduleName: String,
        val modulePath: String,
        val repoRoot: File,
        val moduleDir: File,
        val command: String,
        val taskName: String
    )

    fun findTarget(project: Project, file: com.intellij.psi.PsiFile, offset: Int): CopyProjectDependencyAsMavenSupport.Target? {
        val target = CopyProjectDependencyAsMavenSupport.findTarget(project, file, offset) ?: return null
        return target.takeIf { !it.canonicalModulePath.isNullOrBlank() }
    }

    fun execute(project: Project, target: CopyProjectDependencyAsMavenSupport.Target) {
        val publishTarget = resolvePublishTarget(project, target) ?: return
        val taskSettings = ExternalSystemTaskExecutionSettings().apply {
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
            externalProjectPath = publishTarget.repoRoot.absolutePath
            taskNames = listOf(publishTarget.taskName)
        }

        try {
            ExternalSystemUtil.runTask(
                taskSettings,
                com.intellij.execution.executors.DefaultRunExecutor.EXECUTOR_ID,
                project,
                GradleConstants.SYSTEM_ID
            )
            showStartedNotification(project, publishTarget)
        } catch (t: Throwable) {
            notify(
                project = project,
                title = GradleBuddyBundle.message("intention.publish.project.dependency.from.source.failed.title"),
                content = GradleBuddyBundle.message(
                    "intention.publish.project.dependency.from.source.failed.content",
                    publishTarget.modulePath,
                    publishTarget.command,
                    t.message ?: t.javaClass.simpleName
                ),
                type = NotificationType.ERROR
            )
        }
    }

    private fun resolvePublishTarget(
        project: Project,
        target: CopyProjectDependencyAsMavenSupport.Target
    ): PublishTarget? {
        val modulePath = target.canonicalModulePath
        if (modulePath.isNullOrBlank()) {
            return null
        }

        target.sourceGradleRootPath
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?.takeIf { it.exists() && it.isDirectory }
            ?.let { currentRoot ->
                resolveModuleInRepo(target, modulePath, currentRoot)?.let { return it }
            }

        val settings = GradleBuddySettingsService.getInstance(project)
        val repoRoot = settings.resolveExternalLibraryRepoRoot(project)
        if (!repoRoot.exists() || !repoRoot.isDirectory) {
            notify(
                project = project,
                title = GradleBuddyBundle.message("intention.publish.project.dependency.from.source.missing.root.title"),
                content = GradleBuddyBundle.message(
                    "intention.publish.project.dependency.from.source.missing.root.content",
                    repoRoot.absolutePath
                ),
                type = NotificationType.WARNING
            )
            return null
        }

        val externalTarget = resolveModuleInRepo(target, modulePath, repoRoot)
        if (externalTarget == null) {
            val expectedDir = buildExpectedModuleDir(repoRoot, modulePath)
            notify(
                project = project,
                title = GradleBuddyBundle.message("intention.publish.project.dependency.from.source.missing.module.title"),
                content = GradleBuddyBundle.message(
                    "intention.publish.project.dependency.from.source.missing.module.content",
                    modulePath,
                    expectedDir.absolutePath
                ),
                type = NotificationType.WARNING
            )
            return null
        }

        return externalTarget
    }

    private fun showStartedNotification(project: Project, target: PublishTarget) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(
                GradleBuddyBundle.message("intention.publish.project.dependency.from.source.started.title"),
                GradleBuddyBundle.message(
                    "intention.publish.project.dependency.from.source.started.content",
                    target.repoRoot.absolutePath,
                    target.command
                ),
                NotificationType.INFORMATION
            )

        notification.addAction(object : AnAction(
            GradleBuddyBundle.message("intention.publish.project.dependency.from.source.copy.command")
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                CopyPasteManager.getInstance().setContents(StringSelection(target.command))
                notify(
                    project = project,
                    title = GradleBuddyBundle.message("intention.publish.project.dependency.from.source.command.copied.title"),
                    content = GradleBuddyBundle.message(
                        "intention.publish.project.dependency.from.source.command.copied.content",
                        target.command
                    ),
                    type = NotificationType.INFORMATION
                )
            }
        })

        notification.addAction(object : AnAction(
            GradleBuddyBundle.message("intention.publish.project.dependency.from.source.enqueue.command")
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                val added = project.getService(PublishCommandQueueService::class.java)
                    .addAll(
                        listOf(
                            PublishCommandEntry(
                                moduleName = target.moduleName,
                                modulePath = target.modulePath,
                                rootPath = target.repoRoot.absolutePath,
                                command = target.command
                            )
                        )
                    )
                notify(
                    project = project,
                    title = GradleBuddyBundle.message("intention.publish.project.dependency.from.source.command.enqueued.title"),
                    content = GradleBuddyBundle.message(
                        "intention.publish.project.dependency.from.source.command.enqueued.content",
                        added
                    ),
                    type = NotificationType.INFORMATION
                )
            }
        })

        notification.notify(project)
    }

    private fun findBuildFile(moduleDir: File): File? {
        if (!moduleDir.exists() || !moduleDir.isDirectory) {
            return null
        }
        val kts = File(moduleDir, "build.gradle.kts")
        if (kts.isFile) {
            return kts
        }
        val groovy = File(moduleDir, "build.gradle")
        return groovy.takeIf { it.isFile }
    }

    private fun resolveModuleInRepo(
        target: CopyProjectDependencyAsMavenSupport.Target,
        modulePath: String,
        repoRoot: File
    ): PublishTarget? {
        val moduleDir = buildExpectedModuleDir(repoRoot, modulePath)
        val buildFile = findBuildFile(moduleDir) ?: return null
        val taskName = if (modulePath == ":") "publishToMavenCentral" else "$modulePath:publishToMavenCentral"
        val command = if (modulePath == ":") "./gradlew publishToMavenCentral" else "./gradlew $modulePath:publishToMavenCentral"

        return PublishTarget(
            moduleName = target.moduleName,
            modulePath = modulePath,
            repoRoot = repoRoot,
            moduleDir = buildFile.parentFile ?: moduleDir,
            command = command,
            taskName = taskName
        )
    }

    private fun buildExpectedModuleDir(repoRoot: File, modulePath: String): File {
        return if (modulePath == ":") {
            repoRoot
        } else {
            File(repoRoot, modulePath.trim(':').replace(':', File.separatorChar))
        }
    }

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(title, content, type)
            .notify(project)
    }
}

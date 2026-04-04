package site.addzero.smart.intentions.actions

import com.intellij.icons.AllIcons
import com.intellij.execution.RunManager
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.pathString
import site.addzero.smart.intentions.hiddenfiles.IdeKitBundle

class LocateCurrentRunTargetAction : DumbAwareAction(
    IdeKitBundle.message("action.locate.run.target.text"),
    IdeKitBundle.message("action.locate.run.target.description"),
    AllIcons.General.Locate,
) {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project?.let(RunTargetResolver::resolve) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val target = RunTargetResolver.resolve(project)
        if (target == null) {
            notify(
                project = project,
                content = IdeKitBundle.message("notification.locate.run.target.not.found"),
                type = NotificationType.WARNING,
            )
            return
        }

        revealInProjectView(project, target)
    }

    private fun revealInProjectView(project: Project, target: RunTarget) {
        val projectView = ProjectView.getInstance(project)
        projectView.changeView(ProjectViewPane.ID)

        val psiManager = PsiManager.getInstance(project)
        val element = psiManager.findFile(target.virtualFile) ?: psiManager.findDirectory(target.virtualFile)
        projectView.select(element ?: target.virtualFile, target.virtualFile, true)

        notify(
            project = project,
            content = IdeKitBundle.message("notification.locate.run.target.revealed", target.presentablePath),
            type = NotificationType.INFORMATION,
        )
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("IdeKit Notifications")
            .createNotification(content, type)
            .notify(project)
    }
}

private data class RunTarget(
    val virtualFile: VirtualFile,
    val presentablePath: String,
)

private object RunTargetResolver {
    private val supportedGradleTasks = setOf("runIde", "runIdeForUiTests")

    fun resolve(project: Project): RunTarget? {
        val settings = RunManager.getInstance(project).selectedConfiguration ?: return null
        val configuration = settings.configuration as? ExternalSystemRunConfiguration ?: return null
        val externalSettings = configuration.settings
        val rootPath = externalSettings.externalProjectPath ?: project.basePath ?: return null
        val rootDir = Paths.get(rootPath)
        val taskNames = externalSettings.taskNames.takeIf { it.isNotEmpty() } ?: parseTaskNames(settings.name)

        return taskNames.asSequence()
            .mapNotNull { taskName -> resolveGradleTask(rootDir, taskName) }
            .firstOrNull()
    }

    private fun parseTaskNames(configurationName: String): List<String> {
        val bracketed = "\\[(.*)]".toRegex().find(configurationName)?.groupValues?.getOrNull(1)
        return listOfNotNull(bracketed ?: configurationName)
    }

    private fun resolveGradleTask(rootDir: Path, taskName: String): RunTarget? {
        val segments = taskName.split(':').filter { it.isNotBlank() }
        if (segments.isEmpty() || segments.last() !in supportedGradleTasks) {
            return null
        }

        val moduleDir = segments.dropLast(1).fold(rootDir) { current, segment ->
            current.resolve(segment)
        }

        val candidate = pickPreferredTarget(moduleDir) ?: return null
        val virtualFile = LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(candidate.pathString)
            ?: return null

        return RunTarget(
            virtualFile = virtualFile,
            presentablePath = rootDir.relativize(candidate).pathString,
        )
    }

    private fun pickPreferredTarget(moduleDir: Path): Path? {
        val candidates = listOf(
            moduleDir.resolve("src/main/kotlin"),
            moduleDir.resolve("src/main/java"),
            moduleDir.resolve("src"),
            moduleDir,
        )

        return candidates.firstOrNull(Files::exists)
    }
}

package site.addzero.gradle.buddy.intentions.projectdep

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.gradle.buddy.i18n.GradleBuddyActionI18n
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

class PublishModulesWithDependenciesAction : AnAction() {

    init {
        syncPresentation()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = selectedFiles(e)
        if (selectedFiles.isEmpty()) {
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            GradleBuddyBundle.message("action.publish.modules.with.dependencies.scan"),
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = GradleBuddyBundle.message("action.publish.modules.with.dependencies.scan")

                val result = ProjectModulesPublishSupport.buildPlan(project, selectedFiles)
                ApplicationManager.getApplication().invokeLater {
                    ProjectModulesPublishSupport.handlePlanResult(project, result)
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        syncPresentation(e.presentation)
        val project = e.project
        val files = selectedFiles(e)
        e.presentation.isEnabledAndVisible = project != null &&
            files.isNotEmpty() &&
            ProjectModulesPublishSupport.isAvailable(project, files)
    }

    private fun syncPresentation(presentation: com.intellij.openapi.actionSystem.Presentation? = null) {
        GradleBuddyActionI18n.sync(
            this,
            presentation,
            "action.publish.modules.with.dependencies.title",
            "action.publish.modules.with.dependencies.description"
        )
    }

    private fun selectedFiles(e: AnActionEvent): Array<VirtualFile> {
        return e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
            ?: e.getData(CommonDataKeys.VIRTUAL_FILE)?.let { file -> arrayOf(file) }
            ?: emptyArray()
    }
}

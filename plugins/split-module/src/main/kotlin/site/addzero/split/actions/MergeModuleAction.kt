package site.addzero.split.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.split.services.ModuleMerger
import site.addzero.split.ui.MergeModuleDialog

/**
 * Merge Module 右键菜单操作
 */
class MergeModuleAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedModules = resolveSelectedModuleRoots(project, e)
        if (selectedModules.size < 2) {
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Preparing module merge",
            true,
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = "Inferring common base package..."
                val defaultBasePackage = ModuleMerger.inferCommonBasePackage(selectedModules, indicator)

                ApplicationManager.getApplication().invokeLater({
                    if (!project.isDisposed) {
                        showMergeDialog(project, selectedModules, defaultBasePackage)
                    }
                })
            }
        })
    }

    private fun showMergeDialog(
        project: Project,
        selectedModules: List<VirtualFile>,
        defaultBasePackage: String,
    ) {
        val dialog = MergeModuleDialog(project, selectedModules, defaultBasePackage)
        if (!dialog.showAndGet()) {
            return
        }

        val targetModule = selectedModules.firstOrNull { it.path == dialog.getTargetModulePath() } ?: return
        val sourceModules = selectedModules.filterNot { it.path == targetModule.path }
        ModuleMerger(project).mergeAsync(
            targetModule = targetModule,
            sourceModules = sourceModules,
            basePackage = dialog.getBasePackage(),
        )
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val selectedModules = if (project == null) {
            emptyList()
        } else {
            resolveSelectedModuleRoots(project, e)
        }

        e.presentation.isEnabledAndVisible = selectedModules.size >= 2
    }

    private fun resolveSelectedModuleRoots(project: Project, e: AnActionEvent): List<VirtualFile> {
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY).orEmpty()
        if (selectedFiles.isEmpty()) {
            return emptyList()
        }

        return selectedFiles
            .mapNotNull { selectedFile -> selectedFile.asModuleRoot(project) }
            .distinctBy { it.path }
            .takeIf { it.size == selectedFiles.size }
            ?: emptyList()
    }

    private fun VirtualFile.asModuleRoot(project: Project): VirtualFile? {
        val candidate = when {
            isDirectory -> this
            name == "build.gradle.kts" || name == "build.gradle" || name == "pom.xml" -> parent
            else -> null
        } ?: return null

        if (!candidate.path.startsWith(project.basePath.orEmpty())) {
            return null
        }

        return candidate.takeIf { moduleRoot ->
            moduleRoot.findChild("build.gradle.kts") != null ||
                moduleRoot.findChild("build.gradle") != null ||
                moduleRoot.findChild("pom.xml") != null
        }
    }
}

package site.addzero.smart.intentions.kotlin.entityfieldmerge

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import org.jetbrains.kotlin.psi.KtClass

class MergeEntityFieldsAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val classes = EntityFieldMergeActionSupport.selectedKotlinClasses(project, event)
        val targetClass = chooseTargetClass(project, classes) ?: run {
            Messages.showWarningDialog(
                project,
                "请至少选中两个 Kotlin 实体类文件；或先记录源实体，再在目标类里执行合并。",
                "ide-kit 实体字段合并",
            )
            return
        }
        val sourceClasses = classes.filterNot { klass -> klass == targetClass }
        val targetModel = EntityFieldCollector.collect(targetClass) ?: run {
            Messages.showWarningDialog(project, "目标类没有可识别字段。", "ide-kit 实体字段合并")
            return
        }
        val sourceModels = sourceClasses.mapNotNull(EntityFieldCollector::collect)
        if (sourceModels.isEmpty()) {
            Messages.showWarningDialog(project, "源类没有可识别字段。", "ide-kit 实体字段合并")
            return
        }

        val plan = EntityFieldMergePlanner.createPlan(targetModel, sourceModels) ?: return
        val dialog = EntityFieldMergeDialog(project, plan)
        if (!dialog.showAndGet()) {
            return
        }

        val selection = dialog.selection()
        EntityFieldMergeApplier.apply(project, plan, selection.selectedRows)
        EntityFieldMergeSelectionMemory.clear()
        if (selection.deleteSourceFiles) {
            EntityFieldMergeDeletePrompt.promptDeleteSources(project, plan)
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        if (project == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }
        val selectedClasses = EntityFieldMergeActionSupport.selectedKotlinClasses(project, event)
        val rememberedSources = EntityFieldMergeSelectionMemory.snapshot()
        event.presentation.isEnabledAndVisible = selectedClasses.size >= 2 || rememberedSources.isNotEmpty()
    }

    private fun chooseTargetClass(
        project: com.intellij.openapi.project.Project,
        classes: List<KtClass>,
    ): KtClass? {
        if (classes.size < 2) {
            return null
        }
        val defaultTarget = classes.lastOrNull()
        val dialog = EntityFieldTargetSelectionDialog(project, classes, defaultTarget)
        if (!dialog.showAndGet()) {
            return null
        }
        return dialog.selectedTarget()
    }
}

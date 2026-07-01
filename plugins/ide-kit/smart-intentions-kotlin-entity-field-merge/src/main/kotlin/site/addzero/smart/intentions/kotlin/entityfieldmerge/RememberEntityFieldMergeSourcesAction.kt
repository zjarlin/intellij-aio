package site.addzero.smart.intentions.kotlin.entityfieldmerge

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class RememberEntityFieldMergeSourcesAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(event: AnActionEvent) {
        val files = EntityFieldMergeActionSupport.selectedKotlinClassFiles(event)
        EntityFieldMergeActionSupport.rememberSelectedSourceFiles(event)
        Messages.showInfoMessage(
            event.project,
            "已记录 ${files.size} 个源实体文件。请打开目标实体文件，再执行“合并已记录实体字段到当前类”。",
            "ide-kit 实体字段合并",
        )
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = EntityFieldMergeActionSupport.selectedKotlinClassFiles(event).isNotEmpty()
    }
}

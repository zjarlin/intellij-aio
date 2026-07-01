package site.addzero.smart.intentions.kotlin.entityfieldmerge

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

internal object EntityFieldMergeDeletePrompt {
    fun promptDeleteSources(
        project: Project,
        plan: EntityFieldMergePlan,
    ) {
        val sourceFiles = plan.sources
            .mapNotNull { source -> source.klass.containingKtFile.virtualFile }
            .distinctBy { file -> file.path }
            .filter { file -> file.isValid }

        if (sourceFiles.isEmpty()) {
            return
        }

        val message = buildString {
            appendLine("字段合并完成。是否删除以下源文件？")
            appendLine()
            sourceFiles.forEach { file -> appendLine(file.path) }
        }
        val result = Messages.showYesNoDialog(
            project,
            message,
            "ide-kit 实体字段合并",
            "删除源文件",
            "保留源文件",
            Messages.getQuestionIcon(),
        )
        if (result != Messages.YES) {
            return
        }

        WriteCommandAction.writeCommandAction(project)
            .withName("(ide-kit) 删除已合并源实体")
            .run<RuntimeException> {
                sourceFiles.forEach { file -> deleteFile(project, file) }
            }
    }

    private fun deleteFile(
        project: Project,
        file: VirtualFile,
    ) {
        val psiFile = PsiManager.getInstance(project).findFile(file)
        if (psiFile != null) {
            psiFile.delete()
            return
        }
        file.delete(this)
    }
}

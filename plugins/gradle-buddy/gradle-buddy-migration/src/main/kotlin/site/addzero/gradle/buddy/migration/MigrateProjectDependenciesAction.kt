package site.addzero.gradle.buddy.migration

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * 一键迁移 project 依赖到 Maven 依赖
 * 
 * 功能：
 * 1. 扫描所有 Gradle 文件中的 project(":xxx") 依赖
 * 2. 使用模块名在 Maven Central 搜索对应依赖
 * 3. 显示替换清单对话框让用户选择
 * 4. 执行替换
 */
class MigrateProjectDependenciesAction : AnAction(
    " Migrate Projects Dependencies then Replacewith Mavencentral Dependencies",
    "Scan project() dependencies and replace them with Maven artifacts",
    null
) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Scanning Project Dependencies",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = "Scanning Gradle files..."
                indicator.fraction = 0.1

                // 1. 扫描 project 依赖
                val dependencies = ProjectDependencyScanner.scan(project)
                
                if (dependencies.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            "No project() dependencies found in Gradle files.",
                            "No Dependencies Found"
                        )
                    }
                    return
                }

                indicator.text = "Searching Maven Central for replacements..."
                indicator.fraction = 0.3

                // 2. 搜索 Maven 替换
                val candidates = MavenReplacementFinder.findReplacements(dependencies, indicator)
                
                if (candidates.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            "Found ${dependencies.size} project dependencies, but no matching Maven artifacts were found.",
                            "No Replacements Found"
                        )
                    }
                    return
                }

                indicator.fraction = 1.0

                // 3. 显示对话框让用户选择
                ApplicationManager.getApplication().invokeLater {
                    showMigrationDialog(project, candidates)
                }
            }
        })
    }

    private fun showMigrationDialog(project: Project, candidates: List<ReplacementCandidate>) {
        val dialog = MigrationDialog(project, candidates)
        
        if (dialog.showAndGet()) {
            val selectedReplacements = dialog.getSelectedReplacements()
            
            if (selectedReplacements.isEmpty()) {
                Messages.showInfoMessage(
                    project,
                    "No replacements selected.",
                    "Migration Cancelled"
                )
                return
            }

            // 4. 执行替换
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                project,
                "Replacing Dependencies",
                false
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "Replacing dependencies..."

                    val result = DependencyReplacer.replace(project, selectedReplacements)

                    ApplicationManager.getApplication().invokeLater {
                        showResult(project, result)
                    }
                }
            })
        }
    }

    private fun showResult(project: Project, result: ReplaceResult) {
        val message = buildString {
            appendLine("Migration completed!")
            appendLine()
            appendLine("📊 Summary:")
            appendLine("  • Dependencies replaced: ${result.totalReplaced}")
            appendLine("  • Files modified: ${result.modifiedFiles}")
            
            if (result.errors.isNotEmpty()) {
                appendLine()
                appendLine("⚠️ Errors:")
                result.errors.take(5).forEach { appendLine("  • $it") }
                if (result.errors.size > 5) {
                    appendLine("  • ... and ${result.errors.size - 5} more")
                }
            }
        }

        Messages.showInfoMessage(project, message, "Migration Complete")
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

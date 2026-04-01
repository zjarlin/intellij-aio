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
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import site.addzero.gradle.buddy.i18n.GradleBuddyActionI18n

/**
 * 一键迁移 project 依赖到 Maven 依赖
 * 
 * 功能：
 * 1. 扫描所有 Gradle 文件中的 project(":xxx") 依赖
 * 2. 使用模块名在 Maven Central 搜索对应依赖
 * 3. 显示替换清单对话框让用户选择
 * 4. 执行替换
 */
class MigrateProjectDependenciesAction : AnAction() {

    init {
        syncPresentation()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            GradleBuddyBundle.message("action.migrate.project.dependencies.task.scan"),
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = GradleBuddyBundle.message("action.migrate.project.dependencies.task.scan")
                indicator.fraction = 0.1

                // 1. 扫描 project 依赖
                val dependencies = ProjectDependencyScanner.scan(project)
                
                if (dependencies.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            GradleBuddyBundle.message("action.migrate.project.dependencies.none.content"),
                            GradleBuddyBundle.message("action.migrate.project.dependencies.none.title")
                        )
                    }
                    return
                }

                indicator.text = GradleBuddyBundle.message("action.migrate.project.dependencies.task.search")
                indicator.fraction = 0.3

                // 2. 搜索 Maven 替换
                val lookup = MavenReplacementFinder.findReplacements(dependencies, indicator)
                
                if (lookup.replacements.isEmpty() && lookup.publishCommandCandidates.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            GradleBuddyBundle.message(
                                "action.migrate.project.dependencies.no.matches.content",
                                dependencies.size
                            ),
                            GradleBuddyBundle.message("action.migrate.project.dependencies.no.matches.title")
                        )
                    }
                    return
                }

                indicator.fraction = 1.0

                // 3. 显示对话框让用户选择
                ApplicationManager.getApplication().invokeLater {
                    showMigrationDialog(project, lookup)
                }
            }
        })
    }

    private fun showMigrationDialog(project: Project, lookup: MigrationLookupResult) {
        val dialog = MigrationDialog(project, lookup.replacements, lookup.publishCommandCandidates)

        // 没有可替换项时，对话框只承担“发布命令队列”管理职责，不再触发替换流程。
        if (lookup.replacements.isEmpty()) {
            dialog.show()
            return
        }

        if (!dialog.showAndGet()) {
            return
        }

        val selectedReplacements = dialog.getSelectedReplacements()
        if (selectedReplacements.isEmpty()) {
            Messages.showInfoMessage(
                project,
                GradleBuddyBundle.message("action.migrate.project.dependencies.none.selected.content"),
                GradleBuddyBundle.message("action.migrate.project.dependencies.none.selected.title")
            )
            return
        }

        // 4. 执行替换
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            GradleBuddyBundle.message("action.migrate.project.dependencies.task.replace"),
            false
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = GradleBuddyBundle.message("action.migrate.project.dependencies.task.replace")

                val result = DependencyReplacer.replace(project, selectedReplacements)

                ApplicationManager.getApplication().invokeLater {
                    showResult(project, result)
                }
            }
        })
    }

    private fun showResult(project: Project, result: ReplaceResult) {
        val message = buildString {
            appendLine(GradleBuddyBundle.message("action.migrate.project.dependencies.result.header"))
            appendLine()
            appendLine(GradleBuddyBundle.message("action.migrate.project.dependencies.result.summary"))
            appendLine(GradleBuddyBundle.message("action.migrate.project.dependencies.result.replaced", result.totalReplaced))
            appendLine(GradleBuddyBundle.message("action.migrate.project.dependencies.result.files", result.modifiedFiles))
            
            if (result.errors.isNotEmpty()) {
                appendLine()
                appendLine(GradleBuddyBundle.message("action.migrate.project.dependencies.result.errors"))
                result.errors.take(5).forEach {
                    appendLine(GradleBuddyBundle.message("action.migrate.project.dependencies.result.error.item", it))
                }
                if (result.errors.size > 5) {
                    appendLine(
                        GradleBuddyBundle.message(
                            "action.migrate.project.dependencies.result.more.errors",
                            result.errors.size - 5
                        )
                    )
                }
            }
        }

        Messages.showInfoMessage(
            project,
            message,
            GradleBuddyBundle.message("action.migrate.project.dependencies.result.title")
        )
    }

    override fun update(e: AnActionEvent) {
        syncPresentation(e.presentation)
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private fun syncPresentation(presentation: com.intellij.openapi.actionSystem.Presentation? = null) {
        GradleBuddyActionI18n.sync(
            this,
            presentation,
            "action.migrate.project.dependencies.title",
            "action.migrate.project.dependencies.description"
        )
    }
}

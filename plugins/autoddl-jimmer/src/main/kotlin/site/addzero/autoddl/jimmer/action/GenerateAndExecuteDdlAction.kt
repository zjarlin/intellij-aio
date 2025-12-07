package site.addzero.autoddl.jimmer.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import site.addzero.autoddl.jimmer.service.DdlResult
import site.addzero.autoddl.jimmer.service.DeltaDdlGenerator
import site.addzero.autoddl.jimmer.service.SqlExecutionService
import site.addzero.util.db.DatabaseType

/**
 * 生成并执行 DDL Action
 */
class GenerateAndExecuteDdlAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "生成并执行 Jimmer DDL", true) {
            var ddlResult: DdlResult? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.text = "扫描 Jimmer 实体类..."
                indicator.fraction = 0.1

                val generator = DeltaDdlGenerator(project)
                val entities = generator.scanJimmerEntities()

                if (entities.isEmpty()) {
                    showNotification(project, "未找到 Jimmer 实体类", NotificationType.WARNING)
                    return
                }

                indicator.text = "生成差量 DDL..."
                indicator.fraction = 0.4

                val databaseType = DatabaseType.MYSQL
                ddlResult = generator.generateDeltaDdl(entities, databaseType)

                indicator.text = "执行 SQL..."
                indicator.fraction = 0.7

                val executionService = SqlExecutionService(project)
                val executionResult = executionService.executeSqlFile(ddlResult!!.ddlFile)

                indicator.text = "完成"
                indicator.fraction = 1.0

                if (executionResult.success) {
                    showNotification(
                        project,
                        "成功执行 ${executionResult.successCount} 条 SQL\n" +
                                "文件：${ddlResult!!.ddlFile.absolutePath}",
                        NotificationType.INFORMATION
                    )
                } else {
                    showNotification(
                        project,
                        "SQL 执行失败：${executionResult.message}\n" +
                                "失败 ${executionResult.failedCount} 条",
                        NotificationType.ERROR
                    )
                }
            }

            override fun onThrowable(error: Throwable) {
                showNotification(
                    project,
                    "操作失败：${error.message}",
                    NotificationType.ERROR
                )
            }
        })
    }

    private fun showNotification(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("AutoDDL.Jimmer")
            .createNotification(content, type)
            .notify(project)
    }
}

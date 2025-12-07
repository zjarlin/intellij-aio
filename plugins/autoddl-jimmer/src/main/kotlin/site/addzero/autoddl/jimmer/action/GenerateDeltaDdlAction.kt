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
import site.addzero.autoddl.jimmer.settings.JimmerDdlSettings
import site.addzero.autoddl.jimmer.toolwindow.getLogPanel
import site.addzero.util.db.DatabaseType

/**
 * 生成差量 DDL Action
 */
class GenerateDeltaDdlAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = JimmerDdlSettings.getInstance(project)

        // 确保工具窗口已打开并获取日志面板
        val toolWindowManager = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("Jimmer DDL")

        // 激活工具窗口
        toolWindow?.activate(null)

        val logPanel = getLogPanel(project)

        if (logPanel == null) {
            showNotification(project, "工具窗口未初始化，请重新打开工具窗口", NotificationType.WARNING)
            return
        }

        // 后台任务生成 DDL
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "生成 Jimmer DDL", true) {
            var result: DdlResult? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.text = "扫描 Jimmer 实体类..."
                indicator.fraction = 0.2

                logPanel.logInfo("开始扫描项目中的 Jimmer 实体类...")
                logPanel.logInfo("扫描包路径：${settings.scanPackages}")

                val generator = DeltaDdlGenerator(project, logPanel)
                val entities = generator.scanJimmerEntities()

                if (entities.isEmpty()) {
                    logPanel.logError("未找到 Jimmer 实体类",
                        "请检查：\n1. 项目中是否有带 @Entity 注解的类\n" +
                        "2. 是否正确配置了扫描包路径\n" +
                        "3. 依赖中是否包含 jimmer-sql")
                    showNotification(project, "未找到 Jimmer 实体类，请查看日志窗口", NotificationType.WARNING)
                    return
                }

                // 记录找到的实体
                entities.forEach {
                    logPanel.logInfo("  - ${it.qualifiedName}")
                }

                // 记录开始生成
                logPanel.logGenerationStart(entities.size)

                indicator.text = "生成差量 DDL... (${entities.size} 个实体)"
                indicator.fraction = 0.6

                val databaseType = DatabaseType.MYSQL
                result = generator.generateDeltaDdl(entities, databaseType)

                indicator.text = "保存 DDL 文件..."
                indicator.fraction = 0.9
            }

            override fun onSuccess() {
                val ddlResult = result ?: return

                // 记录生成完成
                val statementCount = ddlResult.ddlContent.split(";").count { it.trim().isNotEmpty() }
                logPanel?.logGenerationComplete(ddlResult.ddlFile.absolutePath, statementCount)

                // 显示成功通知
                showNotification(
                    project,
                    "DDL 生成成功：${ddlResult.entityCount} 个实体\n" +
                            "文件：${ddlResult.ddlFile.absolutePath}",
                    NotificationType.INFORMATION
                )

                // 如果配置了自动执行
                if (settings.autoExecute) {
                    if (settings.confirmBeforeExecute) {
                        // TODO: 显示确认对话框
                        executeSql(project, ddlResult, logPanel)
                    } else {
                        executeSql(project, ddlResult, logPanel)
                    }
                }
            }

            override fun onThrowable(error: Throwable) {
                logPanel?.logError("生成 DDL 失败", error.message)
                showNotification(
                    project,
                    "生成 DDL 失败：${error.message}",
                    NotificationType.ERROR
                )
            }
        })
    }

    private fun executeSql(project: Project, result: DdlResult, logPanel: site.addzero.autoddl.jimmer.toolwindow.DdlLogPanel?) {
        val executionService = SqlExecutionService(project)
        val executionResult = executionService.executeSqlFile(result.ddlFile)

        // 记录批量执行结果
        val totalCount = executionResult.successCount + executionResult.failedCount
        logPanel?.logBatchExecution(totalCount, executionResult.successCount, executionResult.failedCount)

        if (executionResult.success) {
            showNotification(
                project,
                "SQL 执行成功：${executionResult.message}",
                NotificationType.INFORMATION
            )
        } else {
            showNotification(
                project,
                "SQL 执行失败：${executionResult.message}",
                NotificationType.ERROR
            )
        }
    }

    private fun showNotification(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("AutoDDL.Jimmer")
            .createNotification(content, type)
            .notify(project)
    }
}

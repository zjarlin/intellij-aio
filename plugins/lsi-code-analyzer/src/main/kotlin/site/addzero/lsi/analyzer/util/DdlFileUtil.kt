package site.addzero.lsi.analyzer.util

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.lsi.analyzer.config.DdlSettings
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * DDL 文件操作工具类
 */
object DdlFileUtil {

    private val NOTIFICATION_GROUP = "LSI Code Analyzer Notifications"

    /**
     * 保存 DDL 到文件
     *
     * @param project 项目实例
     * @param entityName 实体名称（用于创建目录）
     * @param tableName 表名
     * @param dialect 数据库方言
     * @param ddlContent DDL 内容
     * @return 保存的文件路径，如果保存失败则返回 null
     */
    fun saveDdlToFile(project: Project, entityName: String, tableName: String, dialect: String, ddlContent: String): Path? {
        val settings = DdlSettings.getInstance()
        val projectBasePath = project.basePath ?: return null

        // 使用动态路径（支持变量替换）
        val saveDirectory = settings.getDynamicSaveDirectory(entityName, projectBasePath)

        try {
            // 确保目录存在
            if (!Files.exists(saveDirectory)) {
                Files.createDirectories(saveDirectory)
            }

            // 生成文件名
            val fileName = settings.generateFileName(tableName, dialect)
            val filePath = saveDirectory.resolve(fileName)

            // 写入文件
            Files.write(
                filePath,
                ddlContent.toByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )

            // 刷新文件系统
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(filePath.toFile())

            // 显示成功通知
            showNotification(project, "DDL 已保存到: ${filePath.toAbsolutePath()}", NotificationType.INFORMATION)

            // 如果配置了自动打开，则打开文件
            if (settings.openFileAfterGeneration) {
                openFileInEditor(project, filePath)
            }

            return filePath
        } catch (e: IOException) {
            showNotification(project, "保存 DDL 文件失败: ${e.message}", NotificationType.ERROR)
            return null
        }
    }

    /**
     * 保存多个表的 DDL 到单个文件
     *
     * @param project 项目实例
     * @param entityName 实体名称（用于创建目录）
     * @param dialect 数据库方言
     * @param ddlContents 表名到 DDL 内容的映射
     * @return 保存的文件路径，如果保存失败则返回 null
     */
    fun saveAllDdlToFile(project: Project, entityName: String, dialect: String, ddlContents: Map<String, String>): Path? {
        val settings = DdlSettings.getInstance()
        val projectBasePath = project.basePath ?: return null

        // 使用动态路径（支持变量替换）
        val saveDirectory = settings.getDynamicSaveDirectory(entityName, projectBasePath)

        try {
            // 确保目录存在
            if (!Files.exists(saveDirectory)) {
                Files.createDirectories(saveDirectory)
            }

            // 生成文件名（多个表使用 Flyway 命名规范）
            val fileName = "V${generateVersionNumber()}__Schema_${dialect.lowercase()}.sql"
            val filePath = saveDirectory.resolve(fileName)

            // 合并所有 DDL
            val allDdl = ddlContents.entries.joinToString("\n\n") { (table, ddl) ->
                "-- Table: $table\n$ddl"
            }

            // 写入文件
            Files.write(
                filePath,
                allDdl.toByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )

            // 刷新文件系统
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(filePath.toFile())

            // 显示成功通知
            showNotification(project, "DDL 已保存到: ${filePath.toAbsolutePath()}", NotificationType.INFORMATION)

            // 如果配置了自动打开，则打开文件
            if (settings.openFileAfterGeneration) {
                openFileInEditor(project, filePath)
            }

            return filePath
        } catch (e: IOException) {
            showNotification(project, "保存 DDL 文件失败: ${e.message}", NotificationType.ERROR)
            return null
        }
    }

    /**
     * 在编辑器中打开文件
     */
    private fun openFileInEditor(project: Project, filePath: Path) {
        val virtualFile: VirtualFile? = LocalFileSystem.getInstance().findFileByIoFile(filePath.toFile())
        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        }
    }

    /**
     * 显示通知
     */
    private fun showNotification(project: Project, message: String, type: NotificationType) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(message, type)
        notification.notify(project)
    }

    
    /**
     * 生成版本号（基于时间戳的格式化版本）
     * 格式: YYYYMMDDHHMM
     */
    private fun generateVersionNumber(): String {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        return now.format(formatter)
    }
}
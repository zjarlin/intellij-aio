package site.addzero.lsi.analyzer.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileManager
import site.addzero.lsi.analyzer.config.DdlSettings
import site.addzero.util.lsi_impl.impl.intellij.virtualfile.toAllLsiClassesUnified
import java.awt.EventQueue
import javax.swing.*

/**
 * 文件变化检测服务
 * 监听 POJO 文件变化并提示生成差量 DDL
 */
@Service(Service.Level.PROJECT)
class FileChangeDetectionService(private val project: Project) {

    private var listener: VirtualFileListener? = null
    private val jdbcDetector = JdbcConnectionDetectorService()
    private val databaseSchemaService = DatabaseSchemaService(project)

    fun startDetection() {
        if (listener != null) return

        val settings = DdlSettings.getInstance()
        if (!settings.enableFileChangeDetection) return

        listener = object : VirtualFileListener {
            override fun contentsChanged(event: VirtualFileEvent) {
                handleFileChange(event.file)
            }

            override fun fileCreated(event: VirtualFileEvent) {
                handleFileChange(event.file)
            }
        }

        VirtualFileManager.getInstance().addVirtualFileListener(listener!!)
    }

    fun stopDetection() {
        listener?.let {
            VirtualFileManager.getInstance().removeVirtualFileListener(it)
            listener = null
        }
    }

    private fun handleFileChange(file: VirtualFile) {
        // 只处理 Java 和 Kotlin 文件
        val ext = file.extension?.lowercase()
        if (ext != "java" && ext != "kt") return

        // 检查是否包含 POJO
        val lsiClasses = file.toAllLsiClassesUnified()
        if (lsiClasses.none { it.isPojo }) return

        // 延迟执行，避免频繁触发
        SwingUtilities.invokeLater {
            Timer(1000) {
                checkAndNotify(file, lsiClasses.filter { it.isPojo })
            }.isRepeats = false
        }
    }

    private fun checkAndNotify(file: VirtualFile, pojos: List<site.addzero.util.lsi.clazz.LsiClass>) {
        EventQueue.invokeLater {
            // 检查数据库连接
            val connInfo = jdbcDetector.detectConnectionInfo(project)
            if (connInfo.url.isEmpty()) {
                return@invokeLater
            }

            // 生成差量 DDL
            try {
                val comparison = databaseSchemaService.compareWithDatabase(pojos, site.addzero.util.db.DatabaseType.MYSQL)

                if (hasChanges(comparison)) {
                    showNotification(file, pojos)
                }
            } catch (e: Exception) {
                // 忽略错误，可能是数据库连接问题
            }
        }
    }

    private fun hasChanges(comparison: SchemaComparison): Boolean {
        return comparison.newTables.isNotEmpty() ||
               comparison.modifiedTables.isNotEmpty() ||
               comparison.droppedTables.isNotEmpty()
    }

    private fun showNotification(file: VirtualFile, pojos: List<site.addzero.util.lsi.clazz.LsiClass>) {
        val optionPane = JOptionPane().apply {
            message = arrayOf(
                "检测到 POJO 文件发生变化：",
                "${file.path}",
                "",
                "是否生成差量 DDL 并应用到数据库？"
            )
            messageType = JOptionPane.QUESTION_MESSAGE
            optionType = JOptionPane.YES_NO_CANCEL_OPTION
            options = arrayOf("生成并应用", "仅生成", "取消")
        }

        val dialog = JDialog()
        dialog.title = "POJO 变化检测"
        dialog.isModal = true
        dialog.contentPane = optionPane
        dialog.pack()
        dialog.setLocationRelativeTo(null)

        optionPane.addPropertyChangeListener { e ->
            if (e.propertyName == JOptionPane.VALUE_PROPERTY) {
                val value = e.newValue
                when (value) {
                    "生成并应用" -> {
                        // 生成并应用
                        dialog.dispose()
                        generateAndApplyDelta(pojos)
                    }
                    "仅生成" -> {
                        // 仅生成
                        dialog.dispose()
                        generateDeltaOnly(pojos)
                    }
                    else -> {
                        // 取消
                        dialog.dispose()
                    }
                }
            }
        }

        dialog.isVisible = true
    }

    private fun generateAndApplyDelta(pojos: List<site.addzero.util.lsi.clazz.LsiClass>) {
        // 这里应该调用 DDL 生成并应用
        // TODO: 实现应用到数据库的逻辑
        JOptionPane.showMessageDialog(
            null,
            "差量 DDL 生成并应用到数据库功能开发中",
            "提示",
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun generateDeltaOnly(pojos: List<site.addzero.util.lsi.clazz.LsiClass>) {
        // 生成差量 DDL 并显示
        // TODO: 实现 DDL 生成逻辑
        JOptionPane.showMessageDialog(
            null,
            "差量 DDL 生成功能开发中",
            "提示",
            JOptionPane.INFORMATION_MESSAGE
        )
    }
}
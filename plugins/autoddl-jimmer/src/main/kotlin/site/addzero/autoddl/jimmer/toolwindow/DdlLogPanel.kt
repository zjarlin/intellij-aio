package site.addzero.autoddl.jimmer.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Color
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * DDL 日志面板
 * 记录差量 SQL 执行情况
 */
class DdlLogPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val tableModel = LogTableModel()
    private val table = JBTable(tableModel)
    private val scrollPane = JBScrollPane(table)
    
    // 工具栏
    private val toolbar = JToolBar().apply {
        isFloatable = false
        add(JButton("清空日志").apply {
            addActionListener { clearLogs() }
        })
        add(JButton("导出日志").apply {
            addActionListener { exportLogs() }
        })
        addSeparator()
        add(JLabel("  总计: "))
        add(JLabel("0").apply { 
            name = "totalCount" 
        })
        add(JLabel("  成功: "))
        add(JLabel("0").apply { 
            name = "successCount"
            foreground = Color(0, 128, 0)
        })
        add(JLabel("  失败: "))
        add(JLabel("0").apply { 
            name = "failedCount"
            foreground = Color.RED
        })
    }
    
    init {
        setupTable()
        add(toolbar, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
    }
    
    private fun setupTable() {
        // 设置列宽
        table.columnModel.getColumn(0).preferredWidth = 150  // 时间
        table.columnModel.getColumn(1).preferredWidth = 80   // 类型
        table.columnModel.getColumn(2).preferredWidth = 80   // 状态
        table.columnModel.getColumn(3).preferredWidth = 500  // SQL/消息
        table.columnModel.getColumn(4).preferredWidth = 200  // 详情
        
        // 状态列着色
        table.columnModel.getColumn(2).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): java.awt.Component {
                val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                if (!isSelected && value != null) {
                    when (value.toString()) {
                        "SUCCESS" -> component.foreground = Color(0, 128, 0)
                        "FAILED" -> component.foreground = Color.RED
                        "RUNNING" -> component.foreground = Color.BLUE
                    }
                }
                return component
            }
        }
        
        table.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
    }
    
    /**
     * 记录开始生成
     */
    fun logGenerationStart(entityCount: Int) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        tableModel.addRow(arrayOf(
            timestamp,
            "GENERATE",
            "RUNNING",
            "开始生成差量DDL，共 $entityCount 个实体",
            ""
        ))
        scrollToBottom()
    }
    
    /**
     * 记录生成完成
     */
    fun logGenerationComplete(outputFile: String, statementCount: Int) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        tableModel.addRow(arrayOf(
            timestamp,
            "GENERATE",
            "SUCCESS",
            "DDL生成完成，共 $statementCount 条语句",
            outputFile
        ))
        scrollToBottom()
        updateStatistics()
    }
    
    /**
     * 记录SQL执行
     */
    fun logSqlExecution(sql: String, success: Boolean, error: String? = null) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val truncatedSql = if (sql.length > 100) sql.take(100) + "..." else sql
        tableModel.addRow(arrayOf(
            timestamp,
            "EXECUTE",
            if (success) "SUCCESS" else "FAILED",
            truncatedSql,
            error ?: "OK"
        ))
        scrollToBottom()
        updateStatistics()
    }
    
    /**
     * 记录批量执行
     */
    fun logBatchExecution(totalCount: Int, successCount: Int, failedCount: Int) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        tableModel.addRow(arrayOf(
            timestamp,
            "BATCH",
            if (failedCount == 0) "SUCCESS" else "PARTIAL",
            "批量执行完成：总计 $totalCount 条",
            "成功 $successCount, 失败 $failedCount"
        ))
        scrollToBottom()
        updateStatistics()
    }
    
    /**
     * 记录错误
     */
    fun logError(message: String, details: String? = null) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        tableModel.addRow(arrayOf(
            timestamp,
            "ERROR",
            "FAILED",
            message,
            details ?: ""
        ))
        scrollToBottom()
        updateStatistics()
    }
    
    /**
     * 记录信息
     */
    fun logInfo(message: String, details: String? = null) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        tableModel.addRow(arrayOf(
            timestamp,
            "INFO",
            "SUCCESS",
            message,
            details ?: ""
        ))
        scrollToBottom()
    }
    
    /**
     * 清空日志
     */
    private fun clearLogs() {
        tableModel.rowCount = 0
        updateStatistics()
    }
    
    /**
     * 导出日志
     */
    private fun exportLogs() {
        val fileChooser = JFileChooser()
        fileChooser.selectedFile = java.io.File("ddl-log-${System.currentTimeMillis()}.txt")
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            file.writeText(exportToText())
            JOptionPane.showMessageDialog(this, "日志已导出到：${file.absolutePath}")
        }
    }
    
    /**
     * 导出为文本
     */
    private fun exportToText(): String {
        val sb = StringBuilder()
        sb.appendLine("=== DDL 执行日志 ===")
        sb.appendLine("导出时间：${LocalDateTime.now()}")
        sb.appendLine()
        
        for (i in 0 until tableModel.rowCount) {
            sb.appendLine("[${tableModel.getValueAt(i, 0)}] " +
                    "[${tableModel.getValueAt(i, 1)}] " +
                    "[${tableModel.getValueAt(i, 2)}] " +
                    "${tableModel.getValueAt(i, 3)}")
            val details = tableModel.getValueAt(i, 4).toString()
            if (details.isNotBlank()) {
                sb.appendLine("  详情：$details")
            }
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    /**
     * 滚动到底部
     */
    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            val lastRow = table.rowCount - 1
            if (lastRow >= 0) {
                table.scrollRectToVisible(table.getCellRect(lastRow, 0, true))
            }
        }
    }
    
    /**
     * 更新统计信息
     */
    private fun updateStatistics() {
        SwingUtilities.invokeLater {
            var totalCount = 0
            var successCount = 0
            var failedCount = 0
            
            for (i in 0 until tableModel.rowCount) {
                val status = tableModel.getValueAt(i, 2).toString()
                totalCount++
                when (status) {
                    "SUCCESS" -> successCount++
                    "FAILED" -> failedCount++
                }
            }
            
            // 更新工具栏统计
            (toolbar.components.find { (it as? JLabel)?.name == "totalCount" } as? JLabel)?.text = totalCount.toString()
            (toolbar.components.find { (it as? JLabel)?.name == "successCount" } as? JLabel)?.text = successCount.toString()
            (toolbar.components.find { (it as? JLabel)?.name == "failedCount" } as? JLabel)?.text = failedCount.toString()
        }
    }
}

/**
 * 日志表格模型
 */
class LogTableModel : DefaultTableModel(
    arrayOf("时间", "类型", "状态", "SQL/消息", "详情"),
    0
) {
    override fun isCellEditable(row: Int, column: Int): Boolean = false
}

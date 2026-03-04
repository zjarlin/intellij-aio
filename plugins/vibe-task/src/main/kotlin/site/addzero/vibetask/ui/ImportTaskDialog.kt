package site.addzero.vibetask.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import site.addzero.vibetask.service.VibeTaskService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.datatransfer.DataFlavor
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 导入任务对话框
 */
class ImportTaskDialog(private val project: Project) : DialogWrapper(project) {

    private val service = VibeTaskService.getInstance(project)

    private val urlField = JTextField(40)
    private val contentArea = JTextArea(15, 50)
    private val importModeCombo = JComboBox(arrayOf("智能合并", "全部导入", "仅导入新项目"))

    private var importedCount: Int = -1

    init {
        title = "导入 Vibe Tasks"
        init()

        // 尝试从剪贴板读取
        try {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val clipboardContent = clipboard.getData(DataFlavor.stringFlavor) as? String
            if (clipboardContent != null) {
                // 检查是否是 URL
                if (clipboardContent.trim().startsWith("http")) {
                    urlField.text = clipboardContent.trim()
                } else if (clipboardContent.contains("\"tasks\"") || clipboardContent.contains("\"content\"")) {
                    contentArea.text = clipboardContent
                }
            }
        } catch (e: Exception) {
            // 忽略剪贴板错误
        }
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
            border = EmptyBorder(12, 12, 12, 12)
            background = JBColor.namedColor("Panel.background", UIUtil.getPanelBackground())

            // 说明文本
            add(JTextArea("""
                从他人分享的任务元数据导入任务。

                支持两种方式：
                1. 粘贴分享链接（自动下载）
                2. 直接粘贴 JSON 内容

                导入模式：
                • 智能合并：合并重复任务，保留最新状态
                • 全部导入：导入所有任务（可能重复）
                • 仅导入新项目：跳过已存在的任务
            """.trimIndent()).apply {
                isEditable = false
                isOpaque = false
                lineWrap = true
                wrapStyleWord = true
                font = UIUtil.getLabelFont()
                foreground = JBColor.namedColor("Label.foreground", UIUtil.getLabelForeground())
                border = JBUI.Borders.emptyBottom(12)
            }, BorderLayout.NORTH)

            // URL 输入区
            val urlPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyBottom(8)

                add(JLabel("分享链接（可选）:").apply {
                    foreground = JBColor.namedColor("Label.foreground", UIUtil.getLabelForeground())
                }, BorderLayout.NORTH)

                urlField.apply {
                    background = JBColor.namedColor("TextField.background", UIUtil.getTextFieldBackground())
                    foreground = JBColor.namedColor("TextField.foreground", UIUtil.getTextFieldForeground())
                }
                add(urlField, BorderLayout.CENTER)

                add(JButton("从链接获取").apply {
                    addActionListener { fetchFromUrl() }
                }, BorderLayout.EAST)
            }

            // JSON 内容区
            val contentPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyBottom(8)

                add(JLabel("任务数据（JSON）:").apply {
                    foreground = JBColor.namedColor("Label.foreground", UIUtil.getLabelForeground())
                }, BorderLayout.NORTH)

                contentArea.apply {
                    font = Font("Monospaced", Font.PLAIN, 12)
                    lineWrap = true
                    wrapStyleWord = true
                    background = JBColor.namedColor("TextArea.background", UIUtil.getTextFieldBackground())
                    foreground = JBColor.namedColor("TextArea.foreground", UIUtil.getTextFieldForeground())
                }

                add(JScrollPane(contentArea).apply {
                    border = BorderFactory.createLineBorder(JBColor.border())
                }, BorderLayout.CENTER)
            }

            // 导入选项
            val optionsPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                isOpaque = false

                add(JLabel("导入模式:").apply {
                    foreground = JBColor.namedColor("Label.foreground", UIUtil.getLabelForeground())
                })

                importModeCombo.apply {
                    background = JBColor.namedColor("ComboBox.background", UIUtil.getPanelBackground())
                    foreground = JBColor.namedColor("ComboBox.foreground", UIUtil.getLabelForeground())
                }
                add(importModeCombo)

                add(JButton("从剪贴板粘贴").apply {
                    addActionListener { pasteFromClipboard() }
                })
            }

            val centerPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(urlPanel, BorderLayout.NORTH)
                add(contentPanel, BorderLayout.CENTER)
            }

            add(centerPanel, BorderLayout.CENTER)
            add(optionsPanel, BorderLayout.SOUTH)
        }
    }

    override fun getPreferredSize(): Dimension {
        return Dimension(600, 500)
    }

    override fun doOKAction() {
        val content = contentArea.text.trim()
        if (content.isBlank()) {
            Messages.showErrorDialog(project, "请输入任务数据", "错误")
            return
        }

        importedCount = try {
            val merge = when (importModeCombo.selectedIndex) {
                0 -> true  // 智能合并
                1 -> false // 全部导入（替换）
                2 -> true  // 仅新项目（合并模式，但会跳过已存在的）
                else -> true
            }

            val count = service.importFromJson(content, merge)
            count
        } catch (e: Exception) {
            -1
        }

        if (importedCount >= 0) {
            super.doOKAction()
        } else {
            Messages.showErrorDialog(project, "导入失败，请检查 JSON 格式是否正确", "错误")
        }
    }

    fun getImportedCount(): Int = importedCount

    private fun fetchFromUrl() {
        val urlStr = urlField.text.trim()
        if (urlStr.isBlank()) {
            Messages.showErrorDialog(project, "请输入分享链接", "错误")
            return
        }

        // 在后台线程中获取
        SwingUtilities.invokeLater {
            try {
                val url = URI(urlStr).toURL()
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "VibeTask/1.0")
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    contentArea.text = content
                    Messages.showInfoMessage(project, "已成功从链接获取数据", "成功")
                } else {
                    Messages.showErrorDialog(project, "获取失败: HTTP $responseCode", "错误")
                }
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "获取失败: ${e.message}", "错误")
            }
        }
    }

    private fun pasteFromClipboard() {
        try {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val content = clipboard.getData(DataFlavor.stringFlavor) as? String
            if (content != null) {
                contentArea.text = content
            } else {
                Messages.showInfoMessage(project, "剪贴板中没有文本内容", "提示")
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "无法读取剪贴板: ${e.message}", "错误")
        }
    }
}
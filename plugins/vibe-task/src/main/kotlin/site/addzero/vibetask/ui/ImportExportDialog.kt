package site.addzero.vibetask.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import site.addzero.vibetask.service.VibeTaskService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 导入/导出对话框
 */
class ImportExportDialog(
    private val project: Project,
    private val mode: Mode
) : DialogWrapper(project) {

    enum class Mode { EXPORT_JSON, EXPORT_MARKDOWN, IMPORT }

    private val service = VibeTaskService.getInstance(project)
    private val contentArea = JTextArea(20, 60)
    private val filePathField = TextFieldWithBrowseButton()
    private var result: DialogResult? = null

    data class DialogResult(
        val content: String? = null,
        val filePath: String? = null
    )

    init {
        title = when (mode) {
            Mode.EXPORT_JSON -> "导出 Vibe Tasks (JSON)"
            Mode.EXPORT_MARKDOWN -> "导出 Vibe Tasks (Markdown)"
            Mode.IMPORT -> "导入 Vibe Tasks"
        }
        init()

        when (mode) {
            Mode.EXPORT_JSON -> {
                contentArea.text = service.exportToJson()
                contentArea.isEditable = false
            }
            Mode.EXPORT_MARKDOWN -> {
                contentArea.text = service.exportToMarkdown()
                contentArea.isEditable = false
            }
            Mode.IMPORT -> {
                contentArea.text = "// 在此粘贴 JSON 内容，或选择文件导入\n\n"
                contentArea.isEditable = true
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(12, 12, 12, 12)
            background = JBColor.namedColor("Panel.background", UIUtil.getPanelBackground())
        }

        // 文件选择区域（仅导入模式）
        if (mode == Mode.IMPORT) {
            val filePanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyBottom(8)

                filePathField.apply {
                    text = ""
                    addActionListener {
                        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
                            .withTitle("选择 JSON 文件")
                            .withDescription("选择要导入的 Vibe Tasks JSON 文件")
                            .withExtensionFilter("JSON 文件", "json")
                        FileChooser.chooseFile(descriptor, project, null) { file ->
                            text = file.path
                            // 自动读取文件内容
                            try {
                                val content = file.inputStream.readBytes().toString(Charsets.UTF_8)
                                contentArea.text = content
                            } catch (e: Exception) {
                                Messages.showErrorDialog(project, "读取文件失败: ${e.message}", "错误")
                            }
                        }
                    }
                }

                add(JLabel("文件:").apply {
                    foreground = JBColor.namedColor("Label.foreground", UIUtil.getLabelForeground())
                    border = JBUI.Borders.emptyRight(8)
                }, BorderLayout.WEST)
                add(filePathField, BorderLayout.CENTER)
            }
            panel.add(filePanel, BorderLayout.NORTH)
        }

        // 内容区域
        contentArea.apply {
            font = UIUtil.getLabelFont()
            lineWrap = true
            wrapStyleWord = true
            background = JBColor.namedColor("TextArea.background", UIUtil.getTextFieldBackground())
            foreground = JBColor.namedColor("TextArea.foreground", UIUtil.getTextFieldForeground())
            caretColor = JBColor.namedColor("TextArea.caretForeground", UIUtil.getTextFieldForeground())
        }

        val scrollPane = JScrollPane(contentArea).apply {
            border = BorderFactory.createLineBorder(JBColor.border())
        }

        // 按钮区域
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(8)

            when (mode) {
                Mode.EXPORT_JSON, Mode.EXPORT_MARKDOWN -> {
                    add(JButton("复制到剪贴板", AllIcons.Actions.Copy).apply {
                        addActionListener {
                            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(java.awt.datatransfer.StringSelection(contentArea.text), null)
                            Messages.showInfoMessage(project, "已复制到剪贴板", "成功")
                        }
                    })
                    add(JButton("保存到文件", AllIcons.Actions.Download).apply {
                        addActionListener {
                            saveToFile()
                        }
                    })
                }
                Mode.IMPORT -> {
                    add(JButton("从文件加载", AllIcons.Actions.Upload).apply {
                        addActionListener {
                            loadFromFile()
                        }
                    })
                }
            }
        }

        panel.add(scrollPane, BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    override fun createSouthPanel(): JComponent? {
        val panel = super.createSouthPanel() ?: JPanel(FlowLayout())

        // 导入模式添加额外选项
        if (mode == Mode.IMPORT) {
            val mergeCheckBox = JCheckBox("合并到现有任务", true).apply {
                foreground = JBColor.namedColor("CheckBox.foreground", UIUtil.getLabelForeground())
            }
            (panel as? JPanel)?.add(mergeCheckBox, 0)
        }

        return panel
    }

    override fun doOKAction() {
        if (mode == Mode.IMPORT) {
            val content = contentArea.text.trim()
            if (content.isBlank()) {
                Messages.showErrorDialog(project, "请输入或粘贴 JSON 内容", "错误")
                return
            }

            // 获取合并选项
            val merge = (contentArea.parent?.parent?.components?.find {
                it is JPanel && it.components.any { c -> c is JCheckBox }
            } as? JPanel)?.components?.find { it is JCheckBox }?.let { it as JCheckBox }?.isSelected ?: true

            val importedCount = service.importFromJson(content, merge)
            if (importedCount >= 0) {
                Messages.showInfoMessage(project, "成功导入 $importedCount 个任务", "成功")
                result = DialogResult(content = content)
                super.doOKAction()
            } else {
                Messages.showErrorDialog(project, "导入失败，请检查 JSON 格式", "错误")
            }
        } else {
            result = DialogResult(content = contentArea.text)
            super.doOKAction()
        }
    }

    fun getResult(): DialogResult? = result

    private fun saveToFile() {
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
            .withTitle("选择保存目录")
            .withDescription("选择导出文件保存的位置")

        FileChooser.chooseFile(descriptor, project, null) { dir ->
            val extension = if (mode == Mode.EXPORT_JSON) "json" else "md"
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(java.util.Date())
            val fileName = "vibe_tasks_$timestamp.$extension"
            val file = File(dir.path, fileName)

            try {
                file.writeText(contentArea.text)
                Messages.showInfoMessage(project, "已保存到:\n${file.path}", "成功")
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "保存失败: ${e.message}", "错误")
            }
        }
    }

    private fun loadFromFile() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withTitle("选择 JSON 文件")
            .withDescription("选择要导入的 Vibe Tasks JSON 文件")
            .withExtensionFilter("JSON 文件", "json")

        FileChooser.chooseFile(descriptor, project, null) { file ->
            try {
                val content = file.inputStream.readBytes().toString(Charsets.UTF_8)
                contentArea.text = content
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "读取文件失败: ${e.message}", "错误")
            }
        }
    }

    override fun getPreferredSize(): Dimension {
        return Dimension(700, 500)
    }
}

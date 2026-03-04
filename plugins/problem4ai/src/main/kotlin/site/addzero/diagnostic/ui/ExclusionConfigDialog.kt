package site.addzero.diagnostic.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import site.addzero.diagnostic.config.DiagnosticExclusionConfig
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*

/**
 * 排除规则配置对话框
 */
class ExclusionConfigDialog(
    project: Project,
    private val config: DiagnosticExclusionConfig
) : DialogWrapper(project) {

    private lateinit var useDefaultCheckbox: JBCheckBox
    private lateinit var patternsTextArea: JBTextArea
    private lateinit var newPatternField: JBTextField

    init {
        title = "Problem4AI 排除规则配置"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        // 顶部：使用默认模式选项
        useDefaultCheckbox = JBCheckBox("使用默认排除模式（build/, node_modules/, .git/ 等）", config.isUseDefaultPatterns())
        panel.add(useDefaultCheckbox, BorderLayout.NORTH)

        // 中间：自定义模式列表
        val centerPanel = JPanel(BorderLayout())
        centerPanel.border = BorderFactory.createTitledBorder("自定义排除模式（.gitignore 风格）")

        patternsTextArea = JBTextArea().apply {
            text = config.getCustomPatterns().joinToString("\n")
            lineWrap = false
            font = JBTextArea().font
        }

        val scrollPane = JBScrollPane(patternsTextArea)
        scrollPane.preferredSize = Dimension(500, 300)
        centerPanel.add(scrollPane, BorderLayout.CENTER)

        // 提示文本
        val hintLabel = JLabel("""
            <html>
            <body>
            <b>支持的通配符模式：</b><br>
            • <code>*.test.kt</code> - 排除所有 .test.kt 文件<br>
            • <code>**/test/**</code> - 排除 test 目录下的所有文件<br>
            • <code>build/</code> - 排除 build 目录<br>
            • <code>generated/**</code> - 排除 generated 目录<br>
            每行一个模式
            </body>
            </html>
        """.trimIndent())
        centerPanel.add(hintLabel, BorderLayout.SOUTH)

        panel.add(centerPanel, BorderLayout.CENTER)

        // 底部：快速添加
        val bottomPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        bottomPanel.add(JLabel("快速添加："))

        newPatternField = JBTextField(20)
        bottomPanel.add(newPatternField)

        val addButton = JButton("添加")
        addButton.addActionListener { addPattern() }
        bottomPanel.add(addButton)

        // 预设按钮
        val presetButton = JComboBox(arrayOf(
            "选择预设...",
            "测试目录 (test/, tests/)",
            "资源文件 (*.min.js, *.min.css)",
            "文档 (*.md, *.txt)",
            "日志 (*.log)"
        ))
        presetButton.addActionListener {
            when (presetButton.selectedIndex) {
                1 -> addPreset(listOf("**/test/**", "**/tests/**", "**/*Test.kt", "**/*Test.java"))
                2 -> addPreset(listOf("*.min.js", "*.min.css", "*.min.map"))
                3 -> addPreset(listOf("*.md", "*.txt", "*.rst"))
                4 -> addPreset(listOf("*.log", "**/*.log.*", "**/logs/**"))
            }
            presetButton.selectedIndex = 0
        }
        bottomPanel.add(presetButton)

        panel.add(bottomPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun addPattern() {
        val pattern = newPatternField.text.trim()
        if (pattern.isNotEmpty()) {
            val currentText = patternsTextArea.text
            patternsTextArea.text = if (currentText.isEmpty()) {
                pattern
            } else {
                "$currentText\n$pattern"
            }
            newPatternField.text = ""
        }
    }

    private fun addPreset(patterns: List<String>) {
        val currentText = patternsTextArea.text
        val currentPatterns = currentText.lines().filter { it.isNotBlank() }.toSet()
        val newPatterns = patterns.filter { it !in currentPatterns }

        patternsTextArea.text = if (currentText.isEmpty()) {
            newPatterns.joinToString("\n")
        } else {
            if (newPatterns.isNotEmpty()) {
                "$currentText\n${newPatterns.joinToString("\n")}"
            } else {
                currentText
            }
        }
    }

    override fun doOKAction() {
        // 保存配置
        config.setUseDefaultPatterns(useDefaultCheckbox.isSelected)

        val patterns = patternsTextArea.text
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        config.setCustomPatterns(patterns)

        super.doOKAction()
    }
}

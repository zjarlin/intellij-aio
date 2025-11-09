package site.addzero.addl.action

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class StructuredInputDialog(
    project: Project?,
    private val clipboardText: String
) : DialogWrapper(project) {

    private val promptField = JBTextField()
    private val contextArea = JBTextArea(clipboardText, 10, 50)

    init {
        title = "Input Structured Context and Prompt"
        promptField.text = "你是一个数据结构化提取小助手"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 5))
        panel.add(JBLabel("提示词:"), BorderLayout.NORTH)
        panel.add(promptField, BorderLayout.NORTH)
        panel.add(JBLabel("上下文:"), BorderLayout.CENTER)
        contextArea.lineWrap = true
        contextArea.wrapStyleWord = true
        panel.add(contextArea, BorderLayout.SOUTH)
        return panel
    }

    fun getPromptText(): String {
        return promptField.text
    }

    fun getContextText(): String {
        return contextArea.text
    }
}

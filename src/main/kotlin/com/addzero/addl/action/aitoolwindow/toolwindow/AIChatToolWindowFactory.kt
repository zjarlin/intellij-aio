package com.addzero.addl.action.aitoolwindow.toolwindow

import com.addzero.addl.ai.util.ai.AiUtil
import com.addzero.addl.ai.util.ai.Promt.AICODER
import com.addzero.addl.util.ShowContentUtil.showErrorMsg
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit

class AIChatToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatPanel = AIChatToolWindow(project, toolWindow)
        val content = ContentFactory.getInstance().createContent(chatPanel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class AIChatToolWindow(
    private val project: Project,
    private val toolWindow: ToolWindow
) : Disposable {
    private val panel = JPanel(BorderLayout())
    private val messageArea = JTextPane().apply {
        isEditable = false
        contentType = "text/html"
        editorKit = HTMLEditorKit()
        document = HTMLDocument()
    }
    private val inputField = JTextField()
    private val sendButton = JButton("发送")
    private val disposable = Disposer.newDisposable()
    private val loadingPanel = JBLoadingPanel(BorderLayout(), disposable).apply {
        add(JBScrollPane(messageArea), BorderLayout.CENTER)
    }

    init {
        setupUI()
        setupListeners()
        Disposer.register(toolWindow.disposable, this)
    }

    private fun setupUI() {
        panel.add(loadingPanel, BorderLayout.CENTER)

        val bottomPanel = JPanel(BorderLayout())
        bottomPanel.add(inputField, BorderLayout.CENTER)
        bottomPanel.add(sendButton, BorderLayout.EAST)
        panel.add(bottomPanel, BorderLayout.SOUTH)

        // 初始化HTML文档
        val kit = messageArea.editorKit as HTMLEditorKit
        val doc = messageArea.document as HTMLDocument
        val initialHTML = """
            <html>
                <body>
                    <div id='messages'></div>
                </body>
            </html>
        """.trimIndent()
        messageArea.text = initialHTML
    }

    private fun setupListeners() {
        sendButton.addActionListener {
            val message = inputField.text
            if (message.isNotEmpty()) {
                sendMessage(message)
                inputField.text = ""
            }
        }

        inputField.addActionListener {
            sendButton.doClick()
        }
    }

    fun sendMessage(message: String) {
        appendMessage("User", message)
        loadingPanel.startLoading()

        // 使用协程处理后台任务
        Thread {
            try {
                val response = callAI(message)
                SwingUtilities.invokeLater {
                    appendMessage("AI", response)
                    loadingPanel.stopLoading()
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    appendMessage("Error", e.message ?: "Unknown error")
                    loadingPanel.stopLoading()
                }
            }
        }.start()
    }

    private fun appendMessage(sender: String, message: String) {
        val style = when(sender) {
            "User" -> "color: blue"
            "AI" -> "color: green"
            else -> "color: red"
        }

        val formattedMessage = """
            <div style='margin: 5px;'>
                <b style='$style'>$sender:</b>
                <pre style='margin: 5px; white-space: pre-wrap;'>$message</pre>
            </div>
        """.trimIndent()

        SwingUtilities.invokeLater {
            try {
                val doc = messageArea.document as HTMLDocument
                val kit = messageArea.editorKit as HTMLEditorKit
                kit.insertHTML(doc, doc.length, formattedMessage, 0, 0, null)

                // 滚动到底部
                messageArea.caretPosition = doc.length
            } catch (e: Exception) {
                println("Error appending message: ${e.message}")
            }
        }
    }

    private fun callAI(message: String): String {
        try {
            val init = AiUtil.INIT(message, AICODER)
            val res = init.ask(message, AICODER)
            println("airesponse: $res")
            return res
        } catch (e: Exception) {
            showErrorMsg(e.message.toString())
            return "DeepSeekAI 出错啦"
        }
    }

    val component: JComponent get() = panel

    override fun dispose() {
        Disposer.dispose(disposable)
    }

    companion object {
        private var instance: AIChatToolWindow? = null

        fun getInstance(project: Project): AIChatToolWindow {
            if (instance == null) {
                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("AI Chat")
                    ?: throw IllegalStateException("AI Chat tool window not found")
                instance = AIChatToolWindow(project, toolWindow)
            }
            return instance!!
        }
    }
}
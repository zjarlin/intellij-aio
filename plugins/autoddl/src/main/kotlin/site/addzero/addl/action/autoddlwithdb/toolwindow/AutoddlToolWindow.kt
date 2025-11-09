package site.addzero.addl.action.autoddlwithdb.toolwindow

import site.addzero.addl.autoddlstarter.generator.entity.DDLContext
import site.addzero.addl.ktututil.toJson
import site.addzero.addl.util.ShowContentUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea

class AutoDDLToolWindowFactory : ToolWindowFactory {
    private lateinit var resultArea: JTextArea
    private lateinit var generateButton: JButton
    private var currentContext: List<DDLContext>? = null

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))

        // 创建扫描按钮
        val scanButton = JButton("scan entities")
        buttonPanel.add(scanButton)

        // 创建生成按钮（初始状态禁用）
        generateButton = JButton("generate ddl").apply {
            isEnabled = false
            addActionListener {
                currentContext?.let { context ->
                    genMultiCode(context, project)
                }
            }
        }
        buttonPanel.add(generateButton)

        // 创建结果显示区域
        resultArea = JTextArea().apply {
            isEditable = false
            font = Font("Monospaced", Font.PLAIN, 12)
        }

        // 添加滚动条
        val scrollPane = com.intellij.ui.components.JBScrollPane(resultArea)

        // 设置面板布局
        panel.add(buttonPanel, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)


        // 设置扫描按钮事件
        scanButton.addActionListener {
            val pkgContext = scanDdlContext(project)

            if (pkgContext.isEmpty()) {
                ShowContentUtil.showErrorMsg("未扫描到实体结构")
                return@addActionListener
            }

            // 显示JSON结果
            currentContext = pkgContext
            resultArea.text = pkgContext.toJson()
            generateButton.isEnabled = true
        }

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }


}

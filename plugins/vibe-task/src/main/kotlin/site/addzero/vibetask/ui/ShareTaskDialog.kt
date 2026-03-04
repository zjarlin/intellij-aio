package site.addzero.vibetask.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import site.addzero.vibetask.model.ShareResult
import site.addzero.vibetask.model.ShareTarget
import site.addzero.vibetask.model.VibeTask
import site.addzero.vibetask.service.TaskShareService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 分享任务对话框
 */
class ShareTaskDialog(
    private val project: Project,
    private val tasks: List<VibeTask>
) : DialogWrapper(project) {

    private val shareService = TaskShareService()

    private val shareTargetGroup = ButtonGroup()
    private val tempLinkRadio = JRadioButton("🌐 临时外链 (0x0.st) - 匿名，30天有效期")
    private val githubRadio = JRadioButton("🐙 GitHub Gist - 需要 Token")
    private val giteeRadio = JRadioButton("🦊 Gitee Gist - 需要 Token")
    private val clipboardRadio = JRadioButton("📋 仅复制到剪贴板")

    private val githubTokenField = JPasswordField(30)
    private val giteeTokenField = JPasswordField(30)

    init {
        title = "分享 Vibe Tasks (${tasks.size} 个任务)"
        init()

        // 默认选择临时外链
        tempLinkRadio.isSelected = true
        updateTokenFieldState()
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
            border = EmptyBorder(12, 12, 12, 12)
            background = JBColor.namedColor("Panel.background", UIUtil.getPanelBackground())

            // 说明文本
            val infoPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyBottom(16)

                add(JTextArea("""
                    将选中的 ${tasks.size} 个任务分享为 JSON 格式。
                    接收方可以通过「导入」功能导入这些任务。
                """.trimIndent()).apply {
                    isEditable = false
                    isOpaque = false
                    lineWrap = true
                    wrapStyleWord = true
                    font = UIUtil.getLabelFont()
                    foreground = JBColor.namedColor("Label.foreground", UIUtil.getLabelForeground())
                }, BorderLayout.CENTER)
            }

            // 分享方式选择
            val targetPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = BorderFactory.createTitledBorder("分享方式")

                val radioPanel = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    isOpaque = false

                    add(tempLinkRadio.apply {
                        isOpaque = false
                        addActionListener { updateTokenFieldState() }
                    })
                    add(Box.createVerticalStrut(4))

                    add(githubRadio.apply {
                        isOpaque = false
                        addActionListener { updateTokenFieldState() }
                    })
                    add(Box.createVerticalStrut(4))

                    add(giteeRadio.apply {
                        isOpaque = false
                        addActionListener { updateTokenFieldState() }
                    })
                    add(Box.createVerticalStrut(4))

                    add(clipboardRadio.apply {
                        isOpaque = false
                        addActionListener { updateTokenFieldState() }
                    })
                }

                shareTargetGroup.add(tempLinkRadio)
                shareTargetGroup.add(githubRadio)
                shareTargetGroup.add(giteeRadio)
                shareTargetGroup.add(clipboardRadio)

                add(radioPanel, BorderLayout.NORTH)
            }

            // Token 输入区域
            val tokenPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                border = JBUI.Borders.emptyTop(12)

                // GitHub Token
                add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                    isOpaque = false
                    add(JLabel("GitHub Token:").apply {
                        foreground = JBColor.namedColor("Label.foreground", UIUtil.getLabelForeground())
                    })
                    add(githubTokenField)
                    add(JButton("获取", AllIcons.Actions.Help).apply {
                        toolTipText = "前往 GitHub 设置页面生成 Token"
                        addActionListener {
                            openBrowser("https://github.com/settings/tokens/new?scopes=gist&description=VibeTask+Share")
                        }
                    })
                })
                add(Box.createVerticalStrut(8))

                // Gitee Token
                add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                    isOpaque = false
                    add(JLabel("Gitee Token: ").apply {
                        foreground = JBColor.namedColor("Label.foreground", UIUtil.getLabelForeground())
                    })
                    add(giteeTokenField)
                    add(JButton("获取", AllIcons.Actions.Help).apply {
                        toolTipText = "前往 Gitee 设置页面生成 Token"
                        addActionListener {
                            openBrowser("https://gitee.com/profile/personal_access_tokens/new")
                        }
                    })
                })
            }

            // 任务预览
            val previewPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyTop(16)

                val previewText = tasks.take(5).joinToString("\n") { "• ${it.content}" } +
                        if (tasks.size > 5) "\n... 还有 ${tasks.size - 5} 个任务" else ""

                add(JLabel("分享预览:").apply {
                    foreground = JBColor.namedColor("Label.foreground", UIUtil.getLabelForeground())
                }, BorderLayout.NORTH)
                add(JTextArea(previewText).apply {
                    isEditable = false
                    background = JBColor.namedColor("Panel.background", UIUtil.getPanelBackground())
                    foreground = JBColor.GRAY
                    font = font.deriveFont(12f)
                }, BorderLayout.CENTER)
            }

            add(infoPanel, BorderLayout.NORTH)
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                add(targetPanel, BorderLayout.NORTH)
                add(tokenPanel, BorderLayout.CENTER)
            }, BorderLayout.CENTER)
            add(previewPanel, BorderLayout.SOUTH)
        }
    }

    override fun getPreferredSize(): Dimension {
        return Dimension(480, 420)
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            object : DialogWrapperAction("分享") {
                override fun doAction(e: ActionEvent?) {
                    doShare()
                }
            },
            cancelAction
        )
    }

    private fun updateTokenFieldState() {
        githubTokenField.isEnabled = githubRadio.isSelected
        giteeTokenField.isEnabled = giteeRadio.isSelected
    }

    private fun doShare() {
        val target = when {
            tempLinkRadio.isSelected -> ShareTarget.TEMP_LINK
            githubRadio.isSelected -> ShareTarget.GITHUB_GIST
            giteeRadio.isSelected -> ShareTarget.GITEE_GIST
            else -> ShareTarget.CLIPBOARD
        }

        // 获取 Token
        val githubToken = String(githubTokenField.password).takeIf { it.isNotBlank() }
        val giteeToken = String(giteeTokenField.password).takeIf { it.isNotBlank() }

        // 验证 Token
        if (target == ShareTarget.GITHUB_GIST && githubToken.isNullOrBlank()) {
            Messages.showErrorDialog(project, "请输入 GitHub Token", "验证失败")
            return
        }
        if (target == ShareTarget.GITEE_GIST && giteeToken.isNullOrBlank()) {
            Messages.showErrorDialog(project, "请输入 Gitee Token", "验证失败")
            return
        }

        // 执行分享
        val result = shareService.shareTasks(tasks, target, githubToken, giteeToken)

        if (result.success) {
            // 复制结果到剪贴板
            val clipboardContent = result.url ?: buildClipboardContent()
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(clipboardContent), null)

            // 显示成功消息
            val message = buildString {
                appendLine(result.message)
                result.url?.let {
                    appendLine()
                    appendLine("链接已复制到剪贴板:")
                    appendLine(it)
                }
            }

            Messages.showInfoMessage(project, message, "分享成功")
            close(OK_EXIT_CODE)
        } else {
            Messages.showErrorDialog(project, result.message, "分享失败")
        }
    }

    private fun buildClipboardContent(): String {
        return buildString {
            appendLine("📝 Vibe Tasks (${tasks.size} 个任务)")
            appendLine()
            tasks.forEach { task ->
                val status = when (task.status) {
                    VibeTask.TaskStatus.TODO -> "⏳"
                    VibeTask.TaskStatus.IN_PROGRESS -> "▶️"
                    VibeTask.TaskStatus.DONE -> "✅"
                    VibeTask.TaskStatus.CANCELLED -> "❌"
                }
                appendLine("$status ${task.content}")
                task.assignees.takeIf { it.isNotEmpty() }?.let {
                    appendLine("   👤 ${it.joinToString(", ")}")
                }
            }
        }
    }

    private fun openBrowser(url: String) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI(url))
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "无法打开浏览器: ${e.message}", "错误")
        }
    }
}

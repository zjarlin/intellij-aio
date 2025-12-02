package site.addzero.deploy.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import site.addzero.deploy.pipeline.*
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 部署日志面板 - 显示部署会话列表和详细日志
 */
class DeployLogPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val sessionListModel = DefaultListModel<DeploySession>()
    private val sessionList = JList(sessionListModel)
    private val logTextArea = JTextArea()
    private val sessionService = DeploySessionService.getInstance(project)

    private val phaseProgressBars = mutableMapOf<DeployPhase, JProgressBar>()
    private val phaseLabels = mutableMapOf<DeployPhase, JBLabel>()
    private val statusLabel = JBLabel("No active deployment")
    private val durationLabel = JBLabel("")

    init {
        border = JBUI.Borders.empty(8)
        setupUI()
        subscribeToEvents()
        refreshSessionList()
    }

    private fun setupUI() {
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            dividerLocation = 250
            leftComponent = createSessionListPanel()
            rightComponent = createDetailPanel()
        }
        add(splitPane, BorderLayout.CENTER)
    }

    private fun createSessionListPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 0, 0, 8)

            add(JBLabel("Deploy Sessions").apply {
                font = font.deriveFont(Font.BOLD)
                border = JBUI.Borders.empty(0, 0, 8, 0)
            }, BorderLayout.NORTH)

            sessionList.apply {
                cellRenderer = SessionListCellRenderer()
                selectionMode = ListSelectionModel.SINGLE_SELECTION
                addListSelectionListener { e ->
                    if (!e.valueIsAdjusting) {
                        selectedValue?.let { showSessionDetails(it) }
                    }
                }
            }

            add(JBScrollPane(sessionList), BorderLayout.CENTER)

            val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JButton("Clear History").apply {
                    addActionListener {
                        sessionService.clearHistory()
                        refreshSessionList()
                    }
                })
                add(JButton("Refresh").apply {
                    addActionListener { refreshSessionList() }
                })
            }
            add(buttonPanel, BorderLayout.SOUTH)
        }
    }

    private fun createDetailPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            add(createProgressPanel(), BorderLayout.NORTH)
            add(createLogPanel(), BorderLayout.CENTER)
        }
    }

    private fun createProgressPanel(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(0, 0, 8, 0)

            // 状态行
            add(JPanel(BorderLayout()).apply {
                add(statusLabel, BorderLayout.WEST)
                add(durationLabel, BorderLayout.EAST)
            })

            add(Box.createVerticalStrut(8))

            // 各阶段进度条
            DeployPhase.entries.forEach { phase ->
                add(createPhaseRow(phase))
                add(Box.createVerticalStrut(4))
            }
        }
    }

    private fun createPhaseRow(phase: DeployPhase): JPanel {
        return JPanel(BorderLayout(8, 0)).apply {
            val label = JBLabel("${phase.icon} ${phase.displayName}").apply {
                preferredSize = Dimension(80, 20)
            }
            phaseLabels[phase] = label

            val progressBar = JProgressBar(0, 100).apply {
                isStringPainted = true
                string = "Pending"
            }
            phaseProgressBars[phase] = progressBar

            add(label, BorderLayout.WEST)
            add(progressBar, BorderLayout.CENTER)
        }
    }

    private fun createLogPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            add(JBLabel("Logs").apply {
                font = font.deriveFont(Font.BOLD)
                border = JBUI.Borders.empty(0, 0, 4, 0)
            }, BorderLayout.NORTH)

            logTextArea.apply {
                isEditable = false
                font = Font("Monospaced", Font.PLAIN, 12)
                background = JBColor(Color(43, 43, 43), Color(43, 43, 43))
                foreground = JBColor(Color(169, 183, 198), Color(169, 183, 198))
                border = JBUI.Borders.empty(8)
            }

            add(JBScrollPane(logTextArea), BorderLayout.CENTER)
        }
    }

    private fun subscribeToEvents() {
        val connection = project.messageBus.connect(this)
        connection.subscribe(DeploySessionListener.TOPIC, object : DeploySessionListener {
            override fun onSessionCreated(session: DeploySession) {
                SwingUtilities.invokeLater {
                    refreshSessionList()
                    sessionList.selectedIndex = 0
                }
            }

            override fun onSessionUpdated(session: DeploySession) {
                SwingUtilities.invokeLater {
                    if (sessionList.selectedValue?.id == session.id) {
                        updateProgressDisplay(session)
                    }
                    sessionList.repaint()
                }
            }

            override fun onLogAdded(sessionId: String, log: DeployLog) {
                SwingUtilities.invokeLater {
                    if (sessionList.selectedValue?.id == sessionId) {
                        appendLog(log)
                    }
                }
            }

            override fun onSessionCompleted(session: DeploySession) {
                SwingUtilities.invokeLater {
                    refreshSessionList()
                    if (sessionList.selectedValue?.id == session.id) {
                        updateProgressDisplay(session)
                    }
                }
            }
        })
    }

    private fun refreshSessionList() {
        val selectedId = sessionList.selectedValue?.id
        sessionListModel.clear()
        sessionService.getAllSessions().forEach { sessionListModel.addElement(it) }

        if (selectedId != null) {
            for (i in 0 until sessionListModel.size()) {
                if (sessionListModel.getElementAt(i).id == selectedId) {
                    sessionList.selectedIndex = i
                    break
                }
            }
        }
    }

    private fun showSessionDetails(session: DeploySession) {
        updateProgressDisplay(session)
        logTextArea.text = session.logs.joinToString("\n") { it.format() }
        logTextArea.caretPosition = logTextArea.document.length
    }

    private fun updateProgressDisplay(session: DeploySession) {
        statusLabel.text = "${session.configName} -> ${session.targetName}"
        statusLabel.foreground = when (session.status) {
            DeployStatus.RUNNING -> JBColor.BLUE
            DeployStatus.SUCCESS -> JBColor(Color(0, 128, 0), Color(0, 180, 0))
            DeployStatus.FAILED -> JBColor.RED
            DeployStatus.CANCELLED -> JBColor.ORANGE
            else -> JBColor.foreground()
        }

        durationLabel.text = session.getDurationFormatted()

        session.phases.forEach { (phase, progress) ->
            phaseProgressBars[phase]?.apply {
                value = (progress.progress * 100).toInt()
                string = when (progress.status) {
                    DeployStatus.PENDING -> "Pending"
                    DeployStatus.RUNNING -> "${(progress.progress * 100).toInt()}%"
                    DeployStatus.SUCCESS -> "Completed"
                    DeployStatus.FAILED -> "Failed"
                    DeployStatus.CANCELLED -> "Cancelled"
                }
                foreground = when (progress.status) {
                    DeployStatus.SUCCESS -> JBColor(Color(0, 128, 0), Color(0, 180, 0))
                    DeployStatus.FAILED -> JBColor.RED
                    DeployStatus.RUNNING -> JBColor.BLUE
                    else -> JBColor.GRAY
                }
            }
        }
    }

    private fun appendLog(log: DeployLog) {
        logTextArea.append(log.format() + "\n")
        logTextArea.caretPosition = logTextArea.document.length
    }

    override fun dispose() {
        // Cleanup
    }

    /**
     * 会话列表渲染器
     */
    private inner class SessionListCellRenderer : ListCellRenderer<DeploySession> {
        private val panel = JPanel(BorderLayout())
        private val nameLabel = JBLabel()
        private val statusIcon = JBLabel()
        private val timeLabel = JBLabel()

        init {
            panel.border = EmptyBorder(4, 8, 4, 8)
            panel.add(statusIcon, BorderLayout.WEST)
            panel.add(nameLabel, BorderLayout.CENTER)
            panel.add(timeLabel, BorderLayout.EAST)
        }

        override fun getListCellRendererComponent(
            list: JList<out DeploySession>,
            value: DeploySession,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            nameLabel.text = "${value.configName} -> ${value.targetName}"
            timeLabel.text = value.getDurationFormatted()

            statusIcon.text = when (value.status) {
                DeployStatus.RUNNING -> "▶ "
                DeployStatus.SUCCESS -> "✓ "
                DeployStatus.FAILED -> "✗ "
                DeployStatus.CANCELLED -> "⊘ "
                DeployStatus.PENDING -> "○ "
            }
            statusIcon.foreground = when (value.status) {
                DeployStatus.RUNNING -> JBColor.BLUE
                DeployStatus.SUCCESS -> JBColor(Color(0, 128, 0), Color(0, 180, 0))
                DeployStatus.FAILED -> JBColor.RED
                DeployStatus.CANCELLED -> JBColor.ORANGE
                else -> JBColor.GRAY
            }

            if (isSelected) {
                panel.background = list.selectionBackground
                nameLabel.foreground = list.selectionForeground
                timeLabel.foreground = list.selectionForeground
            } else {
                panel.background = list.background
                nameLabel.foreground = list.foreground
                timeLabel.foreground = JBColor.GRAY
            }

            return panel
        }
    }
}

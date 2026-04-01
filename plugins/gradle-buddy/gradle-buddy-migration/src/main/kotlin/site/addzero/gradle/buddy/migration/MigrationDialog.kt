package site.addzero.gradle.buddy.migration

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import site.addzero.network.call.maven.util.MavenArtifact
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import javax.swing.*

/**
 * 迁移选择对话框。
 *
 * 除了原有的 Maven 替换选择外，还额外承载“未发布模块 → 发布命令队列”的管理能力，
 * 方便先复制中央发布命令，再回源仓库执行。
 */
class MigrationDialog(
    private val project: Project,
    private val candidates: List<ReplacementCandidate>,
    private val publishCommandCandidates: List<PublishCommandCandidate>
) : DialogWrapper(project, true) {

    private val queueService = project.getService(PublishCommandQueueService::class.java)
    private val candidatePanels = mutableListOf<CandidatePanel>()
    private val publishCandidatePanels = mutableListOf<PublishCandidatePanel>()
    private val queuePreviewArea = JBTextArea()
    private val queueCountLabel = JLabel()
    private val statusLabel = JLabel(" ")

    init {
        title = GradleBuddyBundle.message("dialog.migrate.project.dependencies.title")
        if (candidates.isNotEmpty()) {
            setOKButtonText(GradleBuddyBundle.message("dialog.migrate.project.dependencies.button.replace"))
        } else {
            setOKButtonText(GradleBuddyBundle.message("dialog.migrate.project.dependencies.button.close"))
        }
        setCancelButtonText(GradleBuddyBundle.message("dialog.migrate.project.dependencies.button.close"))
        init()
        refreshQueuePreview()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(0, 12))
        mainPanel.preferredSize = Dimension(920, 760)
        mainPanel.border = JBUI.Borders.empty(8)

        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH)
        mainPanel.add(createContentPanel(), BorderLayout.CENTER)
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH)

        return mainPanel
    }

    private fun createHeaderPanel(): JComponent {
        return panel {
            row {
                label(
                    GradleBuddyBundle.message(
                        "dialog.migrate.project.dependencies.header.replacements",
                        candidates.size
                    )
                )
            }
            if (publishCommandCandidates.isNotEmpty()) {
                row {
                    label(
                        GradleBuddyBundle.message(
                            "dialog.migrate.project.dependencies.header.publish",
                            publishCommandCandidates.size
                        )
                    )
                }
            }
            row {
                label(GradleBuddyBundle.message("dialog.migrate.project.dependencies.header.hint"))
            }
        }
    }

    private fun createContentPanel(): JComponent {
        val content = JPanel()
        content.layout = BoxLayout(content, BoxLayout.Y_AXIS)
        content.border = JBUI.Borders.empty(4, 0)

        if (candidates.isNotEmpty()) {
            content.add(sectionTitle(GradleBuddyBundle.message("dialog.migrate.project.dependencies.section.replacements")))
            content.add(Box.createVerticalStrut(8))
            candidates.forEach { candidate ->
                val panel = CandidatePanel(candidate)
                candidatePanels += panel
                content.add(panel)
                content.add(Box.createVerticalStrut(10))
            }
        }

        if (publishCommandCandidates.isNotEmpty()) {
            if (content.componentCount > 0) {
                content.add(Box.createVerticalStrut(6))
            }
            content.add(sectionTitle(GradleBuddyBundle.message("dialog.migrate.project.dependencies.section.publish")))
            content.add(Box.createVerticalStrut(8))
            publishCommandCandidates.forEach { candidate ->
                val panel = PublishCandidatePanel(candidate)
                publishCandidatePanels += panel
                content.add(panel)
                content.add(Box.createVerticalStrut(10))
            }
        }

        if (content.componentCount == 0) {
            content.add(JLabel(GradleBuddyBundle.message("dialog.migrate.project.dependencies.empty")))
        }

        return JBScrollPane(content).apply {
            border = JBUI.Borders.empty()
        }
    }

    private fun createBottomPanel(): JComponent {
        val container = JPanel(BorderLayout(0, 8))

        val actionPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        if (candidatePanels.isNotEmpty()) {
            actionPanel.add(createReplacementButtons())
            actionPanel.add(Box.createVerticalStrut(6))
        }

        if (publishCandidatePanels.isNotEmpty()) {
            actionPanel.add(createPublishButtons())
            actionPanel.add(Box.createVerticalStrut(6))
        }

        actionPanel.add(createQueueButtons())
        container.add(actionPanel, BorderLayout.NORTH)

        val queuePanel = JPanel(BorderLayout(0, 6)).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1),
                JBUI.Borders.empty(10)
            )
        }
        queuePanel.add(queueCountLabel, BorderLayout.NORTH)

        queuePreviewArea.apply {
            isEditable = false
            lineWrap = false
            wrapStyleWord = false
            emptyText.text = GradleBuddyBundle.message("dialog.migrate.project.dependencies.queue.empty")
        }
        queuePanel.add(
            JBScrollPane(queuePreviewArea).apply {
                preferredSize = Dimension(880, 150)
            },
            BorderLayout.CENTER
        )
        container.add(queuePanel, BorderLayout.CENTER)

        statusLabel.border = JBUI.Borders.emptyLeft(4)
        container.add(statusLabel, BorderLayout.SOUTH)

        return container
    }

    private fun createReplacementButtons(): JComponent {
        return JPanel().apply {
            add(createButton("dialog.migrate.project.dependencies.button.select.all.replacements") {
                candidatePanels.forEach { it.setSelected(true) }
            })
            add(createButton("dialog.migrate.project.dependencies.button.clear.replacements") {
                candidatePanels.forEach { it.setSelected(false) }
            })
            add(createButton("dialog.migrate.project.dependencies.button.copy.maven") {
                copySelectedMavenDependencies()
            })
        }
    }

    private fun createPublishButtons(): JComponent {
        return JPanel().apply {
            add(createButton("dialog.migrate.project.dependencies.button.select.all.publish") {
                publishCandidatePanels.forEach { it.setSelected(true) }
            })
            add(createButton("dialog.migrate.project.dependencies.button.clear.publish") {
                publishCandidatePanels.forEach { it.setSelected(false) }
            })
            add(createButton("dialog.migrate.project.dependencies.button.enqueue.publish") {
                enqueueSelectedPublishCommands()
            })
        }
    }

    private fun createQueueButtons(): JComponent {
        return JPanel().apply {
            add(createButton("dialog.migrate.project.dependencies.button.copy.queue") {
                copyQueueCommands()
            })
            add(createButton("dialog.migrate.project.dependencies.button.clear.queue") {
                queueService.clear()
                refreshQueuePreview()
                updateStatus(GradleBuddyBundle.message("dialog.migrate.project.dependencies.status.queue.cleared"))
            })
        }
    }

    private fun createButton(messageKey: String, action: () -> Unit): JButton {
        return JButton(GradleBuddyBundle.message(messageKey)).apply {
            addActionListener { action() }
        }
    }

    private fun sectionTitle(text: String): JComponent {
        return JLabel(text).apply {
            border = JBUI.Borders.emptyBottom(2)
        }
    }

    private fun copySelectedMavenDependencies() {
        val selected = getSelectedReplacements()
        if (selected.isEmpty()) {
            updateStatus(GradleBuddyBundle.message("dialog.migrate.project.dependencies.status.maven.empty"))
            return
        }

        val text = selected.joinToString(separator = "\n") { candidate ->
            val artifact = candidate.selectedArtifact ?: return@joinToString ""
            val version = artifact.latestVersion.ifBlank { artifact.version }
            candidate.occurrences
                .map { occurrence -> """${occurrence.configType}("${artifact.groupId}:${artifact.artifactId}:$version")""" }
                .distinct()
                .joinToString(separator = "\n")
        }.trim()

        copyToClipboard(text)
        updateStatus(
            GradleBuddyBundle.message(
                "dialog.migrate.project.dependencies.status.maven.copied",
                text.lineSequence().count { it.isNotBlank() }
            )
        )
    }

    private fun enqueueSelectedPublishCommands() {
        val selected = publishCandidatePanels
            .filter { it.isSelected() }
            .map { it.candidate.toQueueEntry() }

        if (selected.isEmpty()) {
            updateStatus(GradleBuddyBundle.message("dialog.migrate.project.dependencies.status.publish.empty"))
            return
        }

        val added = queueService.addAll(selected)
        refreshQueuePreview()
        updateStatus(
            GradleBuddyBundle.message("dialog.migrate.project.dependencies.status.publish.enqueued", added)
        )
    }

    private fun copyQueueCommands() {
        val text = queueService.buildClipboardText()
        if (text.isBlank()) {
            updateStatus(GradleBuddyBundle.message("dialog.migrate.project.dependencies.status.queue.empty"))
            return
        }

        copyToClipboard(text)
        updateStatus(
            GradleBuddyBundle.message(
                "dialog.migrate.project.dependencies.status.queue.copied",
                queueService.size()
            )
        )
    }

    private fun refreshQueuePreview() {
        val text = queueService.buildClipboardText()
        queuePreviewArea.text = text
        queuePreviewArea.caretPosition = 0
        queueCountLabel.text = GradleBuddyBundle.message(
            "dialog.migrate.project.dependencies.queue.count",
            queueService.size()
        )
    }

    private fun updateStatus(message: String) {
        statusLabel.text = message
    }

    private fun copyToClipboard(text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }

    /**
     * 获取用户选择的替换项。
     */
    fun getSelectedReplacements(): List<ReplacementCandidate> {
        candidatePanels.forEach { panel ->
            panel.candidate.selected = panel.isSelected()
            panel.candidate.selectedArtifact = panel.getSelectedArtifact()
        }
        return candidates.filter { it.selected && it.selectedArtifact != null }
    }

    /**
     * 单个可替换候选项面板。
     */
    private inner class CandidatePanel(val candidate: ReplacementCandidate) : JPanel() {
        private val checkbox = JCheckBox().apply {
            isSelected = false
        }
        private val artifactComboBox = JComboBox<ArtifactItem>()

        init {
            layout = BorderLayout(0, 8)
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1),
                JBUI.Borders.empty(10)
            )

            val topPanel = JPanel()
            topPanel.layout = BoxLayout(topPanel, BoxLayout.Y_AXIS)

            val titleRow = JPanel(BorderLayout())
            titleRow.add(checkbox, BorderLayout.WEST)
            titleRow.add(
                JLabel("<html><b>${buildModuleDisplay(candidate.modulePath)}</b></html>"),
                BorderLayout.CENTER
            )
            topPanel.add(titleRow)
            topPanel.add(Box.createVerticalStrut(4))
            topPanel.add(
                JLabel(
                    "<html><font color='gray'>${
                        GradleBuddyBundle.message(
                            "dialog.migrate.project.dependencies.candidate.info",
                            candidate.occurrenceCount,
                            candidate.filesAffected.joinToString()
                        )
                    }</font></html>"
                )
            )

            add(topPanel, BorderLayout.NORTH)

            candidate.mavenArtifacts.forEach { artifact ->
                artifactComboBox.addItem(ArtifactItem(artifact))
            }
            if (artifactComboBox.itemCount > 0) {
                artifactComboBox.selectedIndex = 0
            }
            artifactComboBox.preferredSize = Dimension(520, artifactComboBox.preferredSize.height)

            val bottomPanel = JPanel(BorderLayout(8, 0))
            bottomPanel.add(
                JLabel(GradleBuddyBundle.message("dialog.migrate.project.dependencies.label.replace.with")),
                BorderLayout.WEST
            )
            bottomPanel.add(artifactComboBox, BorderLayout.CENTER)
            add(bottomPanel, BorderLayout.CENTER)
        }

        fun isSelected(): Boolean = checkbox.isSelected

        fun setSelected(selected: Boolean) {
            checkbox.isSelected = selected
        }

        fun getSelectedArtifact(): MavenArtifact? {
            return (artifactComboBox.selectedItem as? ArtifactItem)?.artifact
        }
    }

    /**
     * 单个待加入发布队列的候选项面板。
     */
    private inner class PublishCandidatePanel(val candidate: PublishCommandCandidate) : JPanel() {
        private val checkbox = JCheckBox().apply {
            isSelected = false
        }

        init {
            layout = BorderLayout(0, 8)
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1),
                JBUI.Borders.empty(10)
            )

            val topPanel = JPanel()
            topPanel.layout = BoxLayout(topPanel, BoxLayout.Y_AXIS)

            val titleRow = JPanel(BorderLayout())
            titleRow.add(checkbox, BorderLayout.WEST)
            titleRow.add(
                JLabel("<html><b>${buildModuleDisplay(candidate.rawModulePath)}</b></html>"),
                BorderLayout.CENTER
            )
            topPanel.add(titleRow)
            topPanel.add(Box.createVerticalStrut(4))
            topPanel.add(
                JLabel(
                    "<html><font color='gray'>${
                        GradleBuddyBundle.message(
                            "dialog.migrate.project.dependencies.publish.info",
                            candidate.occurrenceCount,
                            candidate.filesAffected.joinToString()
                        )
                    }</font></html>"
                )
            )
            topPanel.add(Box.createVerticalStrut(2))
            topPanel.add(
                JLabel(
                    "<html><font color='gray'>${
                        GradleBuddyBundle.message(
                            "dialog.migrate.project.dependencies.publish.root",
                            candidate.sourceRootPath
                        )
                    }</font></html>"
                )
            )
            add(topPanel, BorderLayout.NORTH)

            val commandField = JTextField(candidate.command).apply {
                isEditable = false
                border = JBUI.Borders.empty(4, 6)
            }

            val commandPanel = JPanel(BorderLayout(8, 0))
            commandPanel.add(
                JLabel(GradleBuddyBundle.message("dialog.migrate.project.dependencies.label.publish.command")),
                BorderLayout.WEST
            )
            commandPanel.add(commandField, BorderLayout.CENTER)
            add(commandPanel, BorderLayout.CENTER)
        }

        fun isSelected(): Boolean = checkbox.isSelected

        fun setSelected(selected: Boolean) {
            checkbox.isSelected = selected
        }
    }

    private fun buildModuleDisplay(rawModulePath: String): String {
        return if (rawModulePath.startsWith("projects.")) {
            rawModulePath
        } else {
            """project("$rawModulePath")"""
        }
    }

    /**
     * 下拉框中的 Artifact 包装。
     */
    private data class ArtifactItem(val artifact: MavenArtifact) {
        override fun toString(): String {
            return "${artifact.groupId}:${artifact.artifactId}:${artifact.latestVersion.ifBlank { artifact.version }}"
        }
    }
}

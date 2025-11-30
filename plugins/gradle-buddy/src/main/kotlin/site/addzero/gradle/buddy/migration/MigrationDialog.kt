package site.addzero.gradle.buddy.migration

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import site.addzero.network.call.maven.util.MavenArtifact
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * 迁移选择对话框
 * 显示所有可替换的 project 依赖及其 Maven 替换选项
 */
class MigrationDialog(
    private val project: Project,
    private val candidates: List<ReplacementCandidate>
) : DialogWrapper(project, true) {

    private val candidatePanels = mutableListOf<CandidatePanel>()

    init {
        title = " Migrate Projects Dependencies then Replacewith Mavencentral Dependencies"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.preferredSize = Dimension(800, 600)

        // 标题说明
        val headerPanel = panel {
            row {
                label("Found ${candidates.size} project dependencies that can be replaced with Maven artifacts.")
            }
            row {
                label("Select the replacements you want to apply:")
            }
        }
        mainPanel.add(headerPanel, BorderLayout.NORTH)

        // 候选列表
        val listPanel = JPanel()
        listPanel.layout = BoxLayout(listPanel, BoxLayout.Y_AXIS)
        listPanel.border = JBUI.Borders.empty(10)

        candidates.forEach { candidate ->
            val panel = CandidatePanel(candidate)
            candidatePanels.add(panel)
            listPanel.add(panel)
            listPanel.add(Box.createVerticalStrut(10))
        }

        val scrollPane = JBScrollPane(listPanel)
        scrollPane.border = JBUI.Borders.empty()
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        // 底部全选/取消全选按钮
        val buttonPanel = JPanel()
        val selectAllBtn = JButton("Select All").apply {
            addActionListener { setAllSelected(true) }
        }
        val deselectAllBtn = JButton("Deselect All").apply {
            addActionListener { setAllSelected(false) }
        }
        buttonPanel.add(selectAllBtn)
        buttonPanel.add(deselectAllBtn)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)

        return mainPanel
    }

    private fun setAllSelected(selected: Boolean) {
        candidatePanels.forEach { it.setSelected(selected) }
    }

    /**
     * 获取用户选择的替换项
     */
    fun getSelectedReplacements(): List<ReplacementCandidate> {
        candidatePanels.forEach { panel ->
            panel.candidate.selected = panel.isSelected()
            panel.candidate.selectedArtifact = panel.getSelectedArtifact()
        }
        return candidates.filter { it.selected && it.selectedArtifact != null }
    }

    /**
     * 单个候选项的面板
     */
    private inner class CandidatePanel(val candidate: ReplacementCandidate) : JPanel() {
        private val checkbox: JCheckBox
        private val artifactComboBox: JComboBox<ArtifactItem>

        init {
            layout = BorderLayout()
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1),
                JBUI.Borders.empty(10)
            )

            // 左侧：复选框和模块信息
            val leftPanel = JPanel()
            leftPanel.layout = BoxLayout(leftPanel, BoxLayout.Y_AXIS)

            checkbox = JCheckBox().apply {
                isSelected = false
            }

            val moduleLabel = JLabel("<html><b>project(\"${candidate.modulePath}\")</b></html>")
            val infoLabel = JLabel("<html><font color='gray'>Found in ${candidate.occurrenceCount} place(s): ${candidate.filesAffected.joinToString()}</font></html>")

            val checkboxRow = JPanel(BorderLayout())
            checkboxRow.add(checkbox, BorderLayout.WEST)
            checkboxRow.add(moduleLabel, BorderLayout.CENTER)

            leftPanel.add(checkboxRow)
            leftPanel.add(Box.createVerticalStrut(5))
            leftPanel.add(infoLabel)

            add(leftPanel, BorderLayout.NORTH)

            // 右侧：Maven 替换选项下拉框
            val rightPanel = JPanel(BorderLayout())
            rightPanel.border = JBUI.Borders.emptyTop(10)

            val comboLabel = JLabel("Replace with: ")
            artifactComboBox = JComboBox<ArtifactItem>().apply {
                candidate.mavenArtifacts.forEach { artifact ->
                    addItem(ArtifactItem(artifact))
                }
                if (itemCount > 0) {
                    selectedIndex = 0
                }
                preferredSize = Dimension(500, preferredSize.height)
            }

            rightPanel.add(comboLabel, BorderLayout.WEST)
            rightPanel.add(artifactComboBox, BorderLayout.CENTER)

            add(rightPanel, BorderLayout.CENTER)
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
     * 下拉框中的 Artifact 包装
     */
    private data class ArtifactItem(val artifact: MavenArtifact) {
        override fun toString(): String {
            return "${artifact.groupId}:${artifact.artifactId}:${artifact.latestVersion.ifBlank { artifact.version }}"
        }
    }
}

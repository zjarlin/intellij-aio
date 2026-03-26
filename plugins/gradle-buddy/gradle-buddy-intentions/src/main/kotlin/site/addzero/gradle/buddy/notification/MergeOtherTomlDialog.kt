package site.addzero.gradle.buddy.notification

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 粘贴外部 TOML 内容的长文本对话框。
 */
class MergeOtherTomlDialog(project: Project) : DialogWrapper(project, true) {

    private val textArea = JBTextArea(24, 100).apply {
        lineWrap = false
        wrapStyleWord = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        emptyText.text = GradleBuddyBundle.message("action.merge.other.toml.dialog.placeholder")
    }

    init {
        title = GradleBuddyBundle.message("action.merge.other.toml.dialog.title")
        setOKButtonText(GradleBuddyBundle.message("action.merge.other.toml.dialog.ok"))
        init()
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(8))).apply {
            preferredSize = Dimension(JBUI.scale(920), JBUI.scale(640))
            border = JBUI.Borders.empty(8)
            add(
                JLabel(GradleBuddyBundle.message("action.merge.other.toml.dialog.label")),
                BorderLayout.NORTH
            )
            add(JBScrollPane(textArea), BorderLayout.CENTER)
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = textArea

    override fun doValidate(): ValidationInfo? {
        if (textArea.text.isBlank()) {
            return ValidationInfo(
                GradleBuddyBundle.message("action.merge.other.toml.dialog.empty"),
                textArea
            )
        }
        return null
    }

    fun getTomlContent(): String = textArea.text
}

package site.addzero.gradle.buddy.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class GradleBuddySettingsConfigurable(private val project: Project) : Configurable {

    private var tasksTextArea: JBTextArea? = null
    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "Gradle Buddy"

    override fun createComponent(): JComponent {
        tasksTextArea = JBTextArea(10, 40).apply {
            lineWrap = true
            wrapStyleWord = true
        }

        val resetButton = JButton("Reset to Defaults").apply {
            addActionListener {
                tasksTextArea?.text = GradleBuddySettingsService.DEFAULT_TASKS.joinToString("\n")
            }
        }

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Default Fallback Tasks (one per line):", JBScrollPane(tasksTextArea))
            .addComponent(resetButton)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val current = tasksTextArea?.text?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        val saved = GradleBuddySettingsService.getInstance(project).getDefaultTasks()
        return current != saved
    }

    override fun apply() {
        val tasks = tasksTextArea?.text?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        GradleBuddySettingsService.getInstance(project).setDefaultTasks(tasks)
    }

    override fun reset() {
        val tasks = GradleBuddySettingsService.getInstance(project).getDefaultTasks()
        tasksTextArea?.text = tasks.joinToString("\n")
    }

    override fun disposeUIResources() {
        tasksTextArea = null
        mainPanel = null
    }
}

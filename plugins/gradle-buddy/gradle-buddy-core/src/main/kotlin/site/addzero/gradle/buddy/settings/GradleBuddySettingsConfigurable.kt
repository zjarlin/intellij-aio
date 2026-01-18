package site.addzero.gradle.buddy.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

// Gradle Buddy 设置配置界面
class GradleBuddySettingsConfigurable(private val project: Project) : Configurable {

    private var tasksTextArea: JBTextArea? = null
    private var catalogPathField: JBTextField? = null
    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "Gradle Buddy"

    override fun createComponent(): JComponent {
        tasksTextArea = JBTextArea(10, 40).apply {
            lineWrap = true
            wrapStyleWord = true
        }

        catalogPathField = JBTextField(40).apply {
            toolTipText = "版本目录文件的相对路径，相对于项目根目录。例如：gradle/libs.versions.toml 或 checkouts/build-logic/gradle/libs.versions.toml"
        }

        val resetButton = JButton("重置为默认").apply {
            addActionListener {
                tasksTextArea?.text = GradleBuddySettingsService.DEFAULT_TASKS.joinToString("\n")
                catalogPathField?.text = GradleBuddySettingsService.DEFAULT_VERSION_CATALOG_PATH
            }
        }

        mainPanel = catalogPathField?.let {
          FormBuilder.createFormBuilder()
            .addLabeledComponent("默认回退任务（每行一个）:", JBScrollPane(tasksTextArea))
            .addLabeledComponent("版本目录文件路径（相对根目录）:", it)
        }
          ?.addComponent(resetButton)
          ?.addComponentFillVertically(JPanel(), 0)
            ?.panel

        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val currentTasks = tasksTextArea?.text?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        val savedTasks = GradleBuddySettingsService.getInstance(project).getDefaultTasks()
        val currentPath = catalogPathField?.text ?: ""
        val savedPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        return currentTasks != savedTasks || currentPath != savedPath
    }

    override fun apply() {
        val tasks = tasksTextArea?.text?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        GradleBuddySettingsService.getInstance(project).setDefaultTasks(tasks)

        val path = catalogPathField?.text?.trim() ?: ""
        if (path.isNotBlank()) {
            GradleBuddySettingsService.getInstance(project).setVersionCatalogPath(path)
        }
    }

    override fun reset() {
        val tasks = GradleBuddySettingsService.getInstance(project).getDefaultTasks()
        tasksTextArea?.text = tasks.joinToString("\n")
        catalogPathField?.text = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
    }

    override fun disposeUIResources() {
        tasksTextArea = null
        catalogPathField = null
        mainPanel = null
    }
}

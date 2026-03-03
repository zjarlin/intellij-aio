package site.addzero.gitee.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import site.addzero.gitee.settings.GiteeSettings
import javax.swing.JComponent

/**
 * Dialog for sharing project on Gitee
 */
class ShareProjectDialog(private val project: Project) : DialogWrapper(project) {

    private lateinit var repoNameField: JBTextField
    private lateinit var descriptionArea: JBTextArea
    private lateinit var visibilityComboBox: ComboBox<String>

    init {
        title = "Share Project on Gitee"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val settings = GiteeSettings.getInstance()
        val projectName = project.name.replace(" ", "-").lowercase()

        return panel {
            row("Repository name:") {
                repoNameField = textField()
                    .apply { component.text = projectName }
                    .component
            }
            row("Description:") {
                descriptionArea = JBTextArea(3, 40)
                cell(descriptionArea)
                    .comment("Optional description for the repository")
            }
            row("Visibility:") {
                visibilityComboBox = comboBox(listOf("private", "public"))
                    .apply { component.selectedItem = settings.defaultVisibility }
                    .component
            }
            row {
                label("The project will be pushed to Gitee as a new repository.")
                    .comment("Make sure you have configured your access token in Settings → Tools → Gitee")
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        val repoName = repoNameField.text.trim()
        if (repoName.isEmpty()) {
            return ValidationInfo("Repository name is required", repoNameField)
        }
        if (!repoName.matches(Regex("^[a-zA-Z0-9._-]+$"))) {
            return ValidationInfo("Repository name can only contain letters, numbers, hyphens, underscores and dots", repoNameField)
        }
        return null
    }

    fun getRepoName(): String = repoNameField.text.trim()
    fun getDescription(): String = descriptionArea.text.trim()
    fun isPrivate(): Boolean = visibilityComboBox.selectedItem == "private"
}

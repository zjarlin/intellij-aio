package site.addzero.gitee.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

/**
 * Settings page for Gitee plugin
 */
class GiteeConfigurable : Configurable {

    private lateinit var accessTokenField: JBPasswordField
    private lateinit var usernameField: JBTextField
    private lateinit var visibilityComboBox: ComboBox<String>

    private val settings: GiteeSettings
        get() = GiteeSettings.getInstance()

    override fun getDisplayName(): String = "Gitee"

    override fun createComponent(): JComponent {
        return panel {
            groupRowsRange("Account Settings") {
                row {
                    label("Access Token:")
                    accessTokenField = passwordField()
                        .comment("Optional. Needed for PRs, sharing repositories, and other advanced API features.")
                        .component
                }
                row {
                    label("Account:")
                    usernameField = textField()
                        .comment("Used by Clone from Gitee to load your public repositories. Git will ask for username/password during clone when needed.")
                        .component
                }
                row {
                    label("Default Visibility:")
                    visibilityComboBox = comboBox(listOf("private", "public"))
                        .comment("Default repository visibility when sharing projects")
                        .component
                }
            }

            separator()

            row {
                comment(
                    """
                    Clone uses your Gitee account name to list public repositories and lets Git ask for credentials when needed.
                    Access Token is optional and only used by advanced features like Share Project, Pull Requests, and authenticated API operations.
                    """.trimIndent()
                ).align(AlignX.FILL)
            }
        }
    }

    override fun isModified(): Boolean {
        return String(accessTokenField.password) != settings.accessToken ||
                usernameField.text != settings.username ||
                visibilityComboBox.selectedItem != settings.defaultVisibility
    }

    override fun apply() {
        settings.accessToken = String(accessTokenField.password)
        settings.username = usernameField.text
        settings.defaultVisibility = visibilityComboBox.selectedItem as? String ?: "private"
    }

    override fun reset() {
        accessTokenField.text = settings.accessToken
        usernameField.text = settings.username
        visibilityComboBox.selectedItem = settings.defaultVisibility
    }
}

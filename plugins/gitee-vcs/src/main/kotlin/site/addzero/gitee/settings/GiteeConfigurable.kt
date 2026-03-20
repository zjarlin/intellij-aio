package site.addzero.gitee.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import java.awt.event.ItemEvent
import javax.swing.JComponent

/**
 * Settings page for Gitee plugin
 */
class GiteeConfigurable : Configurable {

    private lateinit var authTypeComboBox: ComboBox<GiteeAuthType>
    private lateinit var accessTokenField: JBPasswordField
    private lateinit var usernameField: JBTextField
    private lateinit var passwordField: JBPasswordField
    private lateinit var visibilityComboBox: ComboBox<String>

    private val settings: GiteeSettings
        get() = GiteeSettings.getInstance()

    override fun getDisplayName(): String = "Gitee"

    override fun createComponent(): JComponent {
        val component = panel {
            groupRowsRange("Account Settings") {
                row {
                    label("Authentication:")
                    authTypeComboBox = comboBox(GiteeAuthType.entries)
                        .comment("Choose either Access Token or Username / Password.")
                        .component
                }
                row {
                    label("Access Token:")
                    accessTokenField = passwordField()
                        .comment("Used for API features and can also be used as Git HTTPS password.")
                        .component
                }
                row {
                    label("Username:")
                    usernameField = textField()
                        .comment("In Username / Password mode, this account is used for public repository listing and Git authentication.")
                        .component
                }
                row {
                    label("Password:")
                    passwordField = passwordField()
                        .comment("Stored securely in IntelliJ PasswordSafe and used for Git HTTPS authentication.")
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
                    Username / Password mode:
                    Clone lists the user's public repositories and automatically provides Git HTTPS credentials.

                    Access Token mode:
                    Clone can load authenticated repositories through the API. Advanced actions like Share Project and Pull Requests also require this mode.
                    """.trimIndent()
                ).align(AlignX.FILL)
            }
        }

        authTypeComboBox.addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                updateAuthModeUi()
            }
        }

        updateAuthModeUi()
        return component
    }

    override fun isModified(): Boolean {
        return authTypeComboBox.selectedItem != settings.authType ||
                String(accessTokenField.password) != settings.accessToken ||
                usernameField.text != settings.username ||
                String(passwordField.password) != settings.password ||
                visibilityComboBox.selectedItem != settings.defaultVisibility
    }

    override fun apply() {
        settings.authType = authTypeComboBox.selectedItem as? GiteeAuthType ?: GiteeAuthType.PASSWORD
        settings.accessToken = String(accessTokenField.password)
        settings.username = usernameField.text
        settings.password = String(passwordField.password)
        settings.defaultVisibility = visibilityComboBox.selectedItem as? String ?: "private"
    }

    override fun reset() {
        authTypeComboBox.selectedItem = settings.authType
        accessTokenField.text = settings.accessToken
        usernameField.text = settings.username
        passwordField.text = settings.password
        visibilityComboBox.selectedItem = settings.defaultVisibility
        updateAuthModeUi()
    }

    private fun updateAuthModeUi() {
        val authType = authTypeComboBox.selectedItem as? GiteeAuthType ?: GiteeAuthType.PASSWORD
        val tokenMode = authType == GiteeAuthType.TOKEN

        accessTokenField.isEnabled = tokenMode
        usernameField.isEnabled = !tokenMode
        passwordField.isEnabled = !tokenMode
    }
}

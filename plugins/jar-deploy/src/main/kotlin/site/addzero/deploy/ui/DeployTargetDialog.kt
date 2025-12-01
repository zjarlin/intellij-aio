package site.addzero.deploy.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComponentPredicate
import site.addzero.deploy.AuthType
import site.addzero.deploy.DeployTarget
import javax.swing.JComponent

/**
 * 部署目标配置对话框
 */
class DeployTargetDialog(
    private val project: Project,
    private val existingTarget: DeployTarget?
) : DialogWrapper(project) {

    private val nameField = JBTextField()
    private val hostField = JBTextField()
    private val portField = JBTextField("22")
    private val usernameField = JBTextField()
    private val passwordField = JBPasswordField()
    private val privateKeyField = TextFieldWithBrowseButton()
    private val passphraseField = JBPasswordField()
    private val remoteDirField = JBTextField()
    private val preDeployField = JBTextField()
    private val postDeployField = JBTextField()

    private var authType: AuthType = AuthType.PASSWORD

    init {
        title = if (existingTarget == null) "Add Deploy Target" else "Edit Deploy Target"

        privateKeyField.addBrowseFolderListener(
            "Select Private Key",
            "Select SSH private key file",
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )

        existingTarget?.let { target ->
            nameField.text = target.name ?: ""
            hostField.text = target.host ?: ""
            portField.text = (target.port ?: 22).toString()
            usernameField.text = target.username ?: ""
            passwordField.text = target.password ?: ""
            privateKeyField.text = target.privateKeyPath ?: ""
            passphraseField.text = target.passphrase ?: ""
            authType = target.authType ?: AuthType.PASSWORD
            remoteDirField.text = target.remoteDir ?: "/usr/local/app"
            preDeployField.text = target.preDeployCommand ?: ""
            postDeployField.text = target.postDeployCommand ?: ""
        }

        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Name:") {
            cell(nameField)
                .columns(COLUMNS_MEDIUM)
                .comment("Unique name for this deploy target")
        }

        group("SSH Connection") {
            row("Host:") {
                cell(hostField)
                    .columns(COLUMNS_MEDIUM)
                    .comment("Remote server hostname or IP")
            }

            row("Port:") {
                cell(portField)
                    .columns(COLUMNS_SHORT)
                    .comment("SSH port (default: 22)")
            }

            row("Username:") {
                cell(usernameField)
                    .columns(COLUMNS_MEDIUM)
            }

            buttonsGroup("Authentication:") {
                row {
                    radioButton("Password", AuthType.PASSWORD)
                        .comment("Use password authentication")
                }
                row {
                    radioButton("Key Pair", AuthType.KEY_PAIR)
                        .comment("Use SSH key authentication")
                }
            }.bind(::authType)

            row("Password:") {
                cell(passwordField)
                    .columns(COLUMNS_MEDIUM)
            }.visibleIf(ComparablePredicate(AuthType.PASSWORD) { authType })

            row("Private Key:") {
                cell(privateKeyField)
                    .columns(COLUMNS_MEDIUM)
                    .comment("Path to SSH private key (e.g., ~/.ssh/id_rsa)")
            }.visibleIf(ComparablePredicate(AuthType.KEY_PAIR) { authType })

            row("Passphrase:") {
                cell(passphraseField)
                    .columns(COLUMNS_MEDIUM)
                    .comment("Leave empty if key has no passphrase")
            }.visibleIf(ComparablePredicate(AuthType.KEY_PAIR) { authType })
        }

        row("Remote Directory:") {
            cell(remoteDirField)
                .columns(COLUMNS_MEDIUM)
                .comment("e.g., /usr/local/app")
        }

        collapsibleGroup("Advanced") {
            row("Pre-Deploy Command:") {
                cell(preDeployField)
                    .columns(COLUMNS_MEDIUM)
                    .comment("Command to run before upload (e.g., systemctl stop myapp)")
            }

            row("Post-Deploy Command:") {
                cell(postDeployField)
                    .columns(COLUMNS_MEDIUM)
                    .comment("Command to run after upload (e.g., systemctl start myapp)")
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (nameField.text.isBlank()) {
            return ValidationInfo("Name is required", nameField)
        }
        if (hostField.text.isBlank()) {
            return ValidationInfo("Host is required", hostField)
        }
        if (usernameField.text.isBlank()) {
            return ValidationInfo("Username is required", usernameField)
        }
        if (authType == AuthType.PASSWORD && String(passwordField.password).isBlank()) {
            return ValidationInfo("Password is required for password authentication", passwordField)
        }
        if (authType == AuthType.KEY_PAIR && privateKeyField.text.isBlank()) {
            return ValidationInfo("Private key path is required for key authentication", privateKeyField)
        }
        if (remoteDirField.text.isBlank()) {
            return ValidationInfo("Remote directory is required", remoteDirField)
        }
        return null
    }

    fun getTarget(): DeployTarget = DeployTarget().apply {
        name = nameField.text
        host = hostField.text
        port = portField.text.toIntOrNull() ?: 22
        username = usernameField.text
        password = String(passwordField.password)
        privateKeyPath = privateKeyField.text
        passphrase = String(passphraseField.password)
        this.authType = this@DeployTargetDialog.authType
        remoteDir = remoteDirField.text
        preDeployCommand = preDeployField.text
        postDeployCommand = postDeployField.text
        enabled = true
    }
}

private class ComparablePredicate<T>(
    private val expected: T,
    private val getter: () -> T
) : ComponentPredicate() {
    override fun invoke(): Boolean = getter() == expected
    override fun addListener(listener: (Boolean) -> Unit) {}
}

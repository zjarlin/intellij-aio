package site.addzero.openprojecteverywhere.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import site.addzero.openprojecteverywhere.OpenProjectEverywhereBundle
import site.addzero.openprojecteverywhere.service.OpenProjectEverywhereSearchService
import java.net.URI
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class OpenProjectEverywhereConfigurable : Configurable {

    private val settings: OpenProjectEverywhereSettings
        get() = OpenProjectEverywhereSettings.getInstance()

    private val localEnabledCheckBox = JBCheckBox(OpenProjectEverywhereBundle.message("settings.local.enabled"))
    private val localRootField = TextFieldWithBrowseButton()

    private val githubEnabledCheckBox = JBCheckBox(OpenProjectEverywhereBundle.message("settings.provider.enabled"))
    private val githubAuthModeCombo = ComboBox(AuthMode.entries.toTypedArray())
    private val githubUsernameField = JBTextField()
    private val githubSecretField = JBPasswordField()

    private val gitlabEnabledCheckBox = JBCheckBox(OpenProjectEverywhereBundle.message("settings.provider.enabled"))
    private val gitlabBaseUrlField = JBTextField()
    private val gitlabAuthModeCombo = ComboBox(AuthMode.entries.toTypedArray())
    private val gitlabUsernameField = JBTextField()
    private val gitlabSecretField = JBPasswordField()

    private val giteeEnabledCheckBox = JBCheckBox(OpenProjectEverywhereBundle.message("settings.provider.enabled"))
    private val giteeBaseUrlField = JBTextField()
    private val giteeAuthModeCombo = ComboBox(AuthMode.entries.toTypedArray())
    private val giteeUsernameField = JBTextField()
    private val giteeSecretField = JBPasswordField()

    private val customEnabledCheckBox = JBCheckBox(OpenProjectEverywhereBundle.message("settings.provider.enabled"))
    private val customDisplayNameField = JBTextField()
    private val customProviderKindCombo = ComboBox(ProviderKind.entries.toTypedArray())
    private val customBaseUrlField = JBTextField()
    private val customAuthModeCombo = ComboBox(AuthMode.entries.toTypedArray())
    private val customUsernameField = JBTextField()
    private val customSecretField = JBPasswordField()

    private var githubStoredSecret = ""
    private var gitlabStoredSecret = ""
    private var giteeStoredSecret = ""
    private var customStoredSecret = ""

    private var githubSecretDirty = false
    private var gitlabSecretDirty = false
    private var giteeSecretDirty = false
    private var customSecretDirty = false

    private var hydratingSecrets = false

    override fun getDisplayName(): String = OpenProjectEverywhereBundle.message("settings.display.name")

    override fun createComponent(): JComponent {
        localRootField.addBrowseFolderListener(
            OpenProjectEverywhereBundle.message("settings.local.root"),
            OpenProjectEverywhereBundle.message("settings.local.root.comment"),
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )

        val panel = panel {
            group(OpenProjectEverywhereBundle.message("settings.section.local")) {
                row {
                    cell(localEnabledCheckBox)
                }
                row(OpenProjectEverywhereBundle.message("settings.local.root")) {
                    cell(localRootField)
                        .align(AlignX.FILL)
                        .comment(OpenProjectEverywhereBundle.message("settings.local.root.comment"))
                }
            }

            group(OpenProjectEverywhereBundle.message("settings.section.github")) {
                row {
                    cell(githubEnabledCheckBox)
                }
                row(OpenProjectEverywhereBundle.message("settings.auth.mode")) {
                    cell(githubAuthModeCombo)
                        .comment(OpenProjectEverywhereBundle.message("settings.github.comment"))
                }
                row(OpenProjectEverywhereBundle.message("settings.username")) {
                    cell(githubUsernameField).align(AlignX.FILL)
                }
                row(OpenProjectEverywhereBundle.message("settings.secret")) {
                    cell(githubSecretField).align(AlignX.FILL)
                }
            }

            group(OpenProjectEverywhereBundle.message("settings.section.gitlab")) {
                row {
                    cell(gitlabEnabledCheckBox)
                }
                row(OpenProjectEverywhereBundle.message("settings.gitlab.baseUrl")) {
                    cell(gitlabBaseUrlField)
                        .align(AlignX.FILL)
                        .comment(OpenProjectEverywhereBundle.message("settings.gitlab.comment"))
                }
                row(OpenProjectEverywhereBundle.message("settings.auth.mode")) {
                    cell(gitlabAuthModeCombo)
                }
                row(OpenProjectEverywhereBundle.message("settings.username")) {
                    cell(gitlabUsernameField).align(AlignX.FILL)
                }
                row(OpenProjectEverywhereBundle.message("settings.secret")) {
                    cell(gitlabSecretField).align(AlignX.FILL)
                }
            }

            group(OpenProjectEverywhereBundle.message("settings.section.gitee")) {
                row {
                    cell(giteeEnabledCheckBox)
                }
                row(OpenProjectEverywhereBundle.message("settings.gitee.baseUrl")) {
                    cell(giteeBaseUrlField)
                        .align(AlignX.FILL)
                        .comment(OpenProjectEverywhereBundle.message("settings.gitee.comment"))
                }
                row(OpenProjectEverywhereBundle.message("settings.auth.mode")) {
                    cell(giteeAuthModeCombo)
                }
                row(OpenProjectEverywhereBundle.message("settings.username")) {
                    cell(giteeUsernameField).align(AlignX.FILL)
                }
                row(OpenProjectEverywhereBundle.message("settings.secret")) {
                    cell(giteeSecretField).align(AlignX.FILL)
                }
            }

            group(OpenProjectEverywhereBundle.message("settings.section.custom")) {
                row {
                    cell(customEnabledCheckBox)
                }
                row(OpenProjectEverywhereBundle.message("settings.custom.displayName")) {
                    cell(customDisplayNameField).align(AlignX.FILL)
                }
                row(OpenProjectEverywhereBundle.message("settings.custom.providerKind")) {
                    cell(customProviderKindCombo)
                        .comment(OpenProjectEverywhereBundle.message("settings.custom.comment"))
                }
                row(OpenProjectEverywhereBundle.message("settings.custom.baseUrl")) {
                    cell(customBaseUrlField).align(AlignX.FILL)
                }
                row(OpenProjectEverywhereBundle.message("settings.auth.mode")) {
                    cell(customAuthModeCombo)
                }
                row(OpenProjectEverywhereBundle.message("settings.username")) {
                    cell(customUsernameField).align(AlignX.FILL)
                }
                row(OpenProjectEverywhereBundle.message("settings.secret")) {
                    cell(customSecretField).align(AlignX.FILL)
                }
            }
        }

        githubEnabledCheckBox.addActionListener { updateEnabledStates() }
        githubAuthModeCombo.addActionListener { updateEnabledStates() }
        gitlabEnabledCheckBox.addActionListener { updateEnabledStates() }
        gitlabAuthModeCombo.addActionListener { updateEnabledStates() }
        giteeEnabledCheckBox.addActionListener { updateEnabledStates() }
        giteeAuthModeCombo.addActionListener { updateEnabledStates() }
        customEnabledCheckBox.addActionListener { updateEnabledStates() }
        customAuthModeCombo.addActionListener { updateEnabledStates() }
        localEnabledCheckBox.addActionListener { updateEnabledStates() }

        installSecretDirtyTracking(githubSecretField) { githubSecretDirty = true }
        installSecretDirtyTracking(gitlabSecretField) { gitlabSecretDirty = true }
        installSecretDirtyTracking(giteeSecretField) { giteeSecretDirty = true }
        installSecretDirtyTracking(customSecretField) { customSecretDirty = true }

        reset()
        loadSecretsAsync()
        return panel
    }

    override fun isModified(): Boolean {
        return localEnabledCheckBox.isSelected != settings.localProjectsEnabled ||
            localRootField.text.trim() != settings.localProjectsRoot ||
            githubEnabledCheckBox.isSelected != settings.githubEnabled ||
            githubAuthModeCombo.selectedItem != settings.githubAuthMode ||
            githubUsernameField.text.trim() != settings.githubUsername ||
            (githubSecretDirty && String(githubSecretField.password) != githubStoredSecret) ||
            gitlabEnabledCheckBox.isSelected != settings.gitlabEnabled ||
            gitlabBaseUrlField.text.trim() != settings.gitlabBaseUrl ||
            gitlabAuthModeCombo.selectedItem != settings.gitlabAuthMode ||
            gitlabUsernameField.text.trim() != settings.gitlabUsername ||
            (gitlabSecretDirty && String(gitlabSecretField.password) != gitlabStoredSecret) ||
            giteeEnabledCheckBox.isSelected != settings.giteeEnabled ||
            giteeBaseUrlField.text.trim() != settings.giteeBaseUrl ||
            giteeAuthModeCombo.selectedItem != settings.giteeAuthMode ||
            giteeUsernameField.text.trim() != settings.giteeUsername ||
            (giteeSecretDirty && String(giteeSecretField.password) != giteeStoredSecret) ||
            customEnabledCheckBox.isSelected != settings.customEnabled ||
            customDisplayNameField.text.trim() != settings.customDisplayName.ifBlank {
                OpenProjectEverywhereBundle.message("settings.custom.display.default")
            } ||
            customProviderKindCombo.selectedItem != settings.customProviderKind ||
            customBaseUrlField.text.trim() != settings.customBaseUrl ||
            customAuthModeCombo.selectedItem != settings.customAuthMode ||
            customUsernameField.text.trim() != settings.customUsername ||
            (customSecretDirty && String(customSecretField.password) != customStoredSecret)
    }

    override fun apply() {
        validateSettings()

        settings.localProjectsEnabled = localEnabledCheckBox.isSelected
        settings.localProjectsRoot = localRootField.text.trim()

        settings.githubEnabled = githubEnabledCheckBox.isSelected
        settings.githubAuthMode = githubAuthModeCombo.selectedItem as? AuthMode ?: AuthMode.TOKEN
        settings.githubUsername = githubUsernameField.text.trim()
        if (githubSecretDirty) {
            settings.setGithubSecret(String(githubSecretField.password))
            githubStoredSecret = String(githubSecretField.password)
        }

        settings.gitlabEnabled = gitlabEnabledCheckBox.isSelected
        settings.gitlabBaseUrl = gitlabBaseUrlField.text.trim()
        settings.gitlabAuthMode = gitlabAuthModeCombo.selectedItem as? AuthMode ?: AuthMode.TOKEN
        settings.gitlabUsername = gitlabUsernameField.text.trim()
        if (gitlabSecretDirty) {
            settings.setGitlabSecret(String(gitlabSecretField.password))
            gitlabStoredSecret = String(gitlabSecretField.password)
        }

        settings.giteeEnabled = giteeEnabledCheckBox.isSelected
        settings.giteeBaseUrl = giteeBaseUrlField.text.trim()
        settings.giteeAuthMode = giteeAuthModeCombo.selectedItem as? AuthMode ?: AuthMode.TOKEN
        settings.giteeUsername = giteeUsernameField.text.trim()
        if (giteeSecretDirty) {
            settings.setGiteeSecret(String(giteeSecretField.password))
            giteeStoredSecret = String(giteeSecretField.password)
        }

        settings.customEnabled = customEnabledCheckBox.isSelected
        settings.customDisplayName = customDisplayNameField.text.trim()
        settings.customProviderKind = customProviderKindCombo.selectedItem as? ProviderKind ?: ProviderKind.GITLAB
        settings.customBaseUrl = customBaseUrlField.text.trim()
        settings.customAuthMode = customAuthModeCombo.selectedItem as? AuthMode ?: AuthMode.TOKEN
        settings.customUsername = customUsernameField.text.trim()
        if (customSecretDirty) {
            settings.setCustomSecret(String(customSecretField.password))
            customStoredSecret = String(customSecretField.password)
        }

        githubSecretDirty = false
        gitlabSecretDirty = false
        giteeSecretDirty = false
        customSecretDirty = false

        OpenProjectEverywhereSearchService.getInstance().invalidateCaches()
    }

    override fun reset() {
        localEnabledCheckBox.isSelected = settings.localProjectsEnabled
        localRootField.text = settings.localProjectsRoot

        githubEnabledCheckBox.isSelected = settings.githubEnabled
        githubAuthModeCombo.selectedItem = settings.githubAuthMode
        githubUsernameField.text = settings.githubUsername
        githubSecretDirty = false
        githubSecretField.text = githubStoredSecret

        gitlabEnabledCheckBox.isSelected = settings.gitlabEnabled
        gitlabBaseUrlField.text = settings.gitlabBaseUrl
        gitlabAuthModeCombo.selectedItem = settings.gitlabAuthMode
        gitlabUsernameField.text = settings.gitlabUsername
        gitlabSecretDirty = false
        gitlabSecretField.text = gitlabStoredSecret

        giteeEnabledCheckBox.isSelected = settings.giteeEnabled
        giteeBaseUrlField.text = settings.giteeBaseUrl
        giteeAuthModeCombo.selectedItem = settings.giteeAuthMode
        giteeUsernameField.text = settings.giteeUsername
        giteeSecretDirty = false
        giteeSecretField.text = giteeStoredSecret

        customEnabledCheckBox.isSelected = settings.customEnabled
        customDisplayNameField.text = settings.customDisplayName.ifBlank {
            OpenProjectEverywhereBundle.message("settings.custom.display.default")
        }
        customProviderKindCombo.selectedItem = settings.customProviderKind
        customBaseUrlField.text = settings.customBaseUrl
        customAuthModeCombo.selectedItem = settings.customAuthMode
        customUsernameField.text = settings.customUsername
        customSecretDirty = false
        customSecretField.text = customStoredSecret

        updateEnabledStates()
    }

    private fun validateSettings() {
        if (localEnabledCheckBox.isSelected && localRootField.text.trim().isEmpty()) {
            throw ConfigurationException(OpenProjectEverywhereBundle.message("settings.validation.localRoot"))
        }

        if (gitlabEnabledCheckBox.isSelected) {
            validateUrl(gitlabBaseUrlField.text.trim(), OpenProjectEverywhereBundle.message("settings.section.gitlab"))
        }

        if (giteeEnabledCheckBox.isSelected) {
            validateUrl(giteeBaseUrlField.text.trim(), OpenProjectEverywhereBundle.message("settings.section.gitee"))
        }

        if (customEnabledCheckBox.isSelected) {
            if (customDisplayNameField.text.trim().isEmpty()) {
                throw ConfigurationException(OpenProjectEverywhereBundle.message("settings.validation.customName"))
            }
            validateUrl(customBaseUrlField.text.trim(), customDisplayNameField.text.trim())
        }
    }

    private fun validateUrl(value: String, label: String) {
        if (value.isBlank()) {
            throw ConfigurationException(OpenProjectEverywhereBundle.message("settings.validation.url", label))
        }
        val valid = runCatching {
            val uri = URI(value)
            uri.scheme == "http" || uri.scheme == "https"
        }.getOrDefault(false)
        if (!valid) {
            throw ConfigurationException(OpenProjectEverywhereBundle.message("settings.validation.url", label))
        }
    }

    private fun updateEnabledStates() {
        localRootField.isEnabled = localEnabledCheckBox.isSelected

        val githubEnabled = githubEnabledCheckBox.isSelected
        githubAuthModeCombo.isEnabled = githubEnabled
        githubUsernameField.isEnabled = githubEnabled && githubAuthModeCombo.selectedItem == AuthMode.USERNAME_PASSWORD
        githubSecretField.isEnabled = githubEnabled

        val gitlabEnabled = gitlabEnabledCheckBox.isSelected
        gitlabBaseUrlField.isEnabled = gitlabEnabled
        gitlabAuthModeCombo.isEnabled = gitlabEnabled
        gitlabUsernameField.isEnabled = gitlabEnabled && gitlabAuthModeCombo.selectedItem == AuthMode.USERNAME_PASSWORD
        gitlabSecretField.isEnabled = gitlabEnabled

        val giteeEnabled = giteeEnabledCheckBox.isSelected
        giteeBaseUrlField.isEnabled = giteeEnabled
        giteeAuthModeCombo.isEnabled = giteeEnabled
        giteeUsernameField.isEnabled = giteeEnabled && giteeAuthModeCombo.selectedItem == AuthMode.USERNAME_PASSWORD
        giteeSecretField.isEnabled = giteeEnabled

        val customEnabled = customEnabledCheckBox.isSelected
        customDisplayNameField.isEnabled = customEnabled
        customProviderKindCombo.isEnabled = customEnabled
        customBaseUrlField.isEnabled = customEnabled
        customAuthModeCombo.isEnabled = customEnabled
        customUsernameField.isEnabled = customEnabled && customAuthModeCombo.selectedItem == AuthMode.USERNAME_PASSWORD
        customSecretField.isEnabled = customEnabled
    }

    private fun loadSecretsAsync() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val githubSecret = settings.githubSecret()
            val gitlabSecret = settings.gitlabSecret()
            val giteeSecret = settings.giteeSecret()
            val customSecret = settings.customSecret()

            ApplicationManager.getApplication().invokeLater {
                hydratingSecrets = true
                try {
                    githubStoredSecret = githubSecret
                    gitlabStoredSecret = gitlabSecret
                    giteeStoredSecret = giteeSecret
                    customStoredSecret = customSecret

                    if (!githubSecretDirty) {
                        githubSecretField.text = githubSecret
                    }
                    if (!gitlabSecretDirty) {
                        gitlabSecretField.text = gitlabSecret
                    }
                    if (!giteeSecretDirty) {
                        giteeSecretField.text = giteeSecret
                    }
                    if (!customSecretDirty) {
                        customSecretField.text = customSecret
                    }
                } finally {
                    hydratingSecrets = false
                }
            }
        }
    }

    private fun installSecretDirtyTracking(field: JBPasswordField, onDirty: () -> Unit) {
        field.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = markDirty()
            override fun removeUpdate(e: DocumentEvent?) = markDirty()
            override fun changedUpdate(e: DocumentEvent?) = markDirty()

            private fun markDirty() {
                if (!hydratingSecrets) {
                    onDirty()
                }
            }
        })
    }
}

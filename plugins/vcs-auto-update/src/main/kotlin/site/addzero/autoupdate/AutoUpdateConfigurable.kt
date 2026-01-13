package site.addzero.autoupdate

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

/**
 * Settings page for VCS Auto Update plugin
 */
class AutoUpdateConfigurable : Configurable {

    private lateinit var autoPullCheckBox: JBCheckBox
    private lateinit var showNotificationCheckBox: JBCheckBox
    private lateinit var pullRebaseCheckBox: JBCheckBox

    private val settings: AutoUpdateSettings
        get() = AutoUpdateSettings.getInstance()

    override fun getDisplayName(): String = "Vcs auto update"

    override fun createComponent(): JComponent {
        return panel {
            groupRowsRange("General settings") {
                row {
                    autoPullCheckBox = checkBox("Auto pull before push")
                        .comment("Automatically pull latest changes before pushing to avoid conflicts")
                        .component
                }
                row {
                    showNotificationCheckBox = checkBox("Show notification")
                        .comment("Show balloon notification when auto-pull is triggered")
                        .component
                }
                row {
                    pullRebaseCheckBox = checkBox("Use rebase when pulling")
                        .comment("Use 'git pull --rebase' instead of 'git pull'")
                        .component
                }
            }

            separator()

            row {
                comment(
                    """
                    When enabled, this plugin will automatically pull the latest changes from
                    the remote repository before you push, reducing the chance of push conflicts.
                    """.trimIndent()
                ).align(AlignX.FILL)
            }
        }
    }

    override fun isModified(): Boolean {
        return autoPullCheckBox.isSelected != settings.autoPullBeforePush ||
                showNotificationCheckBox.isSelected != settings.showNotification ||
                pullRebaseCheckBox.isSelected != settings.pullRebase
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        settings.autoPullBeforePush = autoPullCheckBox.isSelected
        settings.showNotification = showNotificationCheckBox.isSelected
        settings.pullRebase = pullRebaseCheckBox.isSelected
    }

    override fun reset() {
        autoPullCheckBox.isSelected = settings.autoPullBeforePush
        showNotificationCheckBox.isSelected = settings.showNotification
        pullRebaseCheckBox.isSelected = settings.pullRebase
    }
}

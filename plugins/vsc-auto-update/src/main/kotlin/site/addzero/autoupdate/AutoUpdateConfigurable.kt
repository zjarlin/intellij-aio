package site.addzero.autoupdate

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import javax.swing.JComponent

/**
 * Settings page for VSC Auto Update plugin
 */
class AutoUpdateConfigurable : Configurable {

    private lateinit var autoPullCheckBox: JBCheckBox
    private lateinit var showNotificationCheckBox: JBCheckBox
    private lateinit var pullRebaseCheckBox: JBCheckBox

    private val settings: AutoUpdateSettings
        get() = AutoUpdateSettings.getInstance()

    override fun getDisplayName(): String = "VSC Auto Update"

    override fun createComponent(): JComponent {
        return panel {
            groupRowsRange("General Settings") {
                row {
                    checkBox("Auto pull before push")
                        .componentAlso { it ->
                            autoPullCheckBox = it
                        }
                        .comment("Automatically pull latest changes before pushing to avoid conflicts")
                }
                row {
                    checkBox("Show notification")
                        .componentAlso { it ->
                            showNotificationCheckBox = it
                        }
                        .comment("Show balloon notification when auto-pull is triggered")
                }
                row {
                    checkBox("Use rebase when pulling")
                        .componentAlso { it ->
                            pullRebaseCheckBox = it
                        }
                        .comment("Use 'git pull --rebase' instead of 'git pull'")
                }
            }

            separator {
                row {
                    comment(
                        """
                        When enabled, this plugin will automatically pull the latest changes from
                        the remote repository before you push, reducing the chance of push conflicts.
                        """.trimIndent()
                    ).component.horizontalAlign(HorizontalAlign.FILL)
                }
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

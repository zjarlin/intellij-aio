package site.addzero.composebuddy.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import site.addzero.composebuddy.ComposeBuddyBundle

class ComposeBuddyConfigurable : BoundConfigurable(ComposeBuddyBundle.message("settings.display.name")) {
    override fun createPanel() = panel {
        val settings = ComposeBuddySettingsService.getInstance().state

        group(ComposeBuddyBundle.message("settings.group.analysis")) {
            row(ComposeBuddyBundle.message("settings.parameter.threshold")) {
                intTextField(1..99)
                    .bindIntText(settings::parameterThreshold)
            }
            row(ComposeBuddyBundle.message("settings.callback.threshold")) {
                intTextField(1..99)
                    .bindIntText(settings::callbackThreshold)
            }
            row(ComposeBuddyBundle.message("settings.statepair.threshold")) {
                intTextField(1..99)
                    .bindIntText(settings::statePairThreshold)
            }
        }

        group(ComposeBuddyBundle.message("settings.group.wrapper")) {
            row {
                checkBox(ComposeBuddyBundle.message("settings.wrapper.props.default"))
                    .bindSelected(settings::preferPropsWrapper)
            }
            row {
                checkBox(ComposeBuddyBundle.message("settings.wrapper.compat.default"))
                    .bindSelected(settings::keepCompatibilityByDefault)
            }
            row {
                checkBox(ComposeBuddyBundle.message("settings.wrapper.templates.default"))
                    .bindSelected(settings::addTemplateParameters)
            }
        }
    }
}

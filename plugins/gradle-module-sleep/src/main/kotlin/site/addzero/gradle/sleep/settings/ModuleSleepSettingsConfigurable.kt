package site.addzero.gradle.sleep.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider

class ModuleSleepSettingsConfigurable(private val project: Project) : Configurable {

    private var autoSleepCheckBox: JBCheckBox? = null
    private var idleTimeoutSlider: JSlider? = null
    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "Gradle Module Sleep"

    override fun createComponent(): JComponent {
        autoSleepCheckBox = JBCheckBox("Enable Auto-Sleep (uncheck to disable, leave unchecked for auto-detect)").apply {
            isSelected = false
            isThreeState = true
        }

        idleTimeoutSlider = JSlider(1, 30, 5).apply {
            majorTickSpacing = 5
            minorTickSpacing = 1
            paintTicks = true
            paintLabels = true
        }

        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("Module Sleep Settings"))
            .addVerticalGap(10)
            .addComponent(autoSleepCheckBox!!)
            .addLabeledComponent("Module Idle Timeout (minutes):", idleTimeoutSlider!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = ModuleSleepSettingsService.getInstance(project)
        val currentAutoSleep = when {
            autoSleepCheckBox?.isSelected == true -> true
            autoSleepCheckBox?.isSelected == false -> false
            else -> null
        }
        return currentAutoSleep != settings.getAutoSleepEnabled() ||
               idleTimeoutSlider?.value != settings.getModuleIdleTimeoutMinutes()
    }

    override fun apply() {
        val settings = ModuleSleepSettingsService.getInstance(project)
        val autoSleep = when {
            autoSleepCheckBox?.isSelected == true -> true
            autoSleepCheckBox?.isSelected == false -> false
            else -> null
        }
        settings.setAutoSleepEnabled(autoSleep)
        settings.setModuleIdleTimeoutMinutes(idleTimeoutSlider?.value ?: 5)
    }

    override fun reset() {
        val settings = ModuleSleepSettingsService.getInstance(project)
        when (settings.getAutoSleepEnabled()) {
            true -> autoSleepCheckBox?.isSelected = true
            false -> autoSleepCheckBox?.isSelected = false
            null -> {
                // Three-state checkbox for auto-detect
                autoSleepCheckBox?.isSelected = false
            }
        }
        idleTimeoutSlider?.value = settings.getModuleIdleTimeoutMinutes()
    }

    override fun disposeUIResources() {
        autoSleepCheckBox = null
        idleTimeoutSlider = null
        mainPanel = null
    }
}

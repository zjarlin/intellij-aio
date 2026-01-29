package site.addzero.gradle.sleep.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.ThreeStateCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider

class ModuleSleepSettingsConfigurable(private val project: Project) : Configurable {

    private var autoSleepCheckBox: ThreeStateCheckBox? = null
    private var floatingToolbarCheckBox: com.intellij.ui.components.JBCheckBox? = null
    private var idleTimeoutSlider: JSlider? = null
    private var manualFoldersField: JBTextField? = null
    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "Gradle Module Sleep"

    override fun createComponent(): JComponent {
        autoSleepCheckBox = ThreeStateCheckBox("Enable auto-sleep (uncheck to disable, leave unchecked for auto-detect)").apply {
            state = ThreeStateCheckBox.State.DONT_CARE
        }

        floatingToolbarCheckBox = com.intellij.ui.components.JBCheckBox("Show floating toolbar").apply {
            isSelected = true
        }

        idleTimeoutSlider = JSlider(1, 30, 5).apply {
            majorTickSpacing = 5
            minorTickSpacing = 1
            paintTicks = true
            paintLabels = true
        }

        manualFoldersField = JBTextField().apply {
            emptyText.text = "Root folder names, comma-separated (e.g. gradle-buddy, maven-buddy)"
        }

        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("Module sleep settings"))
            .addVerticalGap(10)
            .addComponent(autoSleepCheckBox!!)
            .addComponent(floatingToolbarCheckBox!!)
            .addLabeledComponent("Module idle timeout (minutes):", idleTimeoutSlider!!)
            .addLabeledComponent("Root module folders:", manualFoldersField!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = ModuleSleepSettingsService.getInstance(project)
        val currentAutoSleep = when (autoSleepCheckBox?.state) {
            ThreeStateCheckBox.State.SELECTED -> true
            ThreeStateCheckBox.State.NOT_SELECTED -> false
            else -> null
        }
        return currentAutoSleep != settings.getAutoSleepEnabled() ||
               floatingToolbarCheckBox?.isSelected != settings.isFloatingToolbarEnabled() ||
               idleTimeoutSlider?.value != settings.getModuleIdleTimeoutMinutes() ||
               manualFoldersField?.text != settings.getManualFolderNamesRaw()
    }

    override fun apply() {
        val settings = ModuleSleepSettingsService.getInstance(project)
        val autoSleep = when (autoSleepCheckBox?.state) {
            ThreeStateCheckBox.State.SELECTED -> true
            ThreeStateCheckBox.State.NOT_SELECTED -> false
            else -> null
        }
        settings.setAutoSleepEnabled(autoSleep)
        settings.setFloatingToolbarEnabled(floatingToolbarCheckBox?.isSelected ?: true)
        settings.setModuleIdleTimeoutMinutes(idleTimeoutSlider?.value ?: 5)
        settings.setManualFolderNames(manualFoldersField?.text ?: "")
    }

    override fun reset() {
        val settings = ModuleSleepSettingsService.getInstance(project)
        autoSleepCheckBox?.state = when (settings.getAutoSleepEnabled()) {
            true -> ThreeStateCheckBox.State.SELECTED
            false -> ThreeStateCheckBox.State.NOT_SELECTED
            null -> ThreeStateCheckBox.State.DONT_CARE
        }
        floatingToolbarCheckBox?.isSelected = settings.isFloatingToolbarEnabled()
        idleTimeoutSlider?.value = settings.getModuleIdleTimeoutMinutes()
        manualFoldersField?.text = settings.getManualFolderNamesRaw()
    }

    override fun disposeUIResources() {
        autoSleepCheckBox = null
        floatingToolbarCheckBox = null
        idleTimeoutSlider = null
        manualFoldersField = null
        mainPanel = null
    }
}

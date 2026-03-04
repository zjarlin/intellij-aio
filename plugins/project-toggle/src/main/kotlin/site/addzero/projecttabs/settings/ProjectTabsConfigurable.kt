package site.addzero.projecttabs.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * Settings UI for Project Tabs plugin
 */
class ProjectTabsConfigurable : Configurable {

    private var mainPanel: JPanel? = null
    private lateinit var enabledCheckBox: JBCheckBox
    private lateinit var showCloseButtonCheckBox: JBCheckBox
    private lateinit var showProjectPathCheckBox: JBCheckBox
    private lateinit var maxTabWidthSpinner: JSpinner

    override fun getDisplayName(): String = "Project Tabs"

    override fun createComponent(): JComponent? {
        enabledCheckBox = JBCheckBox("Enable Project Tabs")
        showCloseButtonCheckBox = JBCheckBox("Show close button on tabs")
        showProjectPathCheckBox = JBCheckBox("Show project path in tooltip")

        maxTabWidthSpinner = JSpinner(SpinnerNumberModel(200, 100, 400, 10))

        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(enabledCheckBox)
            .addSeparator()
            .addLabeledComponent(JBLabel("Max tab width (px):"), maxTabWidthSpinner)
            .addComponent(showCloseButtonCheckBox)
            .addComponent(showProjectPathCheckBox)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        mainPanel!!.border = JBUI.Borders.empty(10)

        reset()

        return mainPanel
    }

    override fun isModified(): Boolean {
        val settings = ProjectTabsSettings.getInstance()
        return enabledCheckBox.isSelected != settings.enabled ||
                showCloseButtonCheckBox.isSelected != settings.showCloseButton ||
                showProjectPathCheckBox.isSelected != settings.showProjectPath ||
                (maxTabWidthSpinner.value as Int) != settings.maxTabWidth
    }

    override fun apply() {
        val settings = ProjectTabsSettings.getInstance()
        settings.enabled = enabledCheckBox.isSelected
        settings.showCloseButton = showCloseButtonCheckBox.isSelected
        settings.showProjectPath = showProjectPathCheckBox.isSelected
        settings.maxTabWidth = maxTabWidthSpinner.value as Int
    }

    override fun reset() {
        val settings = ProjectTabsSettings.getInstance()
        enabledCheckBox.isSelected = settings.enabled
        showCloseButtonCheckBox.isSelected = settings.showCloseButton
        showProjectPathCheckBox.isSelected = settings.showProjectPath
        maxTabWidthSpinner.value = settings.maxTabWidth
    }
}

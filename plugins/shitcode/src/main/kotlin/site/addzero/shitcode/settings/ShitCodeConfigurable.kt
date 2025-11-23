package site.addzero.shitcode.settings

import com.intellij.openapi.options.Configurable
import javax.swing.*

class ShitCodeConfigurable : Configurable {
    private var panel: JPanel? = null
    private var annotationField: JTextField? = null

    override fun getDisplayName(): String = "ShitCode"

    override fun createComponent(): JComponent? {
        panel = JPanel()
        panel!!.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val labelPanel = JPanel()
        labelPanel.layout = BoxLayout(labelPanel, BoxLayout.X_AXIS)
        labelPanel.add(JLabel("垃圾代码注解名称: "))
        annotationField = JTextField(20)
        labelPanel.add(annotationField)
        labelPanel.add(Box.createHorizontalGlue())

        panel!!.add(labelPanel)
        panel!!.add(Box.createVerticalGlue())

        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val settings = ShitCodeSettingsService.getInstance().state
        return annotationField?.text != settings.shitAnnotation
    }

    override fun apply() {
        val settings = ShitCodeSettingsService.getInstance().state
        settings.shitAnnotation = annotationField?.text ?: "Shit"
    }

    override fun reset() {
        val settings = ShitCodeSettingsService.getInstance().state
        annotationField?.text = settings.shitAnnotation
    }
}

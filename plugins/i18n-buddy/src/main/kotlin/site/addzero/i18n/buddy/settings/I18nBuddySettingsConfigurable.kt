package site.addzero.i18n.buddy.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class I18nBuddySettingsConfigurable(private val project: Project) : Configurable {

    private var wrapperFunctionField: JBTextField? = null
    private var wrapperImportField: JBTextField? = null
    private var localeExprField: JBTextField? = null
    private var localeImportField: JBTextField? = null
    private var constantObjField: JBTextField? = null
    private var constantPkgField: JBTextField? = null
    private var constantModuleField: JBTextField? = null
    private var scanExtField: JBTextField? = null
    private var excludeField: JBTextField? = null
    private var callTemplateField: JBTextField? = null
    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "I18n Buddy"

    override fun createComponent(): JComponent {
        wrapperFunctionField = JBTextField(40).apply { toolTipText = "e.g. i18n" }
        wrapperImportField = JBTextField(40).apply { toolTipText = "e.g. com.example.i18n.i18n" }
        localeExprField = JBTextField(40).apply { toolTipText = "e.g. Settings.localLanguage" }
        localeImportField = JBTextField(40).apply { toolTipText = "e.g. com.example.settings.Settings" }
        constantObjField = JBTextField(40).apply { toolTipText = "e.g. I18nKeys" }
        constantPkgField = JBTextField(40).apply { toolTipText = "e.g. com.example.i18n" }
        constantModuleField = JBTextField(40).apply { toolTipText = "Gradle module path, empty = same module" }
        scanExtField = JBTextField(40).apply { toolTipText = "Comma-separated, e.g. kt,java" }
        excludeField = JBTextField(40).apply { toolTipText = "Glob patterns, comma-separated" }
        callTemplateField = JBTextField(40).apply { toolTipText = "{FN}({LOCALE}, {OBJ}.{KEY})" }

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Wrapper function:", wrapperFunctionField!!)
            .addLabeledComponent("Wrapper function import:", wrapperImportField!!)
            .addLabeledComponent("Locale expression:", localeExprField!!)
            .addLabeledComponent("Locale expression import:", localeImportField!!)
            .addLabeledComponent("Constant object name:", constantObjField!!)
            .addLabeledComponent("Constant package:", constantPkgField!!)
            .addLabeledComponent("Constant target module:", constantModuleField!!)
            .addLabeledComponent("Scan file extensions:", scanExtField!!)
            .addLabeledComponent("Exclude patterns:", excludeField!!)
            .addLabeledComponent("Call template:", callTemplateField!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val s = I18nBuddySettingsService.getInstance(project).state
        return wrapperFunctionField?.text != s.wrapperFunction
            || wrapperImportField?.text != s.wrapperFunctionImport
            || localeExprField?.text != s.localeExpression
            || localeImportField?.text != s.localeExpressionImport
            || constantObjField?.text != s.constantObjectName
            || constantPkgField?.text != s.constantPackage
            || constantModuleField?.text != s.constantModulePath
            || scanExtField?.text != s.scanFileExtensions
            || excludeField?.text != s.excludePatterns
            || callTemplateField?.text != s.callTemplate
    }

    override fun apply() {
        val s = I18nBuddySettingsService.getInstance(project).state
        s.wrapperFunction = wrapperFunctionField?.text?.trim() ?: s.wrapperFunction
        s.wrapperFunctionImport = wrapperImportField?.text?.trim() ?: s.wrapperFunctionImport
        s.localeExpression = localeExprField?.text?.trim() ?: s.localeExpression
        s.localeExpressionImport = localeImportField?.text?.trim() ?: s.localeExpressionImport
        s.constantObjectName = constantObjField?.text?.trim() ?: s.constantObjectName
        s.constantPackage = constantPkgField?.text?.trim() ?: s.constantPackage
        s.constantModulePath = constantModuleField?.text?.trim() ?: s.constantModulePath
        s.scanFileExtensions = scanExtField?.text?.trim() ?: s.scanFileExtensions
        s.excludePatterns = excludeField?.text?.trim() ?: s.excludePatterns
        s.callTemplate = callTemplateField?.text?.trim() ?: s.callTemplate
    }

    override fun reset() {
        val s = I18nBuddySettingsService.getInstance(project).state
        wrapperFunctionField?.text = s.wrapperFunction
        wrapperImportField?.text = s.wrapperFunctionImport
        localeExprField?.text = s.localeExpression
        localeImportField?.text = s.localeExpressionImport
        constantObjField?.text = s.constantObjectName
        constantPkgField?.text = s.constantPackage
        constantModuleField?.text = s.constantModulePath
        scanExtField?.text = s.scanFileExtensions
        excludeField?.text = s.excludePatterns
        callTemplateField?.text = s.callTemplate
    }

    override fun disposeUIResources() {
        wrapperFunctionField = null; wrapperImportField = null
        localeExprField = null; localeImportField = null
        constantObjField = null; constantPkgField = null
        constantModuleField = null; scanExtField = null
        excludeField = null; callTemplateField = null
        mainPanel = null
    }
}

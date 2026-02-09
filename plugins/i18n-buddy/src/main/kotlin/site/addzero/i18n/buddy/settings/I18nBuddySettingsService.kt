package site.addzero.i18n.buddy.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(
    name = "I18nBuddySettings",
    storages = [Storage("i18nBuddySettings.xml")]
)
class I18nBuddySettingsService : PersistentStateComponent<I18nBuddySettingsService.State> {

    data class State(
        /** The i18n wrapper function name, e.g. "i18n" */
        var wrapperFunction: String = "i18n",

        /** Import statement for the wrapper function, e.g. "com.example.i18n.i18n" */
        var wrapperFunctionImport: String = "",

        /** The locale parameter expression, e.g. "Settings.localLanguage" */
        var localeExpression: String = "Settings.localLanguage",

        /** Import statement for the locale expression, e.g. "com.example.settings.Settings" */
        var localeExpressionImport: String = "",

        /** The constant object name, e.g. "I18nKeys" */
        var constantObjectName: String = "I18nKeys",

        /** Package for the generated constant file, e.g. "com.example.i18n" */
        var constantPackage: String = "",

        /** Target module path for constants, empty = same module as source file */
        var constantModulePath: String = "",

        /** File extensions to scan, comma-separated */
        var scanFileExtensions: String = "kt",

        /** Exclude patterns (glob), comma-separated */
        var excludePatterns: String = "*Test.kt,*Spec.kt",

        /** Exclude patterns for string content (regex), one per line */
        var excludeContentPatterns: String = "^\\s*$\n^[a-zA-Z0-9_.\\-/]+$",

        /**
         * Call pattern template. Placeholders:
         * {FN} = wrapper function name
         * {LOCALE} = locale expression
         * {OBJ} = constant object name
         * {KEY} = generated key name
         */
        var callTemplate: String = "{FN}({LOCALE}, {OBJ}.{KEY})",
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    companion object {
        fun getInstance(project: Project): I18nBuddySettingsService = project.service()
    }
}

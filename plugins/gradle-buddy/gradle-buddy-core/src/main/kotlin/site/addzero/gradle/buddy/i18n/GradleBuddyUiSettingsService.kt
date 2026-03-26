package site.addzero.gradle.buddy.i18n

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.Locale

@Service(Service.Level.APP)
@State(
    name = "GradleBuddyUiSettings",
    storages = [Storage("gradleBuddyUiSettings.xml")]
)
class GradleBuddyUiSettingsService : PersistentStateComponent<GradleBuddyUiSettingsService.State> {

    data class State(
        var language: String = GradleBuddyLanguage.ZH.id
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    fun getLanguage(): GradleBuddyLanguage = GradleBuddyLanguage.fromId(myState.language)

    fun setLanguage(language: GradleBuddyLanguage) {
        myState.language = language.id
    }

    fun getLocale(): Locale = getLanguage().locale

    companion object {
        fun getInstance(): GradleBuddyUiSettingsService =
            ApplicationManager.getApplication().getService(GradleBuddyUiSettingsService::class.java)
    }
}

enum class GradleBuddyLanguage(
    val id: String,
    val locale: Locale
) {
    ZH("zh", Locale.SIMPLIFIED_CHINESE),
    EN("en", Locale.ENGLISH);

    companion object {
        fun fromId(id: String?): GradleBuddyLanguage {
            return entries.firstOrNull { it.id == id } ?: ZH
        }
    }
}

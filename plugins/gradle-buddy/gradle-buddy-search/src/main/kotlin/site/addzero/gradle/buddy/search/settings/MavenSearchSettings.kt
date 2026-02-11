package site.addzero.gradle.buddy.search.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Maven 搜索插件设置
 */
@State(
    name = "MavenSearchSettings",
    storages = [Storage("MavenSearchPlugin.xml")]
)
@Service(Service.Level.APP)
class MavenSearchSettings : PersistentStateComponent<MavenSearchSettings> {

    var pageSize: Int = 50
    var enablePagination: Boolean = true
    var autoCopyToClipboard: Boolean = true
    var searchTimeout: Int = 10
    var debounceDelay: Int = 500
    var requireManualTrigger: Boolean = false

    override fun getState(): MavenSearchSettings = this

    override fun loadState(state: MavenSearchSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val configDir: String
            get() = "${System.getProperty("user.home")}/.config/maven-buddy"

        fun getInstance(): MavenSearchSettings {
            return ApplicationManager.getApplication()
                .getService(MavenSearchSettings::class.java)
        }
    }
}

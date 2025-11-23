package site.addzero.maven.search.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import site.addzero.maven.search.DependencyFormat

/**
 * Maven 搜索插件设置
 */
@State(
    name = "MavenSearchSettings",
    storages = [Storage("MavenSearchPlugin.xml")]
)
class MavenSearchSettings : PersistentStateComponent<MavenSearchSettings> {

    /**
     * 依赖格式：Maven / Gradle Kotlin / Gradle Groovy
     */
    var dependencyFormat: DependencyFormat = DependencyFormat.GRADLE_KOTLIN

    /**
     * 搜索结果数量限制
     */
    var maxResults: Int = 20

    /**
     * 是否自动复制到剪贴板
     */
    var autoCopyToClipboard: Boolean = true

    /**
     * 搜索超时时间（秒）
     */
    var searchTimeout: Int = 10

    override fun getState(): MavenSearchSettings = this

    override fun loadState(state: MavenSearchSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): MavenSearchSettings {
            return ApplicationManager.getApplication()
                .getService(MavenSearchSettings::class.java)
        }
    }
}

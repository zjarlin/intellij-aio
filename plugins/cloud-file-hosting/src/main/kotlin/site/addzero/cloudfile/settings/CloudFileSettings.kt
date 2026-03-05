package site.addzero.cloudfile.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import site.addzero.cloudfile.util.SecureStorage

/**
 * Global settings for Cloud File Hosting
 * Application level - applies to all projects
 */
@Service(Service.Level.APP)
@State(
    name = "CloudFileSettings",
    storages = [
        Storage("cloud-file-hosting.xml")
    ]
)
class CloudFileSettings : PersistentStateComponent<CloudFileSettings.State> {

    private var state = State()

    data class State(
        @Tag("provider")
        var provider: StorageProvider = StorageProvider.S3,

        @Tag("s3Endpoint")
        var s3Endpoint: String = "",

        @Tag("s3Region")
        var s3Region: String = "us-east-1",

        @Tag("s3Bucket")
        var s3Bucket: String = "",

        @Tag("ossEndpoint")
        var ossEndpoint: String = "",

        @Tag("ossBucket")
        var ossBucket: String = "",

        @XCollection(style = XCollection.Style.v2, elementName = "globalRule")
        var globalRules: MutableList<HostingRule> = mutableListOf(),

        @XCollection(style = XCollection.Style.v2, elementName = "customRule")
        var customRules: MutableList<CustomHostingRule> = mutableListOf(),

        @Tag("autoSync")
        var autoSync: Boolean = true,

        @Tag("syncIntervalMinutes")
        var syncIntervalMinutes: Int = 5,

        @Tag("encryptLocalCache")
        var encryptLocalCache: Boolean = true,

        @Tag("lastSyncTimestamp")
        var lastSyncTimestamp: Long = 0L
    )

    enum class StorageProvider {
        S3, OSS
    }

    data class HostingRule(
        @Tag("pattern")
        var pattern: String = "",

        @Tag("type")
        var type: RuleType = RuleType.FILE,

        @Tag("enabled")
        var enabled: Boolean = true
    ) {
        enum class RuleType {
            FILE, DIRECTORY, GLOB
        }
    }

    data class CustomHostingRule(
        @Tag("gitAuthorPattern")
        var gitAuthorPattern: String = "",

        @Tag("projectNamePattern")
        var projectNamePattern: String = "",

        @XCollection(style = XCollection.Style.v2, elementName = "rule")
        var rules: MutableList<HostingRule> = mutableListOf(),

        @Tag("priority")
        var priority: Int = 0,

        @Tag("enabled")
        var enabled: Boolean = true
    )

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    fun addGlobalRule(pattern: String, type: HostingRule.RuleType) {
        // 检查是否已存在相同的规则（pattern 和 type 都相同）
        val exists = state.globalRules.any { it.pattern == pattern && it.type == type }
        if (!exists) {
            state.globalRules.add(HostingRule(pattern, type))
        }
    }

    fun removeGlobalRule(pattern: String) {
        state.globalRules.removeIf { it.pattern == pattern }
    }

    // Credentials are stored separately with encryption
    fun getS3AccessKey(): String? = SecureStorage.getSecureValue(KEY_S3_ACCESS_KEY)
    fun setS3AccessKey(value: String) = SecureStorage.setSecureValue(KEY_S3_ACCESS_KEY, value)

    fun getS3SecretKey(): String? = SecureStorage.getSecureValue(KEY_S3_SECRET_KEY)
    fun setS3SecretKey(value: String) = SecureStorage.setSecureValue(KEY_S3_SECRET_KEY, value)

    fun getOssAccessKeyId(): String? = SecureStorage.getSecureValue(KEY_OSS_ACCESS_KEY_ID)
    fun setOssAccessKeyId(value: String) = SecureStorage.setSecureValue(KEY_OSS_ACCESS_KEY_ID, value)

    fun getOssAccessKeySecret(): String? = SecureStorage.getSecureValue(KEY_OSS_ACCESS_KEY_SECRET)
    fun setOssAccessKeySecret(value: String) = SecureStorage.setSecureValue(KEY_OSS_ACCESS_KEY_SECRET, value)

    companion object {
        private const val KEY_S3_ACCESS_KEY = "cloudfile.s3.accessKey"
        private const val KEY_S3_SECRET_KEY = "cloudfile.s3.secretKey"
        private const val KEY_OSS_ACCESS_KEY_ID = "cloudfile.oss.accessKeyId"
        private const val KEY_OSS_ACCESS_KEY_SECRET = "cloudfile.oss.accessKeySecret"

        fun getInstance(): CloudFileSettings = ApplicationManager.getApplication().getService(CloudFileSettings::class.java)
    }
}

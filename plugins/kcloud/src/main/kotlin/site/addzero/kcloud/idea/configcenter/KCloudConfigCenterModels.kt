package site.addzero.kcloud.idea.configcenter

enum class KCloudConfigDomain {
    SYSTEM,
    BUSINESS,
}

enum class KCloudConfigValueType {
    STRING,
    BOOLEAN,
    INTEGER,
    NUMBER,
    JSON,
    TEXT,
}

enum class KCloudConfigStorageMode {
    REPO_PLAIN,
    REPO_ENCRYPTED,
    LOCAL_OVERRIDE,
}

data class KCloudConfigQuery(
    val namespace: String? = null,
    val domain: KCloudConfigDomain? = null,
    val profile: String = "default",
    val keyword: String? = null,
    val includeDisabled: Boolean = true,
)

data class KCloudConfigEntry(
    val id: String = "",
    val key: String = "",
    val namespace: String = "",
    val domain: KCloudConfigDomain = KCloudConfigDomain.SYSTEM,
    val profile: String = "default",
    val valueType: KCloudConfigValueType = KCloudConfigValueType.STRING,
    val storageMode: KCloudConfigStorageMode = KCloudConfigStorageMode.REPO_PLAIN,
    val value: String? = null,
    val description: String? = null,
    val enabled: Boolean = true,
    val decryptionAvailable: Boolean = true,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
)

data class KCloudConfigMutation(
    val id: String? = null,
    val key: String,
    val namespace: String,
    val domain: KCloudConfigDomain = KCloudConfigDomain.SYSTEM,
    val profile: String = "default",
    val valueType: KCloudConfigValueType = KCloudConfigValueType.STRING,
    val storageMode: KCloudConfigStorageMode = KCloudConfigStorageMode.REPO_PLAIN,
    val value: String,
    val description: String? = null,
    val enabled: Boolean = true,
)

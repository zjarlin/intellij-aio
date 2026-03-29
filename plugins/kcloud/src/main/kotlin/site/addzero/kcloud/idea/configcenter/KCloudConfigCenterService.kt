package site.addzero.kcloud.idea.configcenter

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service(Service.Level.APP)
class KCloudConfigCenterService {
    companion object {
        const val DB_PATH_KEY = "CONFIG_CENTER_DB_PATH"
        const val MASTER_KEY = "CONFIG_CENTER_MASTER_KEY"

        private val DEFAULT_DB_RELATIVE_PATHS = listOf(
            "config-center.sqlite",
            "apps/kcloud/config-center.sqlite",
        )
        private const val FALLBACK_DIRECTORY = ".kcloud"
        private const val FALLBACK_DATABASE = "config-center.sqlite"

        fun getInstance(): KCloudConfigCenterService {
            return com.intellij.openapi.application.ApplicationManager
                .getApplication()
                .getService(KCloudConfigCenterService::class.java)
        }
    }

    init {
        Class.forName("org.sqlite.JDBC")
    }

    fun resolveDatabaseFile(project: Project): File {
        val settingsPath = KCloudConfigCenterAppSettings.getInstance().sqlitePath
        if (settingsPath.isNotBlank()) {
            return File(settingsPath).absoluteFile
        }

        readSetting(DB_PATH_KEY)?.let { configured ->
            return File(configured).absoluteFile
        }

        locateProjectDatabase(project)?.let { projectDb ->
            return projectDb
        }

        return File(
            File(System.getProperty("user.home").orEmpty(), FALLBACK_DIRECTORY).apply { mkdirs() },
            FALLBACK_DATABASE,
        ).absoluteFile
    }

    fun listEntries(
        project: Project,
        query: KCloudConfigQuery,
    ): List<KCloudConfigEntry> {
        return withConnection(project) { connection ->
            ensureDatabaseReady(connection)
            val keyword = query.keyword?.trim()?.takeIf { it.isNotEmpty() }
            val sql = buildString {
                append("SELECT * FROM config_entry WHERE profile = ?")
                if (query.namespace != null) {
                    append(" AND namespace = ?")
                }
                if (query.domain != null) {
                    append(" AND domain = ?")
                }
                if (!query.includeDisabled) {
                    append(" AND enabled = 1")
                }
                if (keyword != null) {
                    append(" AND (key LIKE ? OR description LIKE ?)")
                }
                append(" ORDER BY namespace ASC, key ASC, updated_at DESC")
            }
            connection.prepareStatement(sql).use { statement ->
                var index = 1
                statement.setString(index++, query.profile.ifBlank { "default" })
                query.namespace?.let { statement.setString(index++, it) }
                query.domain?.let { statement.setString(index++, it.name) }
                if (keyword != null) {
                    val likeValue = "%$keyword%"
                    statement.setString(index++, likeValue)
                    statement.setString(index++, likeValue)
                }
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(resultSet.toEntry(project))
                        }
                    }
                }
            }
        }
    }

    fun saveEntry(
        project: Project,
        mutation: KCloudConfigMutation,
    ): KCloudConfigEntry {
        require(mutation.key.isNotBlank()) {
            "配置键不能为空"
        }
        require(mutation.namespace.isNotBlank()) {
            "项目/命名空间不能为空"
        }
        return withConnection(project) { connection ->
            ensureDatabaseReady(connection)
            val existingCreatedAt = mutation.id?.let { id ->
                connection.prepareStatement("SELECT created_at FROM config_entry WHERE id = ?").use { statement ->
                    statement.setString(1, id)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getLong("created_at")
                        } else {
                            null
                        }
                    }
                }
            }
            if (mutation.id != null) {
                connection.prepareStatement("DELETE FROM config_entry WHERE id = ?").use { statement ->
                    statement.setString(1, mutation.id)
                    statement.executeUpdate()
                }
            } else {
                deleteConflictingEntry(connection, mutation)
            }

            val now = System.currentTimeMillis()
            val entryId = mutation.id ?: UUID.randomUUID().toString()
            val cipherText = if (mutation.storageMode == KCloudConfigStorageMode.REPO_ENCRYPTED) {
                encrypt(project, mutation.value)
            } else {
                null
            }
            val plainText = if (mutation.storageMode == KCloudConfigStorageMode.REPO_ENCRYPTED) {
                null
            } else {
                mutation.value
            }

            connection.prepareStatement(
                """
                INSERT INTO config_entry (
                    id, key, namespace, domain, profile, value_type, storage_mode,
                    cipher_text, plain_text, description, tags_json, enabled, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '[]', ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, entryId)
                statement.setString(2, mutation.key.trim())
                statement.setString(3, mutation.namespace.trim())
                statement.setString(4, mutation.domain.name)
                statement.setString(5, mutation.profile.ifBlank { "default" })
                statement.setString(6, mutation.valueType.name)
                statement.setString(7, mutation.storageMode.name)
                statement.setString(8, cipherText)
                statement.setString(9, plainText)
                statement.setString(10, mutation.description?.trim()?.ifBlank { null })
                statement.setInt(11, if (mutation.enabled) 1 else 0)
                statement.setLong(12, existingCreatedAt ?: now)
                statement.setLong(13, now)
                statement.executeUpdate()
            }

            loadEntry(connection, project, entryId)
        }
    }

    fun deleteEntry(
        project: Project,
        entryId: String,
    ) {
        withConnection(project) { connection ->
            ensureDatabaseReady(connection)
            connection.prepareStatement("DELETE FROM config_entry WHERE id = ?").use { statement ->
                statement.setString(1, entryId)
                statement.executeUpdate()
            }
        }
    }

    private fun deleteConflictingEntry(
        connection: Connection,
        mutation: KCloudConfigMutation,
    ) {
        val sql = if (mutation.storageMode == KCloudConfigStorageMode.LOCAL_OVERRIDE) {
            """
            DELETE FROM config_entry
            WHERE namespace = ? AND key = ? AND profile = ? AND storage_mode = 'LOCAL_OVERRIDE'
            """.trimIndent()
        } else {
            """
            DELETE FROM config_entry
            WHERE namespace = ? AND key = ? AND profile = ?
              AND storage_mode IN ('REPO_PLAIN', 'REPO_ENCRYPTED')
            """.trimIndent()
        }
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, mutation.namespace.trim())
            statement.setString(2, mutation.key.trim())
            statement.setString(3, mutation.profile.ifBlank { "default" })
            statement.executeUpdate()
        }
    }

    private fun loadEntry(
        connection: Connection,
        project: Project,
        entryId: String,
    ): KCloudConfigEntry {
        connection.prepareStatement("SELECT * FROM config_entry WHERE id = ?").use { statement ->
            statement.setString(1, entryId)
            statement.executeQuery().use { resultSet ->
                require(resultSet.next()) {
                    "配置项写入后未找到记录"
                }
                return resultSet.toEntry(project)
            }
        }
    }

    private fun ensureDatabaseReady(connection: Connection) {
        splitSqlStatements(schemaSql()).forEach { statement ->
            connection.createStatement().use { jdbc ->
                jdbc.execute(statement)
            }
        }
    }

    private fun schemaSql(): String {
        return javaClass
            .getResource("/site/addzero/kcloud/idea/configcenter/kcloud-config-center-schema.sql")
            ?.readText()
            ?: error("缺少 kcloud 配置中心 schema")
    }

    private fun splitSqlStatements(rawSql: String): List<String> {
        return rawSql
            .lineSequence()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun <T> withConnection(
        project: Project,
        block: (Connection) -> T,
    ): T {
        val dbFile = resolveDatabaseFile(project)
        dbFile.parentFile?.mkdirs()
        return DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use(block)
    }

    private fun ResultSet.toEntry(project: Project): KCloudConfigEntry {
        val storageMode = KCloudConfigStorageMode.valueOf(getString("storage_mode"))
        val value = when (storageMode) {
            KCloudConfigStorageMode.REPO_ENCRYPTED -> {
                val cipherText = getString("cipher_text")
                if (cipherText.isNullOrBlank()) {
                    null
                } else {
                    runCatching { decrypt(project, cipherText) }.getOrNull()
                }
            }

            else -> getString("plain_text")
        }
        return KCloudConfigEntry(
            id = getString("id"),
            key = getString("key"),
            namespace = getString("namespace"),
            domain = KCloudConfigDomain.valueOf(getString("domain")),
            profile = getString("profile"),
            valueType = KCloudConfigValueType.valueOf(getString("value_type")),
            storageMode = storageMode,
            value = value,
            description = getString("description"),
            enabled = getInt("enabled") == 1,
            decryptionAvailable = storageMode != KCloudConfigStorageMode.REPO_ENCRYPTED || value != null,
            createdAtEpochMillis = getLong("created_at"),
            updatedAtEpochMillis = getLong("updated_at"),
        )
    }

    private fun locateProjectDatabase(project: Project): File? {
        val basePath = project.basePath ?: return null
        val projectRoot = File(basePath)
        var current: File? = projectRoot
        while (current != null) {
            DEFAULT_DB_RELATIVE_PATHS.forEach { relativePath ->
                val candidate = File(current, relativePath)
                if (candidate.exists()) {
                    return candidate.absoluteFile
                }
            }
            current = current.parentFile
        }
        return null
    }

    private fun readSetting(key: String): String? {
        return System.getProperty(key)
            ?.trim()
            ?.ifBlank { null }
            ?: System.getenv(key)
                ?.trim()
                ?.ifBlank { null }
    }

    private fun encrypt(
        project: Project,
        plainText: String,
    ): String {
        val keyBytes = resolvedKeyBytes(project)
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(128, iv),
        )
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val payload = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, payload, 0, iv.size)
        System.arraycopy(encrypted, 0, payload, iv.size, encrypted.size)
        return "v1:${Base64.getEncoder().encodeToString(payload)}"
    }

    private fun decrypt(
        project: Project,
        cipherText: String,
    ): String {
        val payload = cipherText.removePrefix("v1:").let { Base64.getDecoder().decode(it) }
        require(payload.size > 12) {
            "配置中心密文格式非法"
        }
        val iv = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(resolvedKeyBytes(project), "AES"),
            GCMParameterSpec(128, iv),
        )
        return cipher.doFinal(encrypted).toString(StandardCharsets.UTF_8)
    }

    private fun resolvedKeyBytes(
        project: Project,
    ): ByteArray {
        val masterKey = readSetting(MASTER_KEY)
            ?: error("缺少 CONFIG_CENTER_MASTER_KEY，无法处理加密配置")
        return MessageDigest.getInstance("SHA-256")
            .digest(masterKey.toByteArray(StandardCharsets.UTF_8))
            .copyOf(32)
    }
}

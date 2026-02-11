package site.addzero.gradle.buddy.search.cache

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import site.addzero.gradle.buddy.search.settings.MavenSearchSettings
import site.addzero.network.call.maven.util.MavenArtifact
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Maven 搜索结果缓存服务
 *
 * 全局存储：~/.config/maven-buddy/cache.db (SQLite)
 */
@Service(Service.Level.APP)
class SearchResultCacheService {

    private val logger = Logger.getInstance(SearchResultCacheService::class.java)
    private var connection: Connection? = null

    var maxCacheSize: Int = 500
    var cacheTtlMs: Long = 7 * 24 * 60 * 60 * 1000L
    var enableCache: Boolean = true

    init {
        initDatabase()
    }

    private fun getStoragePath(): String = "${MavenSearchSettings.configDir}/cache.db"

    private fun initDatabase() {
        runCatching { ensureConnection() }.onFailure { e ->
            logger.warn("Failed to init database", e)
        }
    }

    @Synchronized
    private fun ensureConnection(): Connection {
        if (connection?.isClosed != false) {
            val path = getStoragePath()
            File(path).parentFile?.mkdirs()
            Class.forName("org.sqlite.JDBC")
            connection = DriverManager.getConnection("jdbc:sqlite:$path")
            createTables()
        }
        return connection!!
    }


    private fun createTables() {
        connection?.createStatement()?.use { stmt ->
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS artifacts (
                    artifact_key TEXT PRIMARY KEY,
                    id TEXT,
                    group_id TEXT NOT NULL,
                    artifact_id TEXT NOT NULL,
                    version TEXT,
                    latest_version TEXT,
                    packaging TEXT DEFAULT 'jar',
                    timestamp INTEGER DEFAULT 0,
                    repository_id TEXT DEFAULT 'central',
                    cached_at INTEGER DEFAULT (strftime('%s', 'now') * 1000)
                )
            """.trimIndent())
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_group_id ON artifacts(group_id)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_artifact_id ON artifacts(artifact_id)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_cached_at ON artifacts(cached_at)")
        }
    }

    fun match(keyword: String, limit: Int = 50): List<MavenArtifact> {
        if (!enableCache || keyword.isBlank()) return emptyList()

        val lowerKeyword = "%${keyword.lowercase().trim()}%"
        val expireTime = System.currentTimeMillis() - cacheTtlMs

        return runCatching {
            val conn = ensureConnection()
            val sql = """
                SELECT id, group_id, artifact_id, version, latest_version, packaging, timestamp, repository_id
                FROM artifacts
                WHERE cached_at > ?
                  AND (LOWER(group_id) LIKE ? OR LOWER(artifact_id) LIKE ? OR LOWER(artifact_key) LIKE ?)
                ORDER BY timestamp DESC
                LIMIT ?
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, expireTime)
                stmt.setString(2, lowerKeyword)
                stmt.setString(3, lowerKeyword)
                stmt.setString(4, lowerKeyword)
                stmt.setInt(5, limit)

                val results = mutableListOf<MavenArtifact>()
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        results.add(MavenArtifact(
                            id = rs.getString("id") ?: "",
                            groupId = rs.getString("group_id"),
                            artifactId = rs.getString("artifact_id"),
                            version = rs.getString("version") ?: "",
                            latestVersion = rs.getString("latest_version") ?: "",
                            packaging = rs.getString("packaging") ?: "jar",
                            timestamp = rs.getLong("timestamp"),
                            repositoryId = rs.getString("repository_id") ?: "central"
                        ))
                    }
                }
                results
            }
        }.getOrElse { e ->
            logger.warn("Failed to match from cache", e)
            emptyList()
        }
    }

    fun addAll(artifacts: List<MavenArtifact>) {
        if (!enableCache || artifacts.isEmpty()) return

        runCatching {
            val conn = ensureConnection()
            val sql = """
                INSERT OR REPLACE INTO artifacts
                (artifact_key, id, group_id, artifact_id, version, latest_version, packaging, timestamp, repository_id, cached_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.autoCommit = false
            try {
                conn.prepareStatement(sql).use { stmt ->
                    val now = System.currentTimeMillis()
                    artifacts.forEach { artifact ->
                        stmt.setString(1, "${artifact.groupId}:${artifact.artifactId}")
                        stmt.setString(2, artifact.id)
                        stmt.setString(3, artifact.groupId)
                        stmt.setString(4, artifact.artifactId)
                        stmt.setString(5, artifact.version)
                        stmt.setString(6, artifact.latestVersion)
                        stmt.setString(7, artifact.packaging)
                        stmt.setLong(8, artifact.timestamp)
                        stmt.setString(9, artifact.repositoryId)
                        stmt.setLong(10, now)
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
                conn.commit()
                trimCache()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }.onFailure { e ->
            logger.warn("Failed to add artifacts to cache", e)
        }
    }

    fun hasMatch(keyword: String): Boolean {
        if (!enableCache || keyword.isBlank()) return false

        val lowerKeyword = "%${keyword.lowercase().trim()}%"
        val expireTime = System.currentTimeMillis() - cacheTtlMs

        return runCatching {
            val conn = ensureConnection()
            conn.prepareStatement("""
                SELECT 1 FROM artifacts
                WHERE cached_at > ? AND (LOWER(group_id) LIKE ? OR LOWER(artifact_id) LIKE ?)
                LIMIT 1
            """.trimIndent()).use { stmt ->
                stmt.setLong(1, expireTime)
                stmt.setString(2, lowerKeyword)
                stmt.setString(3, lowerKeyword)
                stmt.executeQuery().next()
            }
        }.getOrElse { false }
    }

    private fun trimCache() {
        runCatching {
            val conn = ensureConnection()
            val expireTime = System.currentTimeMillis() - cacheTtlMs
            conn.prepareStatement("DELETE FROM artifacts WHERE cached_at < ?").use { stmt ->
                stmt.setLong(1, expireTime)
                stmt.executeUpdate()
            }
            conn.prepareStatement("""
                DELETE FROM artifacts WHERE artifact_key NOT IN (
                    SELECT artifact_key FROM artifacts ORDER BY cached_at DESC LIMIT ?
                )
            """.trimIndent()).use { stmt ->
                stmt.setInt(1, maxCacheSize)
                stmt.executeUpdate()
            }
        }.onFailure { e ->
            logger.warn("Failed to trim cache", e)
        }
    }

    fun clearAll() {
        runCatching {
            ensureConnection().createStatement().use { it.executeUpdate("DELETE FROM artifacts") }
        }.onFailure { e ->
            logger.warn("Failed to clear cache", e)
        }
    }

    fun stats(): CacheStats {
        return runCatching {
            ensureConnection().createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM artifacts")
                val count = if (rs.next()) rs.getInt("cnt") else 0
                CacheStats(count, count)
            }
        }.getOrElse { CacheStats(0, 0) }
    }

    companion object {
        fun getInstance(): SearchResultCacheService =
            ApplicationManager.getApplication().getService(SearchResultCacheService::class.java)
    }
}

data class CacheStats(val totalEntries: Int, val totalArtifacts: Int)

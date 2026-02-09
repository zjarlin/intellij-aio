package site.addzero.gradle.buddy.buildlogic

import com.intellij.openapi.diagnostic.Logger

/**
 * 通过 Gradle Plugin Marker Artifact 机制，从 plugin id 反查真实实现工件坐标。
 *
 * 原理：
 * 当声明 `id("org.jetbrains.kotlin.jvm") version "2.0.0"` 时，
 * Gradle 会去仓库找 `org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:2.0.0` 的 POM，
 * POM 中的 `<dependency>` 就是真实实现工件（如 `org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0`）。
 *
 * 查询顺序：Gradle Plugin Portal → Maven Central
 */
object PluginMarkerResolver {

    private val LOG = Logger.getInstance(PluginMarkerResolver::class.java)

    /** 仓库列表，按优先级排序 */
    private val REPOSITORIES = listOf(
        "https://plugins.gradle.org/m2",
        "https://repo1.maven.org/maven2"
    )

    private const val CONNECT_TIMEOUT = 5000
    private const val READ_TIMEOUT = 5000

    /**
     * 解析结果
     */
    data class ResolvedArtifact(
        val groupId: String,
        val artifactId: String,
        val version: String
    ) {
        /** Maven 坐标 */
        val coordinate: String get() = "$groupId:$artifactId:$version"
    }

    /**
     * 查询插件 marker artifact 的最新版本（通过 maven-metadata.xml）
     *
     * @param pluginId 插件 ID
     * @return 最新版本号，null 表示未找到
     */
    fun resolveLatestVersion(pluginId: String): String? {
        for (repo in REPOSITORIES) {
            try {
                val groupPath = pluginId.replace('.', '/')
                val markerArtifactId = "$pluginId.gradle.plugin"
                val metadataUrl = "$repo/$groupPath/$markerArtifactId/maven-metadata.xml"
                val metadata = fetchUrl(metadataUrl) ?: continue

                // 优先取 <release>，其次 <latest>，最后 <version> 列表的最后一个
                val release = Regex("""<release>([^<]+)</release>""").find(metadata)?.groupValues?.get(1)?.trim()
                if (release != null) return release

                val latest = Regex("""<latest>([^<]+)</latest>""").find(metadata)?.groupValues?.get(1)?.trim()
                if (latest != null) return latest

                val versions = Regex("""<version>([^<]+)</version>""").findAll(metadata)
                    .map { it.groupValues[1].trim() }.toList()
                if (versions.isNotEmpty()) return versions.last()
            } catch (e: Exception) {
                LOG.debug("Failed to fetch metadata from $repo: ${e.message}")
            }
        }
        return null
    }

    /**
     * 根据 plugin id 和 version 解析真实实现工件
     *
     * @param pluginId 插件 ID，如 "org.jetbrains.kotlin.jvm"
     * @param version 插件版本，如 "2.0.0"
     * @return 解析到的实现工件，null 表示未找到
     */
    fun resolve(pluginId: String, version: String): ResolvedArtifact? {
        for (repo in REPOSITORIES) {
            try {
                val result = resolveFromRepo(repo, pluginId, version)
                if (result != null) {
                    LOG.info("Resolved plugin '$pluginId:$version' → ${result.coordinate} from $repo")
                    return result
                }
            } catch (e: Exception) {
                LOG.debug("Failed to resolve from $repo: ${e.message}")
            }
        }
        LOG.warn("Could not resolve plugin marker for '$pluginId:$version' from any repository")
        return null
    }

    /**
     * 从指定仓库解析 marker POM
     */
    private fun resolveFromRepo(repoUrl: String, pluginId: String, version: String): ResolvedArtifact? {
        // 构造 marker artifact POM URL
        // pluginId: org.jetbrains.kotlin.jvm
        // → path: org/jetbrains/kotlin/jvm/org.jetbrains.kotlin.jvm.gradle.plugin/2.0.0/org.jetbrains.kotlin.jvm.gradle.plugin-2.0.0.pom
        val groupPath = pluginId.replace('.', '/')
        val markerArtifactId = "$pluginId.gradle.plugin"
        val pomUrl = "$repoUrl/$groupPath/$markerArtifactId/$version/$markerArtifactId-$version.pom"

        val pomContent = fetchUrl(pomUrl) ?: return null
        return parsePomDependency(pomContent, version)
    }

    /**
     * HTTP GET 获取内容
     */
    private fun fetchUrl(url: String): String? {
        return try {
            val uri = java.net.URI(url)
            val connection = uri.toURL().openConnection()
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            val responseCode = (connection as? java.net.HttpURLConnection)?.responseCode ?: -1
            if (responseCode != 200) {
                return null
            }

            connection.getInputStream().bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            LOG.debug("HTTP fetch failed for $url: ${e.message}")
            null
        }
    }

    /**
     * 解析 POM XML，提取第一个 dependency 的 groupId:artifactId:version
     *
     * Marker POM 结构示例：
     * ```xml
     * <dependencies>
     *   <dependency>
     *     <groupId>org.jetbrains.kotlin</groupId>
     *     <artifactId>kotlin-gradle-plugin</artifactId>
     *     <version>2.0.0</version>
     *   </dependency>
     * </dependencies>
     * ```
     */
    private fun parsePomDependency(pomXml: String, fallbackVersion: String): ResolvedArtifact? {
        val groupIdPattern = Regex("""<dependency>\s*<groupId>([^<]+)</groupId>""", RegexOption.DOT_MATCHES_ALL)
        val artifactIdPattern = Regex("""<dependency>.*?<artifactId>([^<]+)</artifactId>""", RegexOption.DOT_MATCHES_ALL)
        val versionPattern = Regex("""<dependency>.*?<version>([^<]+)</version>""", RegexOption.DOT_MATCHES_ALL)

        val groupId = groupIdPattern.find(pomXml)?.groupValues?.get(1)?.trim() ?: return null
        val artifactId = artifactIdPattern.find(pomXml)?.groupValues?.get(1)?.trim() ?: return null
        val version = versionPattern.find(pomXml)?.groupValues?.get(1)?.trim() ?: fallbackVersion

        return ResolvedArtifact(groupId, artifactId, version)
    }
}

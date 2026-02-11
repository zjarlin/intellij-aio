package site.addzero.gradle.buddy.notification

import com.intellij.openapi.diagnostic.Logger
import java.net.HttpURLConnection
import java.net.URI

/**
 * 探测依赖在哪些常见 Maven 仓库中可用。
 *
 * 通过 HTTP HEAD 请求检查 POM 文件是否存在，
 * 用于在 Maven Central 找不到依赖时建议用户添加正确的仓库。
 */
object RepositoryProber {

    private val LOG = Logger.getInstance(RepositoryProber::class.java)

    /**
     * 已知的常见 Maven 仓库。
     * @param name 显示名称
     * @param url 仓库 URL（不带尾部斜杠）
     * @param ktsSnippet 写入 build.gradle.kts 的代码片段
     */
    data class KnownRepo(
        val name: String,
        val url: String,
        val ktsSnippet: String
    )

    /** 常见仓库列表（按优先级排序） */
    val KNOWN_REPOS = listOf(
        KnownRepo(
            "Google Maven",
            "https://dl.google.com/dl/android/maven2",
            "google()"
        ),
        KnownRepo(
            "JitPack",
            "https://jitpack.io",
            """maven("https://jitpack.io")"""
        ),
        KnownRepo(
            "Gradle Plugin Portal",
            "https://plugins.gradle.org/m2",
            "gradlePluginPortal()"
        ),
        KnownRepo(
            "JetBrains Compose",
            "https://maven.pkg.jetbrains.space/public/p/compose/dev",
            """maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")"""
        ),
        KnownRepo(
            "Sonatype Snapshots",
            "https://oss.sonatype.org/content/repositories/snapshots",
            """maven("https://oss.sonatype.org/content/repositories/snapshots")"""
        ),
        KnownRepo(
            "Sonatype Central Portal (s01)",
            "https://s01.oss.sonatype.org/content/repositories/releases",
            """maven("https://s01.oss.sonatype.org/content/repositories/releases")"""
        ),
        KnownRepo(
            "JetBrains Maven",
            "https://packages.jetbrains.team/maven/p/kpm/public",
            """maven("https://packages.jetbrains.team/maven/p/kpm/public")"""
        ),
        KnownRepo(
            "Kotlin Wasm Experimental",
            "https://maven.pkg.jetbrains.space/kotlin/p/wasm/experimental",
            """maven("https://maven.pkg.jetbrains.space/kotlin/p/wasm/experimental")"""
        )
    )

    /**
     * 探测依赖在哪些仓库中可用。
     * 通过 HEAD 请求检查 POM 文件是否存在。
     *
     * @return 找到该依赖的仓库列表
     */
    fun probe(groupId: String, artifactId: String, version: String): List<KnownRepo> {
        val path = groupId.replace('.', '/') + "/$artifactId/$version/$artifactId-$version.pom"
        return KNOWN_REPOS.filter { repo ->
            checkExists("${repo.url}/$path")
        }
    }

    /**
     * HTTP HEAD 检查 URL 是否返回 200。
     * 超时 5 秒，不跟随重定向到非 2xx。
     */
    private fun checkExists(url: String): Boolean {
        return try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = true
            try {
                conn.responseCode == 200
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            LOG.debug("Probe failed for $url: ${e.message}")
            false
        }
    }
}

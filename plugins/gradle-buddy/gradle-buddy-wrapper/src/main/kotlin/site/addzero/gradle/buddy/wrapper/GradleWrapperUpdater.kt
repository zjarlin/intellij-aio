package site.addzero.gradle.buddy.wrapper

import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.net.URI

/**
 * Gradle Wrapper 版本查询与 properties 文件更新工具。
 *
 * 通过 Gradle Services API 获取最新版本号，
 * 支持将 distributionUrl 替换为腾讯云/阿里云等国内镜像。
 */
object GradleWrapperUpdater {

    private val LOG = Logger.getInstance(GradleWrapperUpdater::class.java)

    /** Gradle 官方版本查询 API */
    private const val GRADLE_VERSIONS_CURRENT = "https://services.gradle.org/versions/current"

    /**
     * 已知的 Gradle 分发镜像模板。
     * `{version}` 和 `{type}` 会被替换。
     * type = "bin" | "all"
     */
    data class MirrorTemplate(
        val name: String,
        val urlTemplate: String
    )

    val MIRRORS = listOf(
        MirrorTemplate(
            "Tencent Cloud (腾讯云)",
            "https\\://mirrors.cloud.tencent.com/gradle/gradle-{version}-{type}.zip"
        ),
        MirrorTemplate(
            "Aliyun (阿里云)",
            "https\\://mirrors.aliyun.com/macports/distfiles/gradle/gradle-{version}-{type}.zip"
        ),
        MirrorTemplate(
            "Gradle Official",
            "https\\://services.gradle.org/distributions/gradle-{version}-{type}.zip"
        )
    )

    /** 默认使用腾讯云镜像 */
    val DEFAULT_MIRROR = MIRRORS[0]

    /**
     * 从 Gradle Services API 获取最新稳定版本号。
     * @return 版本号字符串，如 "9.3.1"；失败返回 null
     */
    fun fetchLatestVersion(): String? {
        return try {
            val conn = URI(GRADLE_VERSIONS_CURRENT).toURL().openConnection()
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val json = conn.getInputStream().bufferedReader().readText()
            // 简单提取 "version" : "x.y.z"
            val regex = Regex(""""version"\s*:\s*"([^"]+)"""")
            regex.find(json)?.groupValues?.get(1)
        } catch (e: Exception) {
            LOG.warn("Failed to fetch latest Gradle version: ${e.message}")
            null
        }
    }

    /**
     * 从 distributionUrl 中提取当前 Gradle 版本号。
     * 支持格式: `https\://xxx/gradle-9.2.1-bin.zip`
     */
    fun extractVersionFromUrl(distributionUrl: String): String? {
        val regex = Regex("""gradle-(\d+(?:\.\d+)+)-""")
        return regex.find(distributionUrl)?.groupValues?.get(1)
    }

    /**
     * 从 distributionUrl 中提取分发类型 (bin/all)。
     */
    fun extractTypeFromUrl(distributionUrl: String): String {
        return if (distributionUrl.contains("-all.zip")) "all" else "bin"
    }

    /**
     * 生成新的 distributionUrl。
     */
    fun buildDistributionUrl(version: String, type: String, mirror: MirrorTemplate = DEFAULT_MIRROR): String {
        return mirror.urlTemplate
            .replace("{version}", version)
            .replace("{type}", type)
    }

    /**
     * 查找项目中所有的 gradle-wrapper.properties 文件。
     * 从给定的根路径列表开始递归搜索。
     */
    fun findWrapperProperties(rootPaths: List<String>): List<File> {
        val result = mutableListOf<File>()
        val seen = mutableSetOf<String>()
        for (rootPath in rootPaths) {
            findWrapperPropertiesRecursive(File(rootPath), result, seen, 0, 6)
        }
        return result
    }

    private fun findWrapperPropertiesRecursive(
        dir: File, result: MutableList<File>, seen: MutableSet<String>,
        depth: Int, maxDepth: Int
    ) {
        if (depth > maxDepth || !dir.isDirectory) return
        val skipDirs = setOf("build", "out", ".gradle", ".idea", "node_modules", "target", ".git")
        if (dir.name in skipDirs) return

        val wrapperDir = File(dir, "gradle/wrapper")
        val propsFile = File(wrapperDir, "gradle-wrapper.properties")
        if (propsFile.exists() && seen.add(propsFile.canonicalPath)) {
            result.add(propsFile)
        }

        dir.listFiles()?.filter { it.isDirectory }?.forEach { child ->
            findWrapperPropertiesRecursive(child, result, seen, depth + 1, maxDepth)
        }
    }

    /**
     * 解析 gradle-wrapper.properties 中的 distributionUrl。
     */
    fun readDistributionUrl(propsFile: File): String? {
        if (!propsFile.exists()) return null
        return propsFile.readLines().firstOrNull { it.trimStart().startsWith("distributionUrl=") }
            ?.substringAfter("distributionUrl=")?.trim()
    }

    /**
     * 更新 gradle-wrapper.properties 中的 distributionUrl。
     * @return true 如果文件被修改
     */
    fun updateDistributionUrl(propsFile: File, newUrl: String): Boolean {
        if (!propsFile.exists()) return false
        val lines = propsFile.readLines()
        var changed = false
        val newLines = lines.map { line ->
            if (line.trimStart().startsWith("distributionUrl=")) {
                val oldUrl = line.substringAfter("distributionUrl=").trim()
                if (oldUrl != newUrl) {
                    changed = true
                    "distributionUrl=$newUrl"
                } else line
            } else line
        }
        if (changed) {
            propsFile.writeText(newLines.joinToString("\n") + "\n")
        }
        return changed
    }
}

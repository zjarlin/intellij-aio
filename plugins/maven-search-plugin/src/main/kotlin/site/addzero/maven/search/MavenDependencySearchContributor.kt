package site.addzero.maven.search

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.Processor
import com.intellij.util.TimeoutUtil
import site.addzero.maven.search.settings.MavenSearchSettings
import site.addzero.network.call.maven.util.MavenArtifact
import site.addzero.network.call.maven.util.MavenCentralSearchUtil
import javax.swing.ListCellRenderer

/**
 * Maven 依赖搜索贡献者 - 在 Search Everywhere (双击 Shift) 中搜索 Maven 依赖
 */
class MavenDependencySearchContributor(
    private val project: Project
) : SearchEverywhereContributor<MavenArtifact> {

    private val settings = MavenSearchSettings.getInstance()
    private val logger = Logger.getInstance(MavenDependencySearchContributor::class.java)

    override fun getSearchProviderId(): String = "MavenDependencySearch"

    override fun getGroupName(): String = "Maven Dependencies"

    override fun getSortWeight(): Int = 300

    override fun showInFindResults(): Boolean = false

    override fun processSelectedItem(
        selected: MavenArtifact,
        modifiers: Int,
        searchText: String
    ): Boolean {
        // 复制依赖声明到剪贴板
        val dependencyString = formatDependency(selected)
        copyToClipboard(dependencyString)

        // 显示通知
        showNotification(
            project,
            "Maven Dependency Copied",
            "Copied to clipboard: $dependencyString"
        )

        return true
    }

    override fun fetchElements(
        pattern: String,
        progressIndicator: ProgressIndicator,
        consumer: Processor<in MavenArtifact>
    ) {
        if (pattern.isBlank() || pattern.length < 2) return

        progressIndicator.isIndeterminate = true
        progressIndicator.text = "Maven Central"
        progressIndicator.text2 = "Preparing search request..."

        val cached = getCachedResults(pattern)
        if (cached != null) {
            progressIndicator.text2 = "Loaded cached results"
            deliverResults(cached, consumer, progressIndicator)
            progressIndicator.isIndeterminate = false
            progressIndicator.fraction = 0.0
            return
        }

        // 如果需要手动触发，只在用户明确按下 Enter 时才搜索
        // SearchEverywhere 框架在用户选择项目时会自动调用，无法直接区分是否按 Enter
        val delayMs = if (settings.requireManualTrigger) 1000L else settings.debounceDelay.toLong()

        if (!enforceRateLimit(progressIndicator)) {
            progressIndicator.isIndeterminate = false
            return
        }

        if (!waitForDebounce(delayMs, progressIndicator)) {
            progressIndicator.isIndeterminate = false
            progressIndicator.fraction = 0.0
            return
        }

        progressIndicator.isIndeterminate = true
        progressIndicator.text = "Searching Maven Central..."
        progressIndicator.text2 = "Waiting for Maven Central response..."

        try {
            val results = searchMavenArtifacts(pattern, progressIndicator)
            cacheResults(pattern, results)
            if (enableDebugLog) {
                logger.info("Maven Search: found ${results.size} results for '$pattern'")
            }
            progressIndicator.text2 = "Loaded ${results.size} results"
            deliverResults(results, consumer, progressIndicator)
        } catch (e: Exception) {
            logException("Maven search failed for pattern '$pattern'", e)
        } finally {
            progressIndicator.isIndeterminate = false
            progressIndicator.fraction = 0.0
        }
    }

    /**
     * 限流：在滑动窗口内最多允许固定数量的调用
     */
    private fun enforceRateLimit(progressIndicator: ProgressIndicator): Boolean {
        while (true) {
            val waitMs = globalRateLimiter.tryAcquire()
            if (waitMs <= 0L) {
                return true
            }

            if (enableDebugLog) {
                logger.info("Maven search rate limited, waiting ${waitMs}ms")
            }

            val waited = waitWithProgress(
                waitMs,
                progressIndicator
            ) { remaining -> "Rate limited. Retrying in ${remaining}ms..." }
            if (!waited) {
                progressIndicator.text2 = "Canceled while waiting for rate limiter"
                return false
            }
        }
    }

    /**
     * 等待防抖时间，同时保持可取消以确保 UI 有加载提示
     */
    private fun waitForDebounce(delayMs: Long, progressIndicator: ProgressIndicator): Boolean {
        if (delayMs <= 0) return true

        return waitWithProgress(delayMs, progressIndicator) { remaining ->
            if (settings.requireManualTrigger) {
                "Waiting for Enter (${remaining}ms)"
            } else {
                "Debouncing input... ${remaining}ms"
            }
        }
    }

    override fun getElementsRenderer(): ListCellRenderer<in MavenArtifact> {
        return MavenArtifactCellRenderer()
    }

    override fun getDataForItem(element: MavenArtifact, dataId: String): Any? = null

    override fun isShownInSeparateTab(): Boolean = true

    // ==================== 辅助方法 ====================

    /**
     * 搜索 Maven 工件
     *
     * 优先使用 searchByKeyword 方法进行搜索（最高优先级）
     * 使用 site.addzero:tool-api-maven 工具类搜索 Maven Central
     */
    private fun searchMavenArtifacts(
        pattern: String,
        progressIndicator: ProgressIndicator
    ): List<MavenArtifact> {
        progressIndicator.text = "Searching Maven Central..."

        return try {
            val maxResults = settings.maxResults

            // 优先使用关键词搜索（优先级最高）
            // searchByKeyword 支持所有类型的搜索模式：
            // - 简单关键词: "jackson", "guice"
            // - groupId: "com.google.guava"
            // - groupId:artifactId: "com.google.inject:guice"
            // - 完整坐标: "com.google.inject:guice:7.0.0"
            if (enableDebugLog) {
                println("Maven Search: searching by keyword '$pattern'")
                println("max results $maxResults")
            }
            val results = MavenCentralSearchUtil.searchByKeyword(pattern, maxResults)
            if (enableDebugLog) {
                println(results)
                println("Maven Search: found ${results.size} results for '$pattern'")
            }
            results
        } catch (e: Exception) {
            logException("Maven Central search failed for '$pattern'", e)
            emptyList()
        }
    }

    companion object {
        // 是否启用调试日志
        private const val enableDebugLog = true

        private val globalRateLimiter = SlidingWindowRateLimiter(maxRequests = 5, windowMillis = 2000)
        private const val cacheTtlMs = 2_000L
        @Volatile
        private var cachedResults: CachedResult? = null
    }

    private fun logException(message: String, throwable: Throwable) {
        logger.warn(message, throwable)
        throwable.printStackTrace()
    }

    private fun deliverResults(
        results: List<MavenArtifact>,
        consumer: Processor<in MavenArtifact>,
        progressIndicator: ProgressIndicator
    ) {
        for (artifact in results) {
            if (progressIndicator.isCanceled) {
                logger.info("Progress indicator canceled. Stopping delivery for pattern result ${artifact.id}")
                return
            }
            val accepted = consumer.process(artifact)
            if (!accepted) {
                logger.info("Search consumer stopped early while showing ${artifact.id}")
                return
            }
        }
    }

    private fun cacheResults(pattern: String, results: List<MavenArtifact>) {
        cachedResults = CachedResult(pattern, results, System.currentTimeMillis())
    }

    private fun getCachedResults(pattern: String): List<MavenArtifact>? {
        val cache = cachedResults ?: return null
        return if (cache.pattern == pattern && System.currentTimeMillis() - cache.timestamp <= cacheTtlMs) {
            cache.results
        } else {
            null
        }
    }

    private fun waitWithProgress(
        waitMs: Long,
        progressIndicator: ProgressIndicator,
        messageProvider: (remainingMs: Long) -> String
    ): Boolean {
        if (waitMs <= 0) return true

        val start = System.currentTimeMillis()
        val endTime = start + waitMs
        progressIndicator.isIndeterminate = false

        while (true) {
            if (progressIndicator.isCanceled) {
                return false
            }

            val now = System.currentTimeMillis()
            val remaining = endTime - now
            if (remaining <= 0) {
                break
            }

            val elapsed = now - start
            val fraction = (elapsed.toDouble() / waitMs).coerceIn(0.0, 1.0)
            progressIndicator.fraction = fraction
            progressIndicator.text2 = messageProvider(remaining)

            TimeoutUtil.sleep(50)
        }

        progressIndicator.fraction = 1.0
        return true
    }

    /**
     * 简单滑动窗口限流器
     */
    private data class CachedResult(
        val pattern: String,
        val results: List<MavenArtifact>,
        val timestamp: Long
    )

    private class SlidingWindowRateLimiter(
        private val maxRequests: Int,
        private val windowMillis: Long
    ) {
        private val timestamps: ArrayDeque<Long> = ArrayDeque()

        @Synchronized
        fun tryAcquire(): Long {
            val now = System.currentTimeMillis()
            while (timestamps.isNotEmpty() && now - timestamps.first() >= windowMillis) {
                timestamps.removeFirst()
            }

            return if (timestamps.size < maxRequests) {
                timestamps.addLast(now)
                0L
            } else {
                val earliest = timestamps.first()
                windowMillis - (now - earliest)
            }
        }
    }

    /**
     * 格式化依赖声明（根据设置选择 Maven 或 Gradle 格式）
     */
    private fun formatDependency(artifact: MavenArtifact): String {
        return when (settings.dependencyFormat) {
            DependencyFormat.MAVEN -> formatAsMaven(artifact)
            DependencyFormat.GRADLE_KOTLIN -> formatAsGradleKotlin(artifact)
            DependencyFormat.GRADLE_GROOVY -> formatAsGradleGroovy(artifact)
        }
    }

    /**
     * Maven 格式
     */
    private fun formatAsMaven(artifact: MavenArtifact): String {
        return """
<dependency>
    <groupId>${artifact.groupId}</groupId>
    <artifactId>${artifact.artifactId}</artifactId>
    <version>${artifact.latestVersion}</version>
</dependency>
        """.trimIndent()
    }

    /**
     * Gradle Kotlin DSL 格式
     */
    private fun formatAsGradleKotlin(artifact: MavenArtifact): String {
        return """implementation("${artifact.groupId}:${artifact.artifactId}:${artifact.latestVersion}")"""
    }

    /**
     * Gradle Groovy DSL 格式
     */
    private fun formatAsGradleGroovy(artifact: MavenArtifact): String {
        return """implementation '${artifact.groupId}:${artifact.artifactId}:${artifact.latestVersion}'"""
    }

    /**
     * 复制到剪贴板
     */
    private fun copyToClipboard(text: String) {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val stringSelection = java.awt.datatransfer.StringSelection(text)
        clipboard.setContents(stringSelection, null)
    }

    /**
     * 显示通知
     */
    private fun showNotification(project: Project, title: String, content: String) {
        val notification = com.intellij.notification.Notification(
            "MavenSearch",
            title,
            content,
            com.intellij.notification.NotificationType.INFORMATION
        )
        com.intellij.notification.Notifications.Bus.notify(notification, project)
    }
}

/**
 * Maven 依赖搜索贡献者工厂
 */
class MavenDependencySearchContributorFactory : SearchEverywhereContributorFactory<MavenArtifact> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<MavenArtifact> {
        return MavenDependencySearchContributor(initEvent.project!!)
    }
}

/**
 * 依赖格式枚举
 */
enum class DependencyFormat {
    MAVEN,
    GRADLE_KOTLIN,
    GRADLE_GROOVY
}

package site.addzero.maven.search

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.Processor
import com.intellij.util.TimeoutUtil
import site.addzero.maven.search.cache.SearchResultCacheService
import site.addzero.maven.search.detect.ProjectBuildTypeDetector
import site.addzero.maven.search.history.SearchHistoryService
import site.addzero.maven.search.settings.MavenSearchSettings
import site.addzero.network.call.maven.util.MavenArtifact
import site.addzero.network.call.maven.util.MavenCentralSearchUtil
import site.addzero.network.call.maven.util.MavenCentralPaginatedSearchUtil
import javax.swing.ListCellRenderer

/**
 * Maven 依赖搜索贡献者 - 在 Search Everywhere (双击 Shift) 中搜索 Maven 依赖
 */
class MavenDependencySearchContributor(
    private val project: Project
) : SearchEverywhereContributor<MavenArtifact> {

    private val settings = MavenSearchSettings.getInstance()
    private val historyService = SearchHistoryService.getInstance()
    private val cacheService = SearchResultCacheService.getInstance()
    private val logger = Logger.getInstance(MavenDependencySearchContributor::class.java)
    
    // 分页状态
    private var currentPage = 0
    private var lastSearchPattern = ""
    private var totalResultCount = 0
    private var allLoadedArtifacts = mutableListOf<MavenArtifact>()
    private var hasMoreResults = true
    private val paginationSessions = mutableMapOf<String, MavenCentralPaginatedSearchUtil.PaginatedSearchSession>()

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

        // 自动记录历史（使用 += 操作符自动去重）
        historyService.record(selected.groupId, selected.artifactId, selected.latestVersion.ifBlank { selected.version })
        historyService.record(searchText)

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
        if (pattern.isBlank() || pattern.length < 2) {
            resetPaginationState()
            // 显示最近使用的历史记录
            if (historyService.enableHistory) {
                deliverHistoryResults(consumer, progressIndicator)
            }
            return
        }

        // 检测搜索模式是否改变
        val isNewSearch = pattern != lastSearchPattern
        if (isNewSearch) {
            resetPaginationState()
            lastSearchPattern = pattern
        }

        progressIndicator.isIndeterminate = true
        progressIndicator.text = "Maven Central"
        progressIndicator.text2 = "Preparing search request..."

        // 如果没有更多结果且不是新搜索，返回已加载的全部结果
        if (!isNewSearch && !hasMoreResults) {
            progressIndicator.text2 = "All $totalResultCount results loaded"
            deliverResults(allLoadedArtifacts, consumer, progressIndicator)
            progressIndicator.isIndeterminate = false
            return
        }

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
        progressIndicator.text2 = "Page ${currentPage + 1}..."

        try {
            val results = when {
                settings.enablePagination -> searchMavenArtifactsWithPagination(pattern, progressIndicator)
                else -> searchMavenArtifacts(pattern, progressIndicator)
            }
            
            // 按 timestamp 降序排序
            val sortedResults = results.sortedByDescending { it.timestamp }
            
            allLoadedArtifacts.addAll(sortedResults)
            
            // 更新持久化缓存（包含所有已加载的结果）
            if (allLoadedArtifacts.isNotEmpty()) {
                cacheService[pattern] = allLoadedArtifacts.toList()
            }
            
            // 判断是否还有更多结果
            hasMoreResults = results.isNotEmpty() && 
                settings.enablePagination && 
                allLoadedArtifacts.size < totalResultCount
            
            if (enableDebugLog) {
                logger.info("Maven Search: loaded ${results.size} results for '$pattern' (page $currentPage, total: $totalResultCount)")
            }
            
            val statusText = buildString {
                append("🔍 ${allLoadedArtifacts.size}")
                if (totalResultCount > 0) {
                    append(" / $totalResultCount")
                }
                if (hasMoreResults) {
                    append(" ↓ scroll for more")
                }
            }
            progressIndicator.text2 = statusText
            
            // 返回所有已加载的结果（Search Everywhere 每次调用会刷新列表）
            deliverResults(allLoadedArtifacts, consumer, progressIndicator)
            
            // 准备下一页
            if (settings.enablePagination) {
                currentPage++
            }
        } catch (e: Exception) {
            logException("Maven search failed for pattern '$pattern'", e)
        } finally {
            progressIndicator.isIndeterminate = false
            progressIndicator.fraction = 0.0
        }
    }
    
    /**
     * 重置分页状态
     */
    private fun resetPaginationState() {
        currentPage = 0
        lastSearchPattern = ""
        totalResultCount = 0
        allLoadedArtifacts.clear()
        hasMoreResults = true
        paginationSessions.clear()
    }

    /**
     * 显示历史记录结果（按 groupId:artifactId 去重）
     */
    private fun deliverHistoryResults(
        consumer: Processor<in MavenArtifact>,
        progressIndicator: ProgressIndicator
    ) {
        // 获取更多历史记录（已按 groupId:artifactId 去重）
        val recentArtifacts = historyService.recentArtifacts(30)
        if (recentArtifacts.isEmpty()) {
            progressIndicator.text = "No History"
            progressIndicator.text2 = "Search to add dependencies"
            return
        }
        
        progressIndicator.text = "📜 Recent Dependencies"
        progressIndicator.text2 = "${recentArtifacts.size} items (click to copy)"
        
        recentArtifacts
            .takeWhile { !progressIndicator.isCanceled }
            .map { entry ->
                MavenArtifact(
                    id = entry.key,
                    groupId = entry.groupId,
                    artifactId = entry.artifactId,
                    version = entry.version,
                    latestVersion = entry.version,
                    packaging = "jar",
                    timestamp = entry.timestamp,
                    repositoryId = "history"
                )
            }
            .forEach { consumer.process(it) }
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
     * 分页搜索 Maven 工件
     */
    private fun searchMavenArtifactsWithPagination(
        pattern: String,
        progressIndicator: ProgressIndicator
    ): List<MavenArtifact> {
        progressIndicator.text = "Searching Maven Central..."

        return runCatching {
            val pageSize = settings.pageSize
            
            if (enableDebugLog) {
                println("Maven Search (paginated): searching '$pattern' page $currentPage, size $pageSize")
            }
            
            // 创建或复用搜索会话
            val session = paginationSessions.getOrPut(pattern) {
                MavenCentralPaginatedSearchUtil.searchByKeywordPaginated(
                    keyword = pattern,
                    pageSize = pageSize
                )
            }
            
            val paginatedResult = session.loadNextPage()
            
            // 更新总结果数
            totalResultCount = paginatedResult.totalResults.toInt()
            
            if (enableDebugLog) {
                println("Maven Search (paginated): found ${paginatedResult.artifacts.size} results for page $currentPage")
                println("Total: $totalResultCount, hasMore: ${paginatedResult.hasMore}")
            }
            
            paginatedResult.artifacts
        }.getOrElse { e ->
            logException("Maven Central paginated search failed for '$pattern'", e)
            emptyList()
        }
    }

    /**
     * 搜索 Maven 工件（非分页模式）
     *
     * 优先使用 searchByKeyword 方法进行搜索（最高优先级）
     * 使用 site.addzero:tool-api-maven 工具类搜索 Maven Central
     */
    private fun searchMavenArtifacts(
        pattern: String,
        progressIndicator: ProgressIndicator
    ): List<MavenArtifact> {
        progressIndicator.text = "Searching Maven Central..."

        return runCatching {
            @Suppress("DEPRECATION")
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
        }.getOrElse { e ->
            logException("Maven Central search failed for '$pattern'", e)
            emptyList()
        }
    }

    companion object {
        // 是否启用调试日志
        private const val enableDebugLog = false

        private val globalRateLimiter = SlidingWindowRateLimiter(maxRequests = 5, windowMillis = 2000)
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
     * 格式化依赖声明（自动根据项目类型选择格式）
     */
    private fun formatDependency(artifact: MavenArtifact): String {
        val format = ProjectBuildTypeDetector.detect(project)
        return when (format) {
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

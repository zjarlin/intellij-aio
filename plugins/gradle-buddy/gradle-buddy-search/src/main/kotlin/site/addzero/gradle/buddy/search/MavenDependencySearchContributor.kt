package site.addzero.gradle.buddy.search

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.Processor
import com.intellij.util.TimeoutUtil
import site.addzero.gradle.buddy.search.cache.SearchResultCacheService
import site.addzero.gradle.buddy.search.detect.ProjectBuildTypeDetector
import site.addzero.gradle.buddy.search.history.SearchHistoryService
import site.addzero.gradle.buddy.search.settings.MavenSearchSettings
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
        val dialog = VersionSelectionDialog(project, selected)
        if (!dialog.showAndGet()) return true

        val selectedVersion = dialog.getSelectedVersion() ?: selected.latestVersion
        val artifactWithVersion = selected.copy(latestVersion = selectedVersion)
        val dependencyString = formatDependency(artifactWithVersion)
        copyToClipboard(dependencyString)

        historyService.record(selected.groupId, selected.artifactId, selectedVersion)
        historyService.record(searchText)

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
            if (historyService.enableHistory) {
                deliverHistoryResults(consumer, progressIndicator)
            }
            return
        }

        val isNewSearch = pattern != lastSearchPattern
        if (isNewSearch) {
            resetPaginationState()
            lastSearchPattern = pattern
        }

        progressIndicator.isIndeterminate = true
        progressIndicator.text = "Maven Central"
        progressIndicator.text2 = "Preparing search request..."

        if (!isNewSearch && !hasMoreResults) {
            progressIndicator.text2 = "All $totalResultCount results loaded"
            deliverResults(allLoadedArtifacts, consumer, progressIndicator)
            progressIndicator.isIndeterminate = false
            return
        }

        if (isNewSearch && currentPage == 0 && cacheService.enableCache) {
            val cachedResults = cacheService.match(pattern, limit = 50)
            if (cachedResults.isNotEmpty()) {
                progressIndicator.text = "💾 From Cache"
                progressIndicator.text2 = "${cachedResults.size} cached results"

                val cachedWithMark = cachedResults.map { artifact ->
                    MavenArtifact(
                        id = artifact.id,
                        groupId = artifact.groupId,
                        artifactId = artifact.artifactId,
                        version = artifact.version,
                        latestVersion = artifact.latestVersion,
                        packaging = artifact.packaging,
                        timestamp = artifact.timestamp,
                        repositoryId = "cached"
                    )
                }

                deliverResults(cachedWithMark, consumer, progressIndicator)
            }
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

            val sortedResults = results.sortedByDescending { it.timestamp }

            allLoadedArtifacts.addAll(sortedResults)

            if (allLoadedArtifacts.isNotEmpty()) {
                cacheService.addAll(allLoadedArtifacts)
            }

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

            deliverResults(allLoadedArtifacts, consumer, progressIndicator)

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

    private fun resetPaginationState() {
        currentPage = 0
        lastSearchPattern = ""
        totalResultCount = 0
        allLoadedArtifacts.clear()
        hasMoreResults = true
        paginationSessions.clear()
    }

    private fun deliverHistoryResults(
        consumer: Processor<in MavenArtifact>,
        progressIndicator: ProgressIndicator
    ) {
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

            val session = paginationSessions.getOrPut(pattern) {
                MavenCentralPaginatedSearchUtil.searchByKeywordPaginated(
                    keyword = pattern,
                    pageSize = pageSize
                )
            }

            val paginatedResult = session.loadNextPage()

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

    private fun searchMavenArtifacts(
        pattern: String,
        progressIndicator: ProgressIndicator
    ): List<MavenArtifact> {
        progressIndicator.text = "Searching Maven Central..."

        return runCatching {
            val maxResults = settings.pageSize

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

    private fun formatDependency(artifact: MavenArtifact): String {
        val format = ProjectBuildTypeDetector.detect(project)
        return when (format) {
            DependencyFormat.MAVEN -> formatAsMaven(artifact)
            DependencyFormat.GRADLE_KOTLIN -> formatAsGradleKotlin(artifact)
            DependencyFormat.GRADLE_GROOVY -> formatAsGradleGroovy(artifact)
        }
    }

    private fun formatAsMaven(artifact: MavenArtifact): String {
        return """
<dependency>
    <groupId>${artifact.groupId}</groupId>
    <artifactId>${artifact.artifactId}</artifactId>
    <version>${artifact.latestVersion}</version>
</dependency>
        """.trimIndent()
    }

    private fun formatAsGradleKotlin(artifact: MavenArtifact): String {
        return """implementation("${artifact.groupId}:${artifact.artifactId}:${artifact.latestVersion}")"""
    }

    private fun formatAsGradleGroovy(artifact: MavenArtifact): String {
        return """implementation '${artifact.groupId}:${artifact.artifactId}:${artifact.latestVersion}'"""
    }

    private fun copyToClipboard(text: String) {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val stringSelection = java.awt.datatransfer.StringSelection(text)
        clipboard.setContents(stringSelection, null)
    }

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

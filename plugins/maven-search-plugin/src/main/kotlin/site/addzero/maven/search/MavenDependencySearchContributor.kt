package site.addzero.maven.search

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.Processor
import com.intellij.util.concurrency.AppExecutorUtil
import site.addzero.maven.search.settings.MavenSearchSettings
import site.addzero.network.call.maven.util.MavenArtifact
import site.addzero.network.call.maven.util.MavenCentralSearchUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.ListCellRenderer

/**
 * Maven 依赖搜索贡献者 - 在 Search Everywhere (双击 Shift) 中搜索 Maven 依赖
 */
class MavenDependencySearchContributor(
    private val project: Project
) : SearchEverywhereContributor<MavenArtifact> {

    private val settings = MavenSearchSettings.getInstance()
    
    // 防抖定时器
    private val scheduledFuture = AtomicReference<ScheduledFuture<*>?>()
    
    // 上次搜索的关键词
    @Volatile
    private var lastSearchPattern = ""

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

        // 如果需要手动触发，只在用户明确按下 Enter 时才搜索
        // SearchEverywhere 框架在用户选择项目时会自动调用，无法直接区分是否按 Enter
        // 因此，我们使用防抖逻辑来避免立即触发
        if (settings.requireManualTrigger) {
            // 手动模式：仅在输入完成时触发（延迟较长）
            performDebouncedSearch(pattern, progressIndicator, consumer, 1000)
        } else {
            // 自动模式：使用配置的防抖延迟
            performDebouncedSearch(pattern, progressIndicator, consumer, settings.debounceDelay.toLong())
        }
    }
    
    /**
     * 执行防抖搜索
     */
    private fun performDebouncedSearch(
        pattern: String,
        progressIndicator: ProgressIndicator,
        consumer: Processor<in MavenArtifact>,
        delayMs: Long
    ) {
        // 取消之前的搜索任务
        scheduledFuture.get()?.cancel(false)
        
        // 更新最后搜索关键词
        lastSearchPattern = pattern
        
        // 调度新的搜索任务
        val future = AppExecutorUtil.getAppScheduledExecutorService().schedule({
            // 检查是否已被更新的搜索替代
            if (lastSearchPattern == pattern && !progressIndicator.isCanceled) {
                try {
                    val results = searchMavenArtifacts(pattern, progressIndicator)
                    
                    // 在后台线程处理结果
                    ApplicationManager.getApplication().executeOnPooledThread {
                        try {
                            for (artifact in results) {
                                if (progressIndicator.isCanceled) break
                                consumer.process(artifact)
                            }
                        } catch (e: Exception) {
                            if (enableDebugLog) {
                                println("Maven search result processing failed: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (enableDebugLog) {
                        println("Maven search failed: ${e.message}")
                    }
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS)
        
        scheduledFuture.set(future)
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
            val results = MavenCentralSearchUtil.searchByKeyword(pattern, maxResults)
            
            if (enableDebugLog) {
                println("Maven Search: found ${results.size} results for '$pattern'")
            }
            
            results
        } catch (e: Exception) {
            if (enableDebugLog) {
                println("Maven Central search failed: ${e.message}")
                e.printStackTrace()
            }
            emptyList()
        }
    }
    
    companion object {
        // 是否启用调试日志
        private const val enableDebugLog = false
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

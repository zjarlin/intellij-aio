package site.addzero.maven.search

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.Processor
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

        try {
            // 在后台线程执行搜索
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val results = searchMavenArtifacts(pattern, progressIndicator)
                    
                    for (artifact in results) {
                        if (progressIndicator.isCanceled) break
                        consumer.process(artifact)
                    }
                } catch (e: Exception) {
                    // 搜索失败时静默处理
                    println("Maven search failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("Failed to start Maven search: ${e.message}")
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
     * 使用 site.addzero:tool-api-maven 工具类搜索 Maven Central
     */
    private fun searchMavenArtifacts(
        pattern: String,
        progressIndicator: ProgressIndicator
    ): List<MavenArtifact> {
        progressIndicator.text = "Searching Maven Central..."
        
        return try {
            val maxResults = settings.maxResults
            
            // 智能搜索：如果包含 ':' 则按坐标搜索，否则按关键词搜索
            val results = if (pattern.contains(':')) {
                val parts = pattern.split(':', limit = 3)
                when (parts.size) {
                    1 -> {
                        // 只有 groupId
                        MavenCentralSearchUtil.searchByGroupId(parts[0], maxResults)
                    }
                    2 -> {
                        // groupId:artifactId
                        MavenCentralSearchUtil.searchByCoordinates(parts[0], parts[1], maxResults)
                    }
                    else -> {
                        // 包含版本号，按关键词搜索
                        MavenCentralSearchUtil.searchByKeyword(pattern, maxResults)
                    }
                }
            } else {
                // 按关键词搜索 (类似单测中的用法)
                MavenCentralSearchUtil.searchByKeyword(pattern, maxResults)
            }
            
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

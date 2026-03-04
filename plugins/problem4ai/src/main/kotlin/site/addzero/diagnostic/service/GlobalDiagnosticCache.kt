package site.addzero.diagnostic.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.diagnostic.config.DiagnosticExclusionConfig
import site.addzero.diagnostic.model.DiagnosticItem
import site.addzero.diagnostic.model.FileDiagnostics
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 全局诊断缓存服务
 *
 * 采用饿汉式策略：项目启动后立即进行全量扫描
 * 缓存所有文件的诊断信息，提供快速查询接口
 *
 * 特性：
 * - 启动时全量扫描
 * - 实时监听变化更新缓存
 * - 线程安全的缓存访问
 * - 提供 VirtualFile 扩展函数
 */
@Service(Service.Level.PROJECT)
class GlobalDiagnosticCache(private val project: Project) : Disposable {

    // 诊断缓存: VirtualFile -> FileDiagnostics
    private val diagnosticsCache = ConcurrentHashMap<VirtualFile, FileDiagnostics>()

    // 是否已完成初始扫描
    private val initialized = AtomicBoolean(false)

    // 监听器列表
    private val listeners = mutableListOf<CacheUpdateListener>()

    // 排除配置
    private val exclusionConfig: DiagnosticExclusionConfig by lazy {
        DiagnosticExclusionConfig.getInstance(project)
    }

    companion object {
        fun getInstance(project: Project): GlobalDiagnosticCache =
            project.getService(GlobalDiagnosticCache::class.java)
    }

    init {
        // 监听诊断收集服务的更新
        DiagnosticCollectorService.getInstance(project).addListener { diagnostics ->
            updateCache(diagnostics)
        }
    }

    /**
     * 执行全量扫描（饿汉式初始化）
     */
    fun performFullScan() {
        if (DumbService.getInstance(project).isDumb) {
            // 等待索引完成后再扫描
            DumbService.getInstance(project).runWhenSmart {
                doFullScan()
            }
        } else {
            doFullScan()
        }
    }

    private fun doFullScan() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Problem4AI 扫描项目问题...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = "正在初始化扫描..."

                try {
                    val collector = DiagnosticCollectorService.getInstance(project)
                    val diagnostics = ReadAction.compute<List<FileDiagnostics>, Throwable> {
                        collector.collectAllDiagnosticsWithProgress(indicator)
                    }

                    // 过滤并缓存
                    val filteredDiagnostics = diagnostics.filter { fileDiag ->
                        !exclusionConfig.isExcluded(fileDiag.file)
                    }

                    diagnosticsCache.clear()
                    filteredDiagnostics.forEach { fileDiag ->
                        diagnosticsCache[fileDiag.file] = fileDiag
                    }
                    initialized.set(true)

                    indicator.text = "扫描完成，发现 ${filteredDiagnostics.size} 个文件有问题"
                    notifyListeners(CacheUpdateEvent.FullScan(filteredDiagnostics))
                } catch (e: Exception) {
                    indicator.text = "扫描失败: ${e.message}"
                    notifyListeners(CacheUpdateEvent.FullScan(emptyList()))
                }
            }
        })
    }

    /**
     * 更新缓存
     */
    private fun updateCache(diagnostics: List<FileDiagnostics>) {
        // 过滤掉被排除的文件
        val filteredDiagnostics = diagnostics.filter { fileDiag ->
            !exclusionConfig.isExcluded(fileDiag.file)
        }

        // 清除不再有问题的文件
        val currentFiles = filteredDiagnostics.map { it.file }.toSet()
        diagnosticsCache.keys.retainAll(currentFiles)

        // 更新或添加有问题的文件
        filteredDiagnostics.forEach { fileDiag ->
            diagnosticsCache[fileDiag.file] = fileDiag
        }

        notifyListeners(CacheUpdateEvent.IncrementalUpdate(filteredDiagnostics))
    }

    /**
     * 获取指定文件的诊断信息
     */
    fun getDiagnostics(file: VirtualFile): FileDiagnostics? {
        return diagnosticsCache[file]
    }

    /**
     * 获取所有诊断信息
     */
    fun getAllDiagnostics(): List<FileDiagnostics> {
        return diagnosticsCache.values.toList()
    }

    /**
     * 获取所有错误文件
     */
    fun getErrorFiles(): List<FileDiagnostics> {
        return diagnosticsCache.values.filter { it.hasErrors }.toList()
    }

    /**
     * 获取所有警告文件
     */
    fun getWarningFiles(): List<FileDiagnostics> {
        return diagnosticsCache.values.filter { it.hasWarnings }.toList()
    }

    /**
     * 检查文件是否有问题
     */
    fun hasProblems(file: VirtualFile): Boolean {
        return diagnosticsCache.containsKey(file)
    }

    /**
     * 检查文件是否有错误
     */
    fun hasErrors(file: VirtualFile): Boolean {
        return diagnosticsCache[file]?.hasErrors ?: false
    }

    /**
     * 检查文件是否有警告
     */
    fun hasWarnings(file: VirtualFile): Boolean {
        return diagnosticsCache[file]?.hasWarnings ?: false
    }

    /**
     * 获取项目问题统计
     */
    fun getStatistics(): DiagnosticStatistics {
        val allFiles = diagnosticsCache.values.toList()
        val errorCount = allFiles.sumOf { it.items.count { item -> item.severity == site.addzero.diagnostic.model.DiagnosticSeverity.ERROR } }
        val warningCount = allFiles.sumOf { it.items.count { item -> item.severity == site.addzero.diagnostic.model.DiagnosticSeverity.WARNING } }

        return DiagnosticStatistics(
            totalFiles = allFiles.size,
            errorFiles = allFiles.count { it.hasErrors },
            warningFiles = allFiles.count { it.hasWarnings },
            totalErrors = errorCount,
            totalWarnings = warningCount
        )
    }

    /**
     * 添加缓存更新监听器
     */
    fun addListener(listener: CacheUpdateListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    /**
     * 移除缓存更新监听器
     */
    fun removeListener(listener: CacheUpdateListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyListeners(event: CacheUpdateEvent) {
        val listenersCopy = synchronized(listeners) { listeners.toList() }
        ApplicationManager.getApplication().invokeLater {
            listenersCopy.forEach { it.onCacheUpdate(event) }
        }
    }

    /**
     * 是否已完成初始化
     */
    fun isInitialized(): Boolean = initialized.get()

    override fun dispose() {
        diagnosticsCache.clear()
        listeners.clear()
    }
}

/**
 * 诊断统计信息
 */
data class DiagnosticStatistics(
    val totalFiles: Int,
    val errorFiles: Int,
    val warningFiles: Int,
    val totalErrors: Int,
    val totalWarnings: Int
)

/**
 * 缓存更新事件
 */
sealed class CacheUpdateEvent {
    data class FullScan(val diagnostics: List<FileDiagnostics>) : CacheUpdateEvent()
    data class IncrementalUpdate(val diagnostics: List<FileDiagnostics>) : CacheUpdateEvent()
}

/**
 * 缓存更新监听器
 */
fun interface CacheUpdateListener {
    fun onCacheUpdate(event: CacheUpdateEvent)
}

/**
 * 项目启动时触发全量扫描
 */
class GlobalDiagnosticCacheInitializer : ProjectActivity {
    override suspend fun execute(project: Project) {
        // 先自动加载.gitignore排除规则
        val exclusionConfig = DiagnosticExclusionConfig.getInstance(project)
        exclusionConfig.loadFromGitignore(project)

        // 等待索引完成后执行扫描
        DumbService.getInstance(project).runWhenSmart {
            GlobalDiagnosticCache.getInstance(project).performFullScan()
        }
    }
}

package site.addzero.diagnostic.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import site.addzero.diagnostic.config.DiagnosticExclusionConfig
import site.addzero.diagnostic.model.FileDiagnostics
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 全局诊断缓存 - 包装层
 *
 * 实际缓存存储在 DiagnosticCollectorService 中
 * 此类提供方便的查询接口和监听机制
 */
@Service(Service.Level.PROJECT)
class GlobalDiagnosticCache(private val project: Project) : Disposable {
    companion object {
        private val LOG: Logger = Logger.getInstance(GlobalDiagnosticCache::class.java)

        fun getInstance(project: Project): GlobalDiagnosticCache =
            project.getService(GlobalDiagnosticCache::class.java)
    }

    private val collectorService = DiagnosticCollectorService.getInstance(project)
    private val listeners = CopyOnWriteArrayList<CacheUpdateListener>()

    init {
        // 监听收集服务的更新
        collectorService.addListener { diagnostics ->
            LOG.debug("[Problem4AI][Cache] collector update diagnostics=${diagnostics.size}")
            notifyListeners(CacheUpdateEvent.Update(diagnostics))
        }
    }

    /**
     * 执行全量扫描（由 Initializer 在项目启动时调用）
     */
    fun performFullScan() {
        LOG.info("[Problem4AI][Cache] performFullScan called")
        collectorService.performFullScan()
    }

    /**
     * 获取指定文件的诊断信息
     * @return 如果有问题返回 FileDiagnostics，如果无问题返回 null，如果未扫描也返回 null
     */
    fun getDiagnostics(file: VirtualFile): FileDiagnostics? {
        return collectorService.getDiagnostics(file)
    }

    /**
     * 获取所有有问题的诊断
     */
    fun getAllDiagnostics(): List<FileDiagnostics> {
        return collectorService.getAllProblemDiagnostics()
    }

    /**
     * 获取所有错误文件
     */
    fun getErrorFiles(): List<FileDiagnostics> {
        return getAllDiagnostics().filter { it.hasErrors }
    }

    /**
     * 获取所有警告文件
     */
    fun getWarningFiles(): List<FileDiagnostics> {
        return getAllDiagnostics().filter { it.hasWarnings }
    }

    /**
     * 检查文件是否有问题
     */
    fun hasProblems(file: VirtualFile): Boolean {
        return collectorService.getDiagnostics(file)?.items?.isNotEmpty() == true
    }

    /**
     * 检查文件是否有错误
     */
    fun hasErrors(file: VirtualFile): Boolean {
        return collectorService.getDiagnostics(file)?.hasErrors == true
    }

    /**
     * 检查文件是否有警告
     */
    fun hasWarnings(file: VirtualFile): Boolean {
        return collectorService.getDiagnostics(file)?.hasWarnings == true
    }

    /**
     * 获取项目问题统计
     */
    fun getStatistics(): DiagnosticStatistics {
        val allFiles = getAllDiagnostics()
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

    fun addListener(listener: CacheUpdateListener) {
        listeners.add(listener)
        // 立即通知当前状态
        val current = getAllDiagnostics()
        if (current.isNotEmpty()) {
            ApplicationManager.getApplication().invokeLater {
                listener.onCacheUpdate(CacheUpdateEvent.Update(current))
            }
        }
    }

    fun removeListener(listener: CacheUpdateListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners(event: CacheUpdateEvent) {
        val listenersCopy = listeners.toList()
        LOG.debug("[Problem4AI][Cache] notify listeners=${listenersCopy.size}")
        ApplicationManager.getApplication().invokeLater {
            listenersCopy.forEach { it.onCacheUpdate(event) }
        }
    }

    override fun dispose() {
        listeners.clear()
    }
}

data class DiagnosticStatistics(
    val totalFiles: Int,
    val errorFiles: Int,
    val warningFiles: Int,
    val totalErrors: Int,
    val totalWarnings: Int
)

sealed class CacheUpdateEvent {
    data class Update(val diagnostics: List<FileDiagnostics>) : CacheUpdateEvent()
}

fun interface CacheUpdateListener {
    fun onCacheUpdate(event: CacheUpdateEvent)
}

/**
 * 项目启动时触发全量扫描
 */
class GlobalDiagnosticCacheInitializer : ProjectActivity {
    companion object {
        private val LOG: Logger = Logger.getInstance(GlobalDiagnosticCacheInitializer::class.java)
    }

    override suspend fun execute(project: Project) {
        LOG.info("[Problem4AI][Startup] initializer execute project=${project.name}")
        // 先加载.gitignore排除规则
        val exclusionConfig = DiagnosticExclusionConfig.getInstance(project)
        exclusionConfig.loadFromGitignore(project)

        val cache = GlobalDiagnosticCache.getInstance(project)

        // 启动后立即扫一次，尽早给面板基础结果
        LOG.info("[Problem4AI][Startup] trigger immediate full scan")
        cache.performFullScan()

        // 新项目导入阶段根目录可能尚未就绪，延迟再触发两次兜底
        val retryAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)
        retryAlarm.addRequest({
            LOG.info("[Problem4AI][Startup] trigger delayed full scan (+2s)")
            cache.performFullScan()
        }, 2000)
        retryAlarm.addRequest({
            LOG.info("[Problem4AI][Startup] trigger delayed full scan (+6s)")
            cache.performFullScan()
        }, 6000)

        // 索引完成后再扫一次，补齐语义级错误
        com.intellij.openapi.project.DumbService.getInstance(project).runWhenSmart {
            LOG.info("[Problem4AI][Startup] trigger smart-mode full scan")
            cache.performFullScan()
        }
    }
}

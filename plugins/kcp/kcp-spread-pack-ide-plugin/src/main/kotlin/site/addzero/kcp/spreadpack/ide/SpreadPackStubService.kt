package site.addzero.kcp.spreadpack.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class SpreadPackStubService(
    private val project: Project,
) {

    private val logger = Logger.getInstance(SpreadPackStubService::class.java)
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)

    @Volatile
    private var generatedFiles: List<IdeGeneratedFile> = emptyList()

    private val refreshInProgress = AtomicBoolean(false)
    private val refreshRequestedWhileRunning = AtomicBoolean(false)

    init {
        val connection = project.messageBus.connect(project)
        connection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any(::shouldRefreshFor)) {
                        scheduleRefresh()
                    }
                }
            },
        )
    }

    internal fun getGeneratedFiles(): List<IdeGeneratedFile> = generatedFiles

    fun scheduleRefresh() {
        if (project.isDisposed) {
            return
        }
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest(
            {
                if (project.isDisposed) {
                    return@addRequest
                }
                if (DumbService.getInstance(project).isDumb) {
                    scheduleRefresh()
                    return@addRequest
                }
                if (!refreshInProgress.compareAndSet(false, true)) {
                    refreshRequestedWhileRunning.set(true)
                    return@addRequest
                }
                refreshNow()
            },
            400,
        )
    }

    private fun refreshNow() {
        try {
            val generatedFiles = ApplicationManager.getApplication().runReadAction<List<IdeGeneratedFile>> {
                if (project.isDisposed) {
                    emptyList()
                } else {
                    SpreadPackStubGenerator(project).generate()
                }
            }
            this.generatedFiles = generatedFiles
            logger.info("Spread-pack IDEA stubs refreshed: ${generatedFiles.size} file(s)")
        } finally {
            refreshInProgress.set(false)
            if (refreshRequestedWhileRunning.compareAndSet(true, false)) {
                scheduleRefresh()
            }
        }
    }

    private fun shouldRefreshFor(event: VFileEvent): Boolean {
        if (!event.path.endsWith(".kt") && !event.path.endsWith(".kts")) {
            return false
        }
        return true
    }
}

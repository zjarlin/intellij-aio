package site.addzero.deploy.pipeline

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 部署会话监听器
 */
interface DeploySessionListener {
    fun onSessionCreated(session: DeploySession)
    fun onSessionUpdated(session: DeploySession)
    fun onLogAdded(sessionId: String, log: DeployLog)
    fun onSessionCompleted(session: DeploySession)

    companion object {
        val TOPIC = Topic.create("DeploySession", DeploySessionListener::class.java)
    }
}

/**
 * 部署会话
 */
data class DeploySession(
    val id: String = UUID.randomUUID().toString(),
    val configName: String,
    val targetName: String,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    var status: DeployStatus = DeployStatus.PENDING,
    val phases: MutableMap<DeployPhase, PhaseProgress> = mutableMapOf(
        DeployPhase.BUILD to PhaseProgress(DeployPhase.BUILD),
        DeployPhase.UPLOAD to PhaseProgress(DeployPhase.UPLOAD),
        DeployPhase.DEPLOY to PhaseProgress(DeployPhase.DEPLOY)
    ),
    val logs: MutableList<DeployLog> = mutableListOf()
) {
    var currentPhase: DeployPhase = DeployPhase.BUILD
        private set

    val overallProgress: Double
        get() {
            var total = 0.0
            phases.forEach { (phase, progress) ->
                val weight = DeployPhase.PHASE_WEIGHTS[phase] ?: 0.0
                total += weight * progress.progress
            }
            return total.coerceIn(0.0, 1.0)
        }

    fun startPhase(phase: DeployPhase) {
        currentPhase = phase
        phases[phase]?.apply {
            status = DeployStatus.RUNNING
            progress = 0.0
        }
    }

    fun updatePhase(phase: DeployPhase, progress: Double, message: String = "") {
        phases[phase]?.apply {
            this.progress = progress.coerceIn(0.0, 1.0)
            this.message = message
        }
    }

    fun completePhase(phase: DeployPhase, success: Boolean) {
        phases[phase]?.apply {
            status = if (success) DeployStatus.SUCCESS else DeployStatus.FAILED
            progress = if (success) 1.0 else progress
        }
    }

    fun addLog(phase: DeployPhase, level: LogLevel, message: String): DeployLog {
        val log = DeployLog(
            phase = phase,
            level = level,
            message = message
        )
        logs.add(log)
        return log
    }

    fun complete(success: Boolean) {
        status = if (success) DeployStatus.SUCCESS else DeployStatus.FAILED
        endTime = System.currentTimeMillis()
    }

    fun getDuration(): Long = (endTime ?: System.currentTimeMillis()) - startTime

    fun getDurationFormatted(): String {
        val duration = getDuration()
        val seconds = duration / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return if (minutes > 0) "${minutes}m ${remainingSeconds}s" else "${remainingSeconds}s"
    }
}

/**
 * 部署会话管理服务
 */
@Service(Service.Level.PROJECT)
class DeploySessionService(private val project: Project) {

    private val sessions = ConcurrentHashMap<String, DeploySession>()
    private val maxHistorySize = 50

    fun createSession(configName: String, targetName: String): DeploySession {
        val session = DeploySession(
            configName = configName,
            targetName = targetName
        )
        sessions[session.id] = session

        // 清理旧会话
        if (sessions.size > maxHistorySize) {
            val oldestCompleted = sessions.values
                .filter { it.status in listOf(DeployStatus.SUCCESS, DeployStatus.FAILED, DeployStatus.CANCELLED) }
                .minByOrNull { it.startTime }
            oldestCompleted?.let { sessions.remove(it.id) }
        }

        notifySessionCreated(session)
        return session
    }

    fun getSession(id: String): DeploySession? = sessions[id]

    fun getAllSessions(): List<DeploySession> = sessions.values
        .sortedByDescending { it.startTime }
        .toList()

    fun getActiveSessions(): List<DeploySession> = sessions.values
        .filter { it.status == DeployStatus.RUNNING }
        .sortedByDescending { it.startTime }
        .toList()

    fun updateSession(session: DeploySession) {
        sessions[session.id] = session
        notifySessionUpdated(session)
    }

    fun addLog(sessionId: String, phase: DeployPhase, level: LogLevel, message: String) {
        sessions[sessionId]?.let { session ->
            val log = session.addLog(phase, level, message)
            notifyLogAdded(sessionId, log)
        }
    }

    fun completeSession(sessionId: String, success: Boolean) {
        sessions[sessionId]?.let { session ->
            session.complete(success)
            notifySessionCompleted(session)
        }
    }

    fun cancelSession(sessionId: String) {
        sessions[sessionId]?.let { session ->
            session.status = DeployStatus.CANCELLED
            session.endTime = System.currentTimeMillis()
            notifySessionCompleted(session)
        }
    }

    fun clearHistory() {
        val completedIds = sessions.values
            .filter { it.status != DeployStatus.RUNNING }
            .map { it.id }
        completedIds.forEach { sessions.remove(it) }
    }

    private fun notifySessionCreated(session: DeploySession) {
        project.messageBus.syncPublisher(DeploySessionListener.TOPIC).onSessionCreated(session)
    }

    private fun notifySessionUpdated(session: DeploySession) {
        project.messageBus.syncPublisher(DeploySessionListener.TOPIC).onSessionUpdated(session)
    }

    private fun notifyLogAdded(sessionId: String, log: DeployLog) {
        project.messageBus.syncPublisher(DeploySessionListener.TOPIC).onLogAdded(sessionId, log)
    }

    private fun notifySessionCompleted(session: DeploySession) {
        project.messageBus.syncPublisher(DeploySessionListener.TOPIC).onSessionCompleted(session)
    }

    companion object {
        fun getInstance(project: Project): DeploySessionService =
            project.getService(DeploySessionService::class.java)
    }
}

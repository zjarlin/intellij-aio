package site.addzero.deploy.pipeline

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 部署阶段
 */
enum class DeployPhase(val displayName: String, val icon: String) {
    BUILD("构建", "🔨"),
    UPLOAD("上传", "📤"),
    DEPLOY("部署", "🚀");

    companion object {
        val PHASE_WEIGHTS = mapOf(
            BUILD to 0.4,
            UPLOAD to 0.4,
            DEPLOY to 0.2
        )
    }
}

/**
 * 日志级别
 */
enum class LogLevel(val prefix: String) {
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR"),
    DEBUG("DEBUG")
}

/**
 * 部署状态
 */
enum class DeployStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}

/**
 * 部署日志条目
 */
data class DeployLog(
    val timestamp: Long = System.currentTimeMillis(),
    val phase: DeployPhase,
    val level: LogLevel,
    val message: String
) {
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun formatTime(): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return dateTime.format(formatter)
    }

    fun format(): String = "${formatTime()} [${level.prefix}] ${phase.icon} $message"
}

/**
 * 阶段进度
 */
data class PhaseProgress(
    val phase: DeployPhase,
    var progress: Double = 0.0,
    var status: DeployStatus = DeployStatus.PENDING,
    var message: String = ""
) {
    fun isCompleted(): Boolean = status == DeployStatus.SUCCESS || status == DeployStatus.FAILED
}

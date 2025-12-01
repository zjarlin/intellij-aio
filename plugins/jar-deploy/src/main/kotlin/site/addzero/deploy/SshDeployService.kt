package site.addzero.deploy

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.jcraft.jsch.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.*

/**
 * SSH 部署服务 - 使用 JSch 实现 SSH/SFTP 功能
 */
@Service(Service.Level.PROJECT)
class SshDeployService(private val project: Project) {

    private val log = Logger.getInstance(SshDeployService::class.java)

    /**
     * 部署单个文件到远程服务器
     */
    fun deployFile(
        localFile: File,
        target: DeployTarget,
        indicator: ProgressIndicator
    ): DeployResult {
        return withSshSession(target) { session ->
            // 执行部署前命令
            if (target.preDeployCommand.isNotBlank()) {
                indicator.text = "Running pre-deploy command..."
                val preResult = executeCommand(session, target.preDeployCommand)
                if (preResult.exitCode != 0) {
                    return@withSshSession DeployResult.failure("Pre-deploy command failed: ${preResult.stderr}")
                }
            }

            // 确保远程目录存在
            indicator.text = "Creating remote directory..."
            executeCommand(session, "mkdir -p ${target.remoteDir}")

            // 上传文件
            val remotePath = "${target.remoteDir}/${localFile.name}"
            indicator.text = "Uploading ${localFile.name}..."
            uploadFile(session, localFile, remotePath, indicator)

            // 执行部署后命令
            if (target.postDeployCommand.isNotBlank()) {
                indicator.text = "Running post-deploy command..."
                val postResult = executeCommand(session, target.postDeployCommand)
                if (postResult.exitCode != 0) {
                    log.warn("Post-deploy command failed: ${postResult.stderr}")
                }
            }

            DeployResult.success("Deployed to ${target.host}:$remotePath")
        }
    }

    /**
     * 递归部署整个目录到远程服务器
     */
    fun deployDirectory(
        localDir: File,
        target: DeployTarget,
        indicator: ProgressIndicator
    ): DeployResult {
        return withSshSession(target) { session ->
            // 执行部署前命令
            if (target.preDeployCommand.isNotBlank()) {
                indicator.text = "Running pre-deploy command..."
                val preResult = executeCommand(session, target.preDeployCommand)
                if (preResult.exitCode != 0) {
                    return@withSshSession DeployResult.failure("Pre-deploy command failed: ${preResult.stderr}")
                }
            }

            val remoteBaseDir = "${target.remoteDir}/${localDir.name}"
            executeCommand(session, "mkdir -p $remoteBaseDir")

            // 递归上传所有文件
            val files = collectFiles(localDir)
            files.forEachIndexed { index, file ->
                val relativePath = file.relativeTo(localDir).path
                val remotePath = "$remoteBaseDir/$relativePath"

                indicator.text = "Uploading (${index + 1}/${files.size}): ${file.name}"
                indicator.fraction = (index + 1).toDouble() / files.size

                if (file.isDirectory) {
                    executeCommand(session, "mkdir -p $remotePath")
                } else {
                    val remoteDir = remotePath.substringBeforeLast("/")
                    executeCommand(session, "mkdir -p $remoteDir")
                    uploadFile(session, file, remotePath, indicator)
                }
            }

            // 执行部署后命令
            if (target.postDeployCommand.isNotBlank()) {
                indicator.text = "Running post-deploy command..."
                val postResult = executeCommand(session, target.postDeployCommand)
                if (postResult.exitCode != 0) {
                    log.warn("Post-deploy command failed: ${postResult.stderr}")
                }
            }

            DeployResult.success("Deployed ${files.size} files to ${target.host}:$remoteBaseDir")
        }
    }

    private fun <T> withSshSession(target: DeployTarget, action: (Session) -> T): T {
        val jsch = JSch()

        // 添加私钥（如果配置了）
        target.privateKeyPath?.takeIf { it.isNotBlank() }?.let { keyPath ->
            if (target.passphrase.isNullOrBlank()) {
                jsch.addIdentity(keyPath)
            } else {
                jsch.addIdentity(keyPath, target.passphrase)
            }
        }

        val session = jsch.getSession(target.username, target.host, target.port)

        // 设置密码（如果使用密码认证）
        target.password?.takeIf { it.isNotBlank() }?.let {
            session.setPassword(it)
        }

        // 配置
        val config = Properties().apply {
            put("StrictHostKeyChecking", "no")
            put("PreferredAuthentications", "publickey,keyboard-interactive,password")
        }
        session.setConfig(config)

        return try {
            session.connect(30000) // 30秒超时
            action(session)
        } finally {
            if (session.isConnected) {
                session.disconnect()
            }
        }
    }

    private fun executeCommand(session: Session, command: String): CommandResult {
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        channel.outputStream = stdout
        channel.setErrStream(stderr)

        return try {
            channel.connect(10000)

            // 等待命令完成
            while (!channel.isClosed) {
                Thread.sleep(100)
            }

            CommandResult(
                exitCode = channel.exitStatus,
                stdout = stdout.toString(Charsets.UTF_8.name()),
                stderr = stderr.toString(Charsets.UTF_8.name())
            )
        } catch (e: Exception) {
            log.error("Command execution failed: $command", e)
            CommandResult(-1, "", e.message ?: "Unknown error")
        } finally {
            channel.disconnect()
        }
    }

    private fun uploadFile(
        session: Session,
        localFile: File,
        remotePath: String,
        indicator: ProgressIndicator
    ) {
        val channel = session.openChannel("sftp") as ChannelSftp

        try {
            channel.connect(10000)

            // 使用进度监控上传
            val monitor = object : SftpProgressMonitor {
                private var totalBytes = 0L
                private var transferredBytes = 0L

                override fun init(op: Int, src: String?, dest: String?, max: Long) {
                    totalBytes = max
                    transferredBytes = 0
                }

                override fun count(count: Long): Boolean {
                    transferredBytes += count
                    if (totalBytes > 0) {
                        indicator.fraction = transferredBytes.toDouble() / totalBytes
                    }
                    return !indicator.isCanceled
                }

                override fun end() {}
            }

            FileInputStream(localFile).use { input ->
                channel.put(input, remotePath, monitor, ChannelSftp.OVERWRITE)
            }
        } finally {
            channel.disconnect()
        }
    }

    private fun collectFiles(dir: File): List<File> {
        return dir.walkTopDown()
            .filter { it != dir }
            .toList()
    }

    companion object {
        fun getInstance(project: Project): SshDeployService =
            project.getService(SshDeployService::class.java)
    }
}

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

sealed class DeployResult {
    data class Success(val message: String) : DeployResult()
    data class Failure(val error: String) : DeployResult()

    companion object {
        fun success(message: String): DeployResult = Success(message)
        fun failure(error: String): DeployResult = Failure(error)
    }

    fun isSuccess(): Boolean = this is Success
    fun getMessage(): String = when (this) {
        is Success -> message
        is Failure -> error
    }
}

package site.addzero.deploy

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.remote.RemoteCredentials
import com.intellij.ssh.SshConnectionService
import com.intellij.ssh.config.SshConnectionConfigService
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * SSH 部署服务 - 复用 IDE 的 SSH 隧道
 */
@Service(Service.Level.PROJECT)
class SshDeployService(private val project: Project) {

    private val log = Logger.getInstance(SshDeployService::class.java)

    /**
     * 获取 IDE 配置的所有 SSH 连接
     */
    fun getAvailableSshConfigs(): List<String> {
        return try {
            val configService = SshConnectionConfigService.getInstance()
            configService.configs.map { it.name }
        } catch (e: Exception) {
            log.warn("Failed to get SSH configs", e)
            emptyList()
        }
    }

    /**
     * 根据名称获取 SSH 凭证
     */
    fun getSshCredentials(configName: String): RemoteCredentials? {
        return try {
            val configService = SshConnectionConfigService.getInstance()
            configService.configs.find { it.name == configName }?.let { config ->
                config.copyToCredentials(null)
            }
        } catch (e: Exception) {
            log.warn("Failed to get SSH credentials for $configName", e)
            null
        }
    }

    /**
     * 部署单个文件到远程服务器
     */
    fun deployFile(
        localFile: File,
        target: DeployTarget,
        indicator: ProgressIndicator
    ): DeployResult {
        val credentials = getSshCredentials(target.sshConfigName)
            ?: return DeployResult.failure("SSH config not found: ${target.sshConfigName}")

        return try {
            val connectionService = SshConnectionService.getInstance()
            
            connectionService.connect(credentials).use { connection ->
                // 执行部署前命令
                if (target.preDeployCommand.isNotBlank()) {
                    val preResult = executeCommand(connection, target.preDeployCommand)
                    if (preResult.exitCode != 0) {
                        return DeployResult.failure("Pre-deploy command failed: ${preResult.stderr}")
                    }
                }
                
                // 确保远程目录存在
                executeCommand(connection, "mkdir -p ${target.remoteDir}")
                
                // 上传文件
                val remotePath = "${target.remoteDir}/${localFile.name}"
                uploadFile(connection, localFile, remotePath, indicator)
                
                // 执行部署后命令
                if (target.postDeployCommand.isNotBlank()) {
                    val postResult = executeCommand(connection, target.postDeployCommand)
                    if (postResult.exitCode != 0) {
                        log.warn("Post-deploy command failed: ${postResult.stderr}")
                    }
                }
                
                DeployResult.success("Deployed to ${credentials.host}:$remotePath")
            }
        } catch (e: Exception) {
            log.error("Deploy failed", e)
            DeployResult.failure("Deploy failed: ${e.message}")
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
        val credentials = getSshCredentials(target.sshConfigName)
            ?: return DeployResult.failure("SSH config not found: ${target.sshConfigName}")

        return try {
            val connectionService = SshConnectionService.getInstance()
            
            connectionService.connect(credentials).use { connection ->
                // 执行部署前命令
                if (target.preDeployCommand.isNotBlank()) {
                    val preResult = executeCommand(connection, target.preDeployCommand)
                    if (preResult.exitCode != 0) {
                        return DeployResult.failure("Pre-deploy command failed: ${preResult.stderr}")
                    }
                }
                
                val remoteBaseDir = "${target.remoteDir}/${localDir.name}"
                executeCommand(connection, "mkdir -p $remoteBaseDir")
                
                // 递归上传所有文件
                val files = collectFiles(localDir)
                files.forEachIndexed { index, file ->
                    val relativePath = file.relativeTo(localDir).path
                    val remotePath = "$remoteBaseDir/$relativePath"
                    
                    if (file.isDirectory) {
                        executeCommand(connection, "mkdir -p $remotePath")
                    } else {
                        val remoteDir = remotePath.substringBeforeLast("/")
                        executeCommand(connection, "mkdir -p $remoteDir")
                        uploadFile(connection, file, remotePath, indicator)
                    }
                    
                    indicator.text2 = "Uploading ${file.name}"
                }
                
                // 执行部署后命令
                if (target.postDeployCommand.isNotBlank()) {
                    val postResult = executeCommand(connection, target.postDeployCommand)
                    if (postResult.exitCode != 0) {
                        log.warn("Post-deploy command failed: ${postResult.stderr}")
                    }
                }
                
                DeployResult.success("Deployed ${files.size} files to ${credentials.host}:$remoteBaseDir")
            }
        } catch (e: Exception) {
            log.error("Deploy directory failed", e)
            DeployResult.failure("Deploy failed: ${e.message}")
        }
    }

    private fun collectFiles(dir: File): List<File> {
        val result = mutableListOf<File>()
        dir.walkTopDown().forEach { file ->
            if (file != dir) {
                result.add(file)
            }
        }
        return result
    }

    private fun executeCommand(connection: Any, command: String): CommandResult {
        return try {
            // 使用反射调用执行命令方法，兼容不同IDE版本
            val execMethod = connection.javaClass.methods.find { 
                it.name == "exec" || it.name == "execCommand" 
            }
            
            if (execMethod != null) {
                val result = execMethod.invoke(connection, command)
                val exitCode = result.javaClass.getMethod("getExitCode").invoke(result) as Int
                val stdout = result.javaClass.getMethod("getStdout").invoke(result)?.toString() ?: ""
                val stderr = result.javaClass.getMethod("getStderr").invoke(result)?.toString() ?: ""
                CommandResult(exitCode, stdout, stderr)
            } else {
                CommandResult(-1, "", "Exec method not found")
            }
        } catch (e: Exception) {
            log.error("Command execution failed: $command", e)
            CommandResult(-1, "", e.message ?: "Unknown error")
        }
    }

    private fun uploadFile(
        connection: Any,
        localFile: File,
        remotePath: String,
        indicator: ProgressIndicator
    ) {
        try {
            // 使用反射获取SFTP通道
            val sftpMethod = connection.javaClass.methods.find { 
                it.name == "openSftpChannel" || it.name == "getSftp" 
            }
            
            if (sftpMethod != null) {
                val sftp = sftpMethod.invoke(connection)
                val putMethod = sftp.javaClass.methods.find { it.name == "put" }
                
                if (putMethod != null) {
                    localFile.inputStream().use { input ->
                        val params = putMethod.parameters
                        when (params.size) {
                            2 -> putMethod.invoke(sftp, remotePath, input)
                            3 -> putMethod.invoke(sftp, input, remotePath, null)
                            else -> {
                                // 尝试其他上传方式
                                val uploadMethod = sftp.javaClass.methods.find { 
                                    it.name == "upload" || it.name == "copyFile" 
                                }
                                uploadMethod?.invoke(sftp, localFile.absolutePath, remotePath)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("File upload failed", e)
            throw e
        }
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

package site.addzero.lsi.analyzer.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.lsi.analyzer.config.DdlSettings
import java.io.IOException
import java.util.*

/**
 * Spring 项目 JDBC 连接信息猜测服务
 */
@Service
class JdbcConnectionDetectorService {

    data class ConnectionInfo(
        var url: String = "",
        var username: String = "",
        var password: String = "",
        var driver: String = "",
        var dialect: String = ""
    )

    /**
     * 自动检测项目的数据库连接信息
     */
    fun detectConnectionInfo(project: Project): ConnectionInfo {
        val connectionInfo = ConnectionInfo()

        // 1. 从 application.properties/yml 检测
        detectFromSpringConfig(project, connectionInfo)

        // 2. 如果检测不到，使用设置中的默认值
        if (connectionInfo.url.isEmpty()) {
            val settings = DdlSettings.getInstance()
            connectionInfo.url = settings.jdbcUrl ?: ""
            connectionInfo.username = settings.jdbcUsername ?: ""
            connectionInfo.password = settings.jdbcPassword ?: ""
        }

        // 3. 总是从 URL 推断数据库方言（如果有的话）
        if (connectionInfo.url.isNotEmpty()) {
            connectionInfo.dialect = detectDialectFromUrl(connectionInfo.url)
        }

        return connectionInfo
    }

    /**
     * 从 Spring 配置文件中检测连接信息
     */
    private fun detectFromSpringConfig(project: Project, connectionInfo: ConnectionInfo) {

        // 查找 application.properties 和 application.yml 文件
        val configFiles = mutableListOf<VirtualFile>()

        // 通过文件系统搜索配置文件
        ProjectFileIndex.getInstance(project).iterateContent { file ->
            val fileName = file.name
            val extension = file.extension?.lowercase()

            if (fileName.startsWith("application") &&
                (extension == "properties" || extension == "yml" || extension == "yaml")) {
                configFiles.add(file)
            }
            true
        }

        // 解析配置文件
        configFiles.forEach { file ->
            try {
                if (file.extension == "properties") {
                    parsePropertiesFile(file, connectionInfo)
                } else if (file.extension == "yml" || file.extension == "yaml") {
                    parseYamlFile(file, connectionInfo)
                }
            } catch (e: IOException) {
                // 忽略解析错误
            }
        }
    }

    /**
     * 解析 properties 文件
     */
    private fun parsePropertiesFile(file: VirtualFile, connectionInfo: ConnectionInfo) {
        val properties = Properties()
        file.inputStream.use { inputStream ->
            properties.load(inputStream)
        }

        // 常见的 Spring Boot 配置键
        val urlKeys = listOf(
            "spring.datasource.url",
            "spring.datasource.jdbc-url",
            "javax.sql.DataSource.url"
        )

        val usernameKeys = listOf(
            "spring.datasource.username",
            "javax.sql.DataSource.username"
        )

        val passwordKeys = listOf(
            "spring.datasource.password",
            "javax.sql.DataSource.password"
        )

        val driverKeys = listOf(
            "spring.datasource.driver-class-name",
            "spring.datasource.driverClassName",
            "javax.sql.DataSource.driver-class-name"
        )

        // 提取配置值
        urlKeys.forEach { key ->
            val value = properties.getProperty(key)
            if (value != null) {
                connectionInfo.url = value
            }
        }

        usernameKeys.forEach { key ->
            val value = properties.getProperty(key)
            if (value != null) connectionInfo.username = value
        }

        passwordKeys.forEach { key ->
            val value = properties.getProperty(key)
            if (value != null) connectionInfo.password = value
        }

        driverKeys.forEach { key ->
            val value = properties.getProperty(key)
            if (value != null) connectionInfo.driver = value
        }
    }

    /**
     * 解析 YAML 文件
     */
    private fun parseYamlFile(file: VirtualFile, connectionInfo: ConnectionInfo) {
        val content = file.inputStream.use { it.bufferedReader().readText() }

        // 简单的 YAML 解析（可以使用 SnakeYAML 库进行更完整的解析）
        val lines = content.split("\n")
        var inSpringSection = false
        var inDatasourceSection = false

        for (line in lines) {
            val trimmedLine = line.trim()

            when {
                trimmedLine.startsWith("spring:") -> {
                    inSpringSection = true
                }
                inSpringSection && trimmedLine.startsWith("datasource:") -> {
                    inDatasourceSection = true
                }
                inDatasourceSection && trimmedLine.startsWith("url:") -> {
                    connectionInfo.url = extractYamlValue(trimmedLine)
                    connectionInfo.dialect = detectDialectFromUrl(connectionInfo.url)
                }
                inDatasourceSection && trimmedLine.startsWith("username:") -> {
                    connectionInfo.username = extractYamlValue(trimmedLine)
                }
                inDatasourceSection && trimmedLine.startsWith("password:") -> {
                    connectionInfo.password = extractYamlValue(trimmedLine)
                }
                inDatasourceSection && (trimmedLine.startsWith("driver-class-name:")
                        || trimmedLine.startsWith("driverClassName:")) -> {
                    connectionInfo.driver = extractYamlValue(trimmedLine)
                }
                // 检查缩进判断是否还在 datasource 节下
                inDatasourceSection && trimmedLine.isNotEmpty() && !line.startsWith("  ") -> {
                    inDatasourceSection = false
                }
            }
        }
    }

    /**
     * 从 YAML 行中提取值
     */
    private fun extractYamlValue(line: String): String {
        return line.substringAfter(":").trim().removeSurrounding("\"")
    }

    /**
     * 从 JDBC URL 推断数据库方言
     */
    private fun detectDialectFromUrl(url: String): String {
        return when {
            url.contains("mysql://") || url.contains("jdbc:mysql") -> "mysql"
            url.contains("postgresql://") || url.contains("jdbc:postgresql") -> "postgresql"
            url.contains("oracle://") || url.contains("jdbc:oracle") -> "oracle"
            url.contains("sqlserver://") || url.contains("jdbc:sqlserver") -> "sqlserver"
            url.contains("db2://") || url.contains("jdbc:db2") -> "db2"
            url.contains("h2://") || url.contains("jdbc:h2") -> "h2"
            else -> "mysql" // 默认
        }
    }
}
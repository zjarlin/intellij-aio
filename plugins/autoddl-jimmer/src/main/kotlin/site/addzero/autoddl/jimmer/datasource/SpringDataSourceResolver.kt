package site.addzero.autoddl.jimmer.datasource

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import org.yaml.snakeyaml.Yaml
import site.addzero.autoddl.jimmer.settings.JimmerDdlSettings
import java.io.File
import java.util.*

/**
 * Spring 数据源解析器
 * 从项目的 Spring 配置文件中解析 JDBC 连接信息
 * 支持多数据源配置
 */
class SpringDataSourceResolver(private val project: Project) {

    private val log = Logger.getInstance(SpringDataSourceResolver::class.java)
    private val settings = JimmerDdlSettings.getInstance(project)

    /**
     * 解析结果：数据源信息
     */
    data class DataSourceInfo(
        val name: String,
        val url: String,
        val username: String,
        val password: String,
        val driverClassName: String? = null
    )

    /**
     * 解析策略接口
     */
    interface ResolveStrategy {
        fun support(configMap: Map<String, Any?>): Boolean
        fun resolve(configMap: Map<String, Any?>): List<DataSourceInfo>
    }

    /**
     * 标准 Spring Boot 单数据源
     */
    private val singleDataSourceStrategy = object : ResolveStrategy {
        override fun support(configMap: Map<String, Any?>): Boolean {
            val spring = configMap["spring"] as? Map<*, *> ?: return false
            val datasource = spring["datasource"] as? Map<*, *> ?: return false
            return datasource["url"] != null
        }

        override fun resolve(configMap: Map<String, Any?>): List<DataSourceInfo> {
            val spring = configMap["spring"] as? Map<*, *> ?: return emptyList()
            val ds = spring["datasource"] as? Map<*, *> ?: return emptyList()
            val url = ds["url"]?.toString() ?: return emptyList()
            return listOf(
                DataSourceInfo(
                    name = "default",
                    url = url,
                    username = ds["username"]?.toString() ?: "",
                    password = ds["password"]?.toString() ?: "",
                    driverClassName = ds["driver-class-name"]?.toString()
                )
            )
        }
    }

    /**
     * HikariCP 数据源
     */
    private val hikariStrategy = object : ResolveStrategy {
        override fun support(configMap: Map<String, Any?>): Boolean {
            val spring = configMap["spring"] as? Map<*, *> ?: return false
            val datasource = spring["datasource"] as? Map<*, *> ?: return false
            val hikari = datasource["hikari"] as? Map<*, *> ?: return false
            return hikari["jdbc-url"] != null || hikari["jdbcUrl"] != null
        }

        override fun resolve(configMap: Map<String, Any?>): List<DataSourceInfo> {
            val spring = configMap["spring"] as? Map<*, *> ?: return emptyList()
            val ds = spring["datasource"] as? Map<*, *> ?: return emptyList()
            val hikari = ds["hikari"] as? Map<*, *> ?: return emptyList()
            val url = (hikari["jdbc-url"] ?: hikari["jdbcUrl"])?.toString() ?: return emptyList()
            return listOf(
                DataSourceInfo(
                    name = "hikari",
                    url = url,
                    username = hikari["username"]?.toString() ?: ds["username"]?.toString() ?: "",
                    password = hikari["password"]?.toString() ?: ds["password"]?.toString() ?: "",
                    driverClassName = hikari["driver-class-name"]?.toString()
                )
            )
        }
    }

    /**
     * Druid 数据源
     */
    private val druidStrategy = object : ResolveStrategy {
        override fun support(configMap: Map<String, Any?>): Boolean {
            val spring = configMap["spring"] as? Map<*, *> ?: return false
            val datasource = spring["datasource"] as? Map<*, *> ?: return false
            val druid = datasource["druid"] as? Map<*, *> ?: return false
            return druid["url"] != null
        }

        override fun resolve(configMap: Map<String, Any?>): List<DataSourceInfo> {
            val spring = configMap["spring"] as? Map<*, *> ?: return emptyList()
            val ds = spring["datasource"] as? Map<*, *> ?: return emptyList()
            val druid = ds["druid"] as? Map<*, *> ?: return emptyList()
            val url = druid["url"]?.toString() ?: return emptyList()
            return listOf(
                DataSourceInfo(
                    name = "druid",
                    url = url,
                    username = druid["username"]?.toString() ?: "",
                    password = druid["password"]?.toString() ?: "",
                    driverClassName = druid["driver-class-name"]?.toString()
                )
            )
        }
    }

    /**
     * 多数据源（dynamic-datasource-spring-boot-starter 风格）
     */
    private val dynamicMultiDataSourceStrategy = object : ResolveStrategy {
        override fun support(configMap: Map<String, Any?>): Boolean {
            val spring = configMap["spring"] as? Map<*, *> ?: return false
            val datasource = spring["datasource"] as? Map<*, *> ?: return false
            val dynamic = datasource["dynamic"] as? Map<*, *> ?: return false
            return dynamic["datasource"] != null
        }

        @Suppress("UNCHECKED_CAST")
        override fun resolve(configMap: Map<String, Any?>): List<DataSourceInfo> {
            val spring = configMap["spring"] as? Map<*, *> ?: return emptyList()
            val datasource = spring["datasource"] as? Map<*, *> ?: return emptyList()
            val dynamic = datasource["dynamic"] as? Map<*, *> ?: return emptyList()
            val sources = dynamic["datasource"] as? Map<String, Map<*, *>> ?: return emptyList()

            return sources.map { (name, config) ->
                DataSourceInfo(
                    name = name,
                    url = config["url"]?.toString() ?: "",
                    username = config["username"]?.toString() ?: "",
                    password = config["password"]?.toString() ?: "",
                    driverClassName = config["driver-class-name"]?.toString()
                )
            }.filter { it.url.isNotBlank() }
        }
    }

    /**
     * Jimmer 特有的多数据源配置
     */
    private val jimmerMultiDataSourceStrategy = object : ResolveStrategy {
        override fun support(configMap: Map<String, Any?>): Boolean {
            val jimmer = configMap["jimmer"] as? Map<*, *> ?: return false
            return jimmer["datasources"] != null
        }

        @Suppress("UNCHECKED_CAST")
        override fun resolve(configMap: Map<String, Any?>): List<DataSourceInfo> {
            val jimmer = configMap["jimmer"] as? Map<*, *> ?: return emptyList()
            val sources = jimmer["datasources"] as? Map<String, Map<*, *>> ?: return emptyList()

            return sources.map { (name, config) ->
                DataSourceInfo(
                    name = name,
                    url = config["url"]?.toString() ?: "",
                    username = config["username"]?.toString() ?: "",
                    password = config["password"]?.toString() ?: "",
                    driverClassName = config["driver-class-name"]?.toString()
                )
            }.filter { it.url.isNotBlank() }
        }
    }

    private val strategies = listOf(
        dynamicMultiDataSourceStrategy,
        jimmerMultiDataSourceStrategy,
        druidStrategy,
        hikariStrategy,
        singleDataSourceStrategy
    )

    /**
     * 解析所有数据源
     */
    fun resolveDataSources(): List<DataSourceInfo> {
        // 1. 优先使用手动配置
        val manualConfig = getManualConfig()
        if (manualConfig != null) {
            log.info("Using manual JDBC configuration")
            return listOf(manualConfig)
        }

        // 2. 从 Spring 配置文件解析
        val configFiles = findConfigFiles()
        if (configFiles.isEmpty()) {
            log.warn("No Spring configuration files found in project")
            return emptyList()
        }

        val allDataSources = mutableListOf<DataSourceInfo>()
        for (configFile in configFiles) {
            try {
                val dataSources = parseConfigFile(configFile)
                allDataSources.addAll(dataSources)
            } catch (e: Exception) {
                log.warn("Failed to parse config file: ${configFile.path}", e)
            }
        }

        if (allDataSources.isEmpty()) {
            log.warn("No datasource configuration found in Spring config files. Please configure manually in settings.")
        } else {
            log.info("Found ${allDataSources.size} datasource(s): ${allDataSources.map { it.name }}")
        }

        return allDataSources.distinctBy { it.url }
    }

    /**
     * 获取手动配置的数据源
     */
    private fun getManualConfig(): DataSourceInfo? {
        val jdbcUrl = settings.manualJdbcUrl
        if (jdbcUrl.isBlank()) return null

        return DataSourceInfo(
            name = "manual",
            url = jdbcUrl,
            username = settings.manualJdbcUsername,
            password = settings.manualJdbcPassword,
            driverClassName = null
        )
    }

    /**
     * 查找 Spring 配置文件
     */
    private fun findConfigFiles(): List<File> {
        val basePath = project.basePath ?: return emptyList()
        val resourceDirs = listOf(
            "src/main/resources",
            "src/main/resources/config",
            "config"
        )

        val configPatterns = listOf(
            "application.yml",
            "application.yaml",
            "application.properties",
            "application-dev.yml",
            "application-dev.yaml",
            "application-local.yml",
            "application-local.yaml"
        )

        return resourceDirs.flatMap { dir ->
            configPatterns.mapNotNull { pattern ->
                val file = File(basePath, "$dir/$pattern")
                if (file.exists()) file else null
            }
        }
    }

    /**
     * 解析配置文件
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseConfigFile(file: File): List<DataSourceInfo> {
        return when {
            file.name.endsWith(".yml") || file.name.endsWith(".yaml") -> {
                parseYamlFile(file)
            }
            file.name.endsWith(".properties") -> {
                parsePropertiesFile(file)
            }
            else -> emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseYamlFile(file: File): List<DataSourceInfo> {
        val yaml = Yaml()
        val configMap = yaml.load<Map<String, Any?>>(file.inputStream()) ?: return emptyList()

        // 使用策略模式匹配
        for (strategy in strategies) {
            if (strategy.support(configMap)) {
                return strategy.resolve(configMap)
            }
        }
        return emptyList()
    }

    private fun parsePropertiesFile(file: File): List<DataSourceInfo> {
        val props = Properties()
        file.inputStream().use { props.load(it) }

        val url = props.getProperty("spring.datasource.url") ?: return emptyList()
        return listOf(
            DataSourceInfo(
                name = "default",
                url = url,
                username = props.getProperty("spring.datasource.username") ?: "",
                password = props.getProperty("spring.datasource.password") ?: "",
                driverClassName = props.getProperty("spring.datasource.driver-class-name")
            )
        )
    }

    /**
     * 获取默认数据源（优先返回配置的数据源名称）
     */
    fun getDefaultDataSource(): DataSourceInfo? {
        val dataSources = resolveDataSources()
        if (dataSources.isEmpty()) return null

        val preferredName = settings.selectedDataSourceName
        return dataSources.find { it.name == preferredName } ?: dataSources.first()
    }
}

package site.addzero.gradle.buddy.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.File

@Service(Service.Level.PROJECT)
@State(
    name = "GradleBuddySettings",
    storages = [Storage("gradleBuddySettings.xml")]
)
class GradleBuddySettingsService : PersistentStateComponent<GradleBuddySettingsService.State> {

    data class State(
        var defaultTasks: MutableList<String> = DEFAULT_TASKS.toMutableList(),
        var versionCatalogPath: String = DEFAULT_VERSION_CATALOG_PATH,
        /** 智能补全时静默 upsert toml：选中后自动写入 toml 并回显 libs.xxx.xxx */
        var silentUpsertToml: Boolean = false,
        /**
         * Normalize 去重策略（同 group:artifact 不同版本冲突时）
         * "MAJOR_VERSION" = 提取主版本号后缀，如 2.7.18 → -v2（默认）
         * "ALT_SUFFIX"    = 使用 -alt, -alt2, -alt3 后缀
         */
        var normalizeDedupStrategy: String = "MAJOR_VERSION"
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    // 获取默认任务列表
    fun getDefaultTasks(): List<String> = myState.defaultTasks.toList()

    // 设置默认任务列表
    fun setDefaultTasks(tasks: List<String>) {
        myState.defaultTasks = tasks.toMutableList()
    }

    // 获取版本目录文件路径
    fun getVersionCatalogPath(): String = myState.versionCatalogPath

    // 设置版本目录文件路径
    fun setVersionCatalogPath(path: String) {
        myState.versionCatalogPath = path
    }

    // 添加默认任务
    fun addDefaultTask(task: String) {
        if (task !in myState.defaultTasks) {
            myState.defaultTasks.add(task)
        }
    }

    // 移除默认任务
    fun removeDefaultTask(task: String) {
        myState.defaultTasks.remove(task)
    }

    // 获取是否静默 upsert toml
    fun isSilentUpsertToml(): Boolean = myState.silentUpsertToml

    // 设置是否静默 upsert toml
    fun setSilentUpsertToml(enabled: Boolean) {
        myState.silentUpsertToml = enabled
    }

    // 获取 Normalize 去重策略
    fun getNormalizeDedupStrategy(): String = myState.normalizeDedupStrategy

    // 设置 Normalize 去重策略
    fun setNormalizeDedupStrategy(strategy: String) {
        myState.normalizeDedupStrategy = strategy
    }

    // 重置为默认值
    fun resetToDefaults() {
        myState.defaultTasks = DEFAULT_TASKS.toMutableList()
        myState.versionCatalogPath = DEFAULT_VERSION_CATALOG_PATH
    }

    /**
     * 解析版本目录文件的真实路径。
     *
     * 优先通过 GradleSettings 获取 linked Gradle project 的根路径，
     * 而非依赖 project.basePath（后者在子目录打开项目时不可靠）。
     * 如果没有 linked Gradle project，fallback 到 project.basePath。
     *
     * @return 找到的第一个存在的 catalog File，或基于最佳猜测的 File（可能不存在）
     */
    fun resolveVersionCatalogFile(project: Project): File {
        val catalogRelPath = getVersionCatalogPath()
        // 优先用 GradleSettings 获取真实的 Gradle root
        try {
            val gradleSettings = GradleSettings.getInstance(project)
            val rootPaths = gradleSettings.linkedProjectsSettings.map { it.externalProjectPath }
            for (rootPath in rootPaths) {
                val candidate = File(rootPath, catalogRelPath)
                if (candidate.exists()) return candidate
            }
            // 没有找到已存在的，返回第一个 root 的路径（用于创建新文件）
            if (rootPaths.isNotEmpty()) {
                return File(rootPaths.first(), catalogRelPath)
            }
        } catch (_: Throwable) {
            // GradleSettings 不可用时 fallback
        }
        // fallback: basePath
        val basePath = project.basePath ?: return File(catalogRelPath)
        return File(basePath, catalogRelPath)
    }

    companion object {
        val DEFAULT_TASKS = listOf(
            "clean",
            "compileKotlin",
            "build",
            "test",
            "jar",
            "publishToMavenLocal",
            "publishToMavenCentral",
            "kspKotlin",
            "kspCommonMainMetadata",
            "signPlugin",
            "publishPlugin",
            "runIde"
        )

        const val DEFAULT_VERSION_CATALOG_PATH = "gradle/libs.versions.toml"

        fun getInstance(project: Project): GradleBuddySettingsService = project.service()
    }
}

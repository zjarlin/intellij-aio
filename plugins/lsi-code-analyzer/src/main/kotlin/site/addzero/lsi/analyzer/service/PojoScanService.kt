package site.addzero.lsi.analyzer.service
import com.google.gson.GsonBuilder
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.FilenameIndex
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.idea.base.util.projectScope
import site.addzero.json2kotlin.Json2Kotlin
import site.addzero.lsi.analyzer.cache.MetadataCacheManager
import site.addzero.lsi.analyzer.metadata.LsiClass
import site.addzero.lsi.analyzer.scanner.LsiClassScanner
import site.addzero.lsi.analyzer.settings.PojoMetaSettingsService
import site.addzero.util.lsi_impl.impl.intellij.virtualfile.toAllLsiClassesUnified
import java.io.File
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class PojoScanService(private val project: Project) {

    private var timer: Timer? = null
    private val scanner = LsiClassScanner()
    private val listeners = CopyOnWriteArrayList<(List<LsiClass>) -> Unit>()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Volatile
    private var lastScanResult: List<LsiClass> = emptyList()

    fun getLastScanResult(): List<LsiClass> = lastScanResult

    fun addScanListener(listener: (List<LsiClass>) -> Unit) {
        listeners.add(listener)
    }

    fun removeScanListener(listener: (List<LsiClass>) -> Unit) {
        listeners.remove(listener)
    }
    fun startScheduledScan(intervalMinutes: Int) {
        stopScheduledScan()
        timer = Timer("PojoMetaScanTimer", true).apply {
            val intervalMs = intervalMinutes * 60 * 1000L
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (!project.isDisposed) {
                        scanNowAsync()
                    }
                }
            }, intervalMs, intervalMs)
        }
    }

    fun stopScheduledScan() {
        timer?.cancel()
        timer = null
    }

    fun scanNowAsync(callback: ((List<LsiClass>) -> Unit)? = null) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // 等待索引完成
                DumbService.getInstance(project).waitForSmartMode()

                val result = runReadAction { scanNow() }

                // 在 EDT 线程调用回调
                ApplicationManager.getApplication().invokeLater {
                    callback?.invoke(result)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ApplicationManager.getApplication().invokeLater {
                    callback?.invoke(emptyList())
                }
            }
        }
    }

    fun scanNow(): List<LsiClass> {
        if (project.isDisposed) return emptyList()

        val result = runReadAction {
            val scope = project.projectScope()
            val allFiles = mutableListOf<VirtualFile>()

            // Java 文件
            FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope).forEach { allFiles.add(it) }

            // Kotlin 文件 - 使用 FilenameIndex 按扩展名搜索，避免类加载器冲突
            FilenameIndex.getAllFilesByExt(project, "kt", scope).forEach { allFiles.add(it) }

            println("[PojoScan] 找到 ${allFiles.size} 个文件 (Java+Kotlin)")
            allFiles.flatMap { file ->
                val classes = file.toAllLsiClassesUnified(project)
                println("[PojoScan] 文件 ${file.name}: 解析到 ${classes.size} 个类")
                classes.forEach { cls ->
                    println("[PojoScan]   - ${cls.name}: isPojo=${cls.isPojo}, isInterface=${cls.isInterface}, isEnum=${cls.isEnum}")
                }
                classes.filter {
                        val support = scanner.support(it)
                        println("[issupport$support]")
                        support
                    }.map {
                        val scan = scanner.scan(it)
                        scan
                    }
            }
        }

        lastScanResult = result

        val projectPath = project.basePath ?: return result
        MetadataCacheManager.saveLsiClass(projectPath, result)

        listeners.forEach { it.invoke(result) }
        return result
    }

    fun saveToJson(path: Path) {
        val json = gson.toJson(lastScanResult)
        path.toFile().apply {
            parentFile?.mkdirs()
            writeText(json)
        }
    }

    fun loadFromJson(path: Path): List<LsiClass> {
        val file = path.toFile()
        if (!file.exists()) return emptyList()
        return try {
            gson.fromJson(file.readText(), Array<LsiClass>::class.java).toList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun generateKotlinDataClasses(): File? {
        if (lastScanResult.isEmpty()) return null

        val projectPath = project.basePath ?: return null
        val generatedDir = File(projectPath, "build/generated/sources/pojometa/kotlin")
        generatedDir.mkdirs()

        val json = gson.toJson(lastScanResult)
        val result = Json2Kotlin.convert(json, "LsiClassList", "LsiClassList", "site.addzero.generated.pojometa")

        val outputFile = File(generatedDir, "site/addzero/generated/pojometa/LsiClassList.kt")
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(result.fullCode)

        markAsGeneratedSources(generatedDir)
        return generatedDir
    }

    private fun markAsGeneratedSources(generatedDir: File) {
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(generatedDir) ?: return

        ApplicationManager.getApplication().invokeLater {
            ModuleManager.getInstance(project).modules.firstOrNull()?.let { module ->
                ModuleRootModificationUtil.updateModel(module) { model ->
                    val contentEntry = model.contentEntries.firstOrNull()
                    contentEntry?.addSourceFolder(vf, JavaSourceRootType.SOURCE, org.jetbrains.jps.model.java.JavaSourceRootProperties("", true))
                }
            }
        }
    }

    fun exportMetadata(): File? {
        val settings = PojoMetaSettingsService.getInstance().state
        val projectPath = project.basePath ?: return null
        val exportDir = File(projectPath, settings.metadataExportPath)
        exportDir.mkdirs()

        val jsonFile = File(exportDir, "pojo_metadata.json")
        saveToJson(jsonFile.toPath())

        if (settings.generateKotlinDataClass) {
            generateKotlinDataClasses()
        }

        return exportDir
    }

    companion object {
        fun getInstance(project: Project): PojoScanService =
            project.getService(PojoScanService::class.java)
    }
}

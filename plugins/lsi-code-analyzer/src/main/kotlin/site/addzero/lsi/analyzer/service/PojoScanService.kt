package site.addzero.lsi.analyzer.service

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.base.util.projectScope
import site.addzero.lsi.analyzer.cache.MetadataCacheManager
import site.addzero.lsi.analyzer.metadata.PojoMetadata
import site.addzero.lsi.analyzer.scanner.PojoMetadataScanner
import site.addzero.lsi.analyzer.settings.PojoMetaSettingsService
import site.addzero.util.lsi_impl.impl.intellij.virtualfile.toAllLsiClassesUnified
import site.addzero.json2kotlin.Json2Kotlin
import com.google.gson.GsonBuilder
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.io.File
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class PojoScanService(private val project: Project) {

    private var timer: Timer? = null
    private val scanner = PojoMetadataScanner()
    private val listeners = CopyOnWriteArrayList<(List<PojoMetadata>) -> Unit>()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Volatile
    private var lastScanResult: List<PojoMetadata> = emptyList()

    fun getLastScanResult(): List<PojoMetadata> = lastScanResult

    fun addScanListener(listener: (List<PojoMetadata>) -> Unit) {
        listeners.add(listener)
    }

    fun removeScanListener(listener: (List<PojoMetadata>) -> Unit) {
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

    fun scanNowAsync(callback: ((List<PojoMetadata>) -> Unit)? = null) {
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

    fun scanNow(): List<PojoMetadata> {
        if (project.isDisposed) return emptyList()

        val result = runReadAction {
            val scope = project.projectScope()
            val allFiles = mutableListOf<VirtualFile>()

            // Java 文件
            FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope).forEach { allFiles.add(it) }
            
            // Kotlin 文件 - 使用 FileTypeManager 避免类加载器冲突
            val kotlinFileType = FileTypeManager.getInstance().getFileTypeByExtension("kt")
            FileTypeIndex.getFiles(kotlinFileType, scope).forEach { allFiles.add(it) }

            println("[PojoScan] 找到 ${allFiles.size} 个文件 (Java+Kotlin)")

            allFiles.flatMap { file ->
                val classes = file.toAllLsiClassesUnified(project)
                println("[PojoScan] 文件 ${file.name}: 解析到 ${classes.size} 个类")
                classes.forEach { cls ->
                    println("[PojoScan]   - ${cls.name}: isPojo=${cls.isPojo}, isInterface=${cls.isInterface}, isEnum=${cls.isEnum}")
                }
                classes
                    .filter { scanner.support(it) }
                    .map { scanner.scan(it) }
            }
        }

        lastScanResult = result
        
        val projectPath = project.basePath ?: return result
        MetadataCacheManager.savePojoMetadata(projectPath, result)

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

    fun loadFromJson(path: Path): List<PojoMetadata> {
        val file = path.toFile()
        if (!file.exists()) return emptyList()
        return try {
            gson.fromJson(file.readText(), Array<PojoMetadata>::class.java).toList()
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
        val result = Json2Kotlin.convert(json, "PojoMetadataList", "pojoMetadataList", "site.addzero.generated.pojometa")
        
        val outputFile = File(generatedDir, "site/addzero/generated/pojometa/PojoMetadataList.kt")
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

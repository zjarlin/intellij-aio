package site.addzero.lsi.analyzer.cache

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import site.addzero.lsi.analyzer.jimmer.JimmerEntityMetadata
import site.addzero.util.lsi.clazz.LsiClass
import java.io.File
import java.security.MessageDigest

object MetadataCacheManager {

    private val cacheDir: File = File(System.getProperty("user.home"), ".config/lsi_metadata")
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    init {
        cacheDir.mkdirs()
    }

    fun getProjectCacheDir(projectPath: String): File {
        val hash = projectPath.md5().take(16)
        val dir = File(cacheDir, hash)
        dir.mkdirs()
        return dir
    }

    fun saveLsiClass(projectPath: String, metadata: List<LsiClass>) {
        val dir = getProjectCacheDir(projectPath)
        val file = File(dir, "pojo_metadata.json")
        file.writeText(gson.toJson(metadata))
        saveScanInfo(dir, projectPath)
    }

    fun loadLsiClass(projectPath: String): List<LsiClass>? {
        val dir = getProjectCacheDir(projectPath)
        val file = File(dir, "pojo_metadata.json")
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), Array<LsiClass>::class.java).toList()
        } catch (e: Exception) {
            null
        }
    }

    fun saveJimmerMetadata(projectPath: String, metadata: List<JimmerEntityMetadata>) {
        val dir = getProjectCacheDir(projectPath)
        val file = File(dir, "jimmer_metadata.json")
        file.writeText(gson.toJson(metadata))
        saveScanInfo(dir, projectPath)
    }

    fun loadJimmerMetadata(projectPath: String): List<JimmerEntityMetadata>? {
        val dir = getProjectCacheDir(projectPath)
        val file = File(dir, "jimmer_metadata.json")
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), Array<JimmerEntityMetadata>::class.java).toList()
        } catch (e: Exception) {
            null
        }
    }

    fun invalidate(projectPath: String) {
        val dir = getProjectCacheDir(projectPath)
        dir.deleteRecursively()
    }

    private fun saveScanInfo(dir: File, projectPath: String) {
        val scanInfo = ScanInfo(
            projectPath = projectPath,
            scanTime = System.currentTimeMillis()
        )
        File(dir, "scan_info.json").writeText(gson.toJson(scanInfo))
    }

    private fun String.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(this.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

data class ScanInfo(
    val projectPath: String,
    val scanTime: Long
)

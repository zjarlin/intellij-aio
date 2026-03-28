package site.addzero.split.services

import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * 路径计算工具类
 */
object PathCalculator {

    private val sourceRootMarkers = setOf("kotlin", "java", "resources")

    /**
     * 计算同级目录路径
     * @param moduleDir 源模块目录
     * @param newModuleName 新模块名称
     * @return 新模块的同级目录路径
     */
    fun calculateSiblingPath(moduleDir: File, newModuleName: String): File {
        val parentDir = moduleDir.parentFile
        return File(parentDir, newModuleName)
    }

    /**
     * 将模块目录路径转换为 Gradle 模块路径
     * @param projectRoot 项目根目录
     * @param moduleDir 模块目录
     * @return Gradle 模块路径，例如 ":sub:module-name" 或 ":module-name"
     */
    fun calculateGradlePath(projectRoot: File, moduleDir: File): String {
        val relativePath = moduleDir.relativeTo(projectRoot).path

        // 将路径分隔符替换为 :
        val gradlePath = relativePath.replace(File.separator, ":")

        // 添加前缀 :
        return ":$gradlePath"
    }

    /**
     * 检查模块名称是否合法
     * 只允许字母、数字、下划线、中划线
     */
    fun isValidModuleName(name: String): Boolean {
        if (name.isBlank()) return false
        return name.matches(Regex("[a-zA-Z0-9_-]+"))
    }

    /**
     * 生成默认的新模块名称
     * 例如：module-a -> module-a1
     */
    fun generateDefaultModuleName(sourceModuleName: String): String {
        return "${sourceModuleName}1"
    }

    /**
     * 根据选中包推导默认模块名称
     * 例如：compose-native-component + ext -> compose-native-component-ext
     */
    fun generateDefaultModuleName(
        sourceModuleName: String,
        sourceModule: VirtualFile,
        selectedFiles: List<VirtualFile>
    ): String {
        val leafPackageName = resolveSelectedLeafPackageName(sourceModule, selectedFiles)
        if (leafPackageName == null) {
            return generateDefaultModuleName(sourceModuleName)
        }

        return "$sourceModuleName-$leafPackageName"
    }

    private fun resolveSelectedLeafPackageName(
        sourceModule: VirtualFile,
        selectedFiles: List<VirtualFile>
    ): String? {
        val selectionDirs = selectedFiles
            .mapNotNull { selectedFile ->
                if (selectedFile.isDirectory) {
                    selectedFile
                } else {
                    selectedFile.parent
                }
            }
            .distinctBy { it.path }

        if (selectionDirs.isEmpty()) {
            return null
        }

        val leafPackageNames = selectionDirs
            .mapNotNull { selectedDir ->
                extractLeafPackageName(sourceModule, selectedDir)
            }
            .distinct()

        if (leafPackageNames.size != 1) {
            return null
        }

        return leafPackageNames.single()
    }

    private fun extractLeafPackageName(sourceModule: VirtualFile, selectedDir: VirtualFile): String? {
        if (!selectedDir.path.startsWith(sourceModule.path)) {
            return null
        }

        val relativePath = selectedDir.path
            .removePrefix(sourceModule.path)
            .trimStart('/', File.separatorChar)

        if (relativePath.isBlank()) {
            return null
        }

        val pathSegments = relativePath
            .split('/', '\\')
            .filter { it.isNotBlank() }

        if (pathSegments.isEmpty()) {
            return null
        }

        val sourceRootIndex = pathSegments.indexOfLast { segment ->
            segment in sourceRootMarkers
        }

        if (sourceRootIndex == -1 || sourceRootIndex >= pathSegments.lastIndex) {
            return null
        }

        val leafPackageName = pathSegments.last()
        return leafPackageName.takeIf { candidate ->
            isValidModuleName(candidate)
        }
    }
}

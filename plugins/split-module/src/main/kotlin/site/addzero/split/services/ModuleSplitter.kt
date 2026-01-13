package site.addzero.split.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import java.io.File

/**
 * 模块拆分服务
 */
class ModuleSplitter(private val project: Project) {

    private val logger = Logger.getInstance(ModuleSplitter::class.java)

    /**
     * 执行模块拆分
     * @param sourceModule 源模块目录
     * @param selectedFiles 选中的文件列表
     * @param newModuleName 新模块名称
     * @return 是否成功
     */
    fun split(sourceModule: VirtualFile, selectedFiles: List<VirtualFile>, newModuleName: String): Boolean {
        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                performSplit(sourceModule, selectedFiles, newModuleName)
            }
            showSuccessNotification(sourceModule.name, newModuleName)
            true
        } catch (e: Exception) {
            logger.error("Failed to split module", e)
            showErrorNotification(e.message ?: "Unknown error")
            false
        }
    }

    private fun performSplit(sourceModule: VirtualFile, selectedFiles: List<VirtualFile>, newModuleName: String) {
        val projectRoot = File(project.basePath!!)
        val sourceModuleDir = File(sourceModule.path)

        // 1. 计算新模块路径
        val newModuleDir = PathCalculator.calculateSiblingPath(sourceModuleDir, newModuleName)

        // 2. 检查模块是否已存在
        if (newModuleDir.exists()) {
            throw IllegalStateException("Module '$newModuleName' already exists")
        }

        // 3. 创建新模块目录结构
        createModuleStructure(sourceModule, newModuleDir)

        // 4. 复制 build.gradle.kts
        copyBuildFile(sourceModule, newModuleDir)

        // 5. 移动选中的文件
        moveFiles(selectedFiles, sourceModule, newModuleDir)

        // 6. 添加依赖到源模块
        addDependency(sourceModule, projectRoot, newModuleDir)

        // 7. 刷新文件系统
        VfsUtil.markDirtyAndRefresh(false, true, true, newModuleDir)
    }

    private fun createModuleStructure(sourceModule: VirtualFile, newModuleDir: File) {
        // 创建基础目录
        newModuleDir.mkdirs()

        // 检测源模块的目录结构
        val srcMainKotlin = sourceModule.findFileByRelativePath("src/main/kotlin")
        val srcMainJava = sourceModule.findFileByRelativePath("src/main/java")
        val srcMainResources = sourceModule.findFileByRelativePath("src/main/resources")
        val srcTestKotlin = sourceModule.findFileByRelativePath("src/test/kotlin")
        val srcTestJava = sourceModule.findFileByRelativePath("src/test/java")
        val srcTestResources = sourceModule.findFileByRelativePath("src/test/resources")

        // 创建对应的目录结构
        if (srcMainKotlin != null) {
            File(newModuleDir, "src/main/kotlin").mkdirs()
        }
        if (srcMainJava != null) {
            File(newModuleDir, "src/main/java").mkdirs()
        }
        if (srcMainResources != null) {
            File(newModuleDir, "src/main/resources").mkdirs()
        }
        if (srcTestKotlin != null) {
            File(newModuleDir, "src/test/kotlin").mkdirs()
        }
        if (srcTestJava != null) {
            File(newModuleDir, "src/test/java").mkdirs()
        }
        if (srcTestResources != null) {
            File(newModuleDir, "src/test/resources").mkdirs()
        }
    }

    private fun copyBuildFile(sourceModule: VirtualFile, newModuleDir: File) {
        val buildFile = sourceModule.findChild("build.gradle.kts")
            ?: throw IllegalStateException("Source module does not have build.gradle.kts")

        val targetBuildFile = File(newModuleDir, "build.gradle.kts")
        buildFile.inputStream.use { input ->
            targetBuildFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun moveFiles(selectedFiles: List<VirtualFile>, sourceModule: VirtualFile, newModuleDir: File) {
        val sourceModulePath = File(sourceModule.path)

        selectedFiles.forEach { file ->
            val filePath = File(file.path)
            val relativePath = filePath.relativeTo(sourceModulePath)
            val targetPath = File(newModuleDir, relativePath.path)

            // 确保目标目录存在
            targetPath.parentFile.mkdirs()

            // 移动文件
            filePath.copyTo(targetPath, overwrite = false)
            filePath.delete()
        }
    }


    private fun addDependency(sourceModule: VirtualFile, projectRoot: File, newModuleDir: File) {
        val buildFile = sourceModule.findChild("build.gradle.kts")
            ?: throw IllegalStateException("Source module does not have build.gradle.kts")

        val gradlePath = PathCalculator.calculateGradlePath(projectRoot, newModuleDir)
        val dependencyStatement = "    implementation(project(\"$gradlePath\"))"

        var content = String(buildFile.contentsToByteArray())

        // 检查是否已存在该依赖
        if (content.contains("project(\"$gradlePath\")")) {
            return
        }

        // 查找 dependencies 块
        val dependenciesBlockRegex = Regex("dependencies\\s*\\{([^}]*?)\\}", RegexOption.DOT_MATCHES_ALL)
        val match = dependenciesBlockRegex.find(content)

        if (match != null) {
            // dependencies 块存在，添加到块内
            val blockContent = match.groupValues[1]
            val newBlockContent = blockContent + "\n$dependencyStatement"
            content = content.replace(match.value, "dependencies {$newBlockContent\n}")
        } else {
            // dependencies 块不存在，创建新块
            content += "\n\ndependencies {\n$dependencyStatement\n}\n"
        }

        // 写回文件
        buildFile.setBinaryContent(content.toByteArray())
    }

    private fun showSuccessNotification(sourceModuleName: String, newModuleName: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SplitModule")
            .createNotification(
                "Split Module 成功",
                "已生成模块 $newModuleName，模块 $sourceModuleName 已自动引入该依赖",
                NotificationType.INFORMATION
            )
            .notify(project)
    }

    private fun showErrorNotification(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SplitModule")
            .createNotification(
                "Split Module 失败",
                message,
                NotificationType.ERROR
            )
            .notify(project)
    }
}
